// Real AI analysis of a document's text: summary, key topics, objectives and study tips.
// The API key is NEVER in the app. It comes from, in this order:
//   1. the key the app manager saved on the AI settings screen (table public.ai_settings, unreadable by the app), or
//   2. the secret AI_API_KEY (or GEMINI_API_KEY) set with `supabase secrets set` / the GitHub workflow.
// Every approved person may call it; each person has a daily limit.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { ...cors, 'Content-Type': 'application/json' },
})

const MAX_CHARS = 60_000
const DAILY_LIMIT = Number(Deno.env.get('AI_DAILY_LIMIT') ?? '30')

const instruction = (name: string, course: string) => `You are a study assistant for university students in East Africa.
Read the document text and answer ONLY with JSON in this exact shape:
{"summary": string, "key_topics": string[], "objectives": string[], "study_tips": string[]}
Rules:
- Write in the same language as the document (English or Swahili).
- "summary": 4 to 7 sentences covering the main ideas. Do not invent facts that are not in the document.
- "key_topics": 4 to 8 short topic names.
- "objectives": 3 to 6 learning objectives, each starting with a verb.
- "study_tips": 3 to 5 practical tips specific to this material.
Document name: ${name}
Course: ${course}`

function parseModelJson(text: string) {
  const cleaned = text.trim().replace(/^```(?:json)?/i, '').replace(/```$/, '').trim()
  const o = JSON.parse(cleaned)
  const list = (v: unknown) => Array.isArray(v) ? v.map((x) => String(x).trim()).filter(Boolean).slice(0, 10) : []
  const summary = String(o.summary ?? '').trim()
  if (!summary) throw new Error('empty_summary')
  return { summary, key_topics: list(o.key_topics), objectives: list(o.objectives), study_tips: list(o.study_tips) }
}

async function callGemini(key: string, model: string, prompt: string, text: string) {
  const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
    body: JSON.stringify({
      contents: [{ role: 'user', parts: [{ text: `${prompt}\n\n--- DOCUMENT ---\n${text}` }] }],
      generationConfig: {
        temperature: 0.3,
        responseMimeType: 'application/json',
        responseSchema: {
          type: 'OBJECT',
          properties: {
            summary: { type: 'STRING' },
            key_topics: { type: 'ARRAY', items: { type: 'STRING' } },
            objectives: { type: 'ARRAY', items: { type: 'STRING' } },
            study_tips: { type: 'ARRAY', items: { type: 'STRING' } },
          },
          required: ['summary', 'key_topics', 'objectives', 'study_tips'],
        },
      },
    }),
  })
  if (!r.ok) throw Object.assign(new Error(await r.text()), { status: r.status })
  const body = await r.json()
  const out = body?.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text ?? '').join('') ?? ''
  return parseModelJson(out)
}

async function callAnthropic(key: string, model: string, prompt: string, text: string) {
  const r = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-api-key': key, 'anthropic-version': '2023-06-01' },
    body: JSON.stringify({
      model, max_tokens: 1500, temperature: 0.3,
      messages: [{ role: 'user', content: `${prompt}\n\n--- DOCUMENT ---\n${text}` }],
    }),
  })
  if (!r.ok) throw Object.assign(new Error(await r.text()), { status: r.status })
  const body = await r.json()
  const out = (body?.content ?? []).map((c: { text?: string }) => c.text ?? '').join('')
  return parseModelJson(out)
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  if (req.method !== 'POST') return json(405, { error: 'method_not_allowed' })

  const url = Deno.env.get('SUPABASE_URL')
  const anon = Deno.env.get('SUPABASE_ANON_KEY')
  const service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!url || !anon || !service) return json(500, { error: 'server_not_configured' })

  const authHeader = req.headers.get('Authorization') ?? ''
  if (!authHeader.startsWith('Bearer ')) return json(401, { error: 'missing_bearer_token' })
  const token = authHeader.slice(7)
  const userClient = createClient(url, anon, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  })
  const { data: me, error: meError } = await userClient.auth.getUser(token)
  if (meError || !me.user) return json(401, { error: 'invalid_session' })

  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } })
  const { data: profile } = await admin.from('profiles').select('status, is_platform_admin').eq('id', me.user.id).maybeSingle()
  if (!profile || profile.status !== 'approved') return json(403, { error: 'not_authorized' })

  let i: Record<string, unknown>
  try { i = await req.json() } catch { return json(400, { error: 'invalid_json' }) }
  const text = (typeof i.text === 'string' ? i.text : '').trim().slice(0, MAX_CHARS)
  if (text.length < 40) return json(400, { error: 'text_too_short' })
  const name = typeof i.file_name === 'string' ? i.file_name.slice(0, 120) : 'document'
  const course = typeof i.course === 'string' ? i.course.slice(0, 80) : ''

  const { data: cfg } = await admin.from('ai_settings').select('provider, model, api_key, enabled').eq('id', 1).maybeSingle()
  if (cfg && cfg.enabled === false) return json(503, { error: 'ai_disabled' })
  const key = (cfg?.api_key as string | undefined)?.trim() || Deno.env.get('AI_API_KEY') || Deno.env.get('GEMINI_API_KEY') || ''
  if (!key) return json(503, { error: 'ai_not_configured' })
  const provider = ((cfg?.provider as string | undefined) ?? Deno.env.get('AI_PROVIDER') ?? 'gemini').toLowerCase()
  const model = ((cfg?.model as string | undefined) ?? '').trim() || Deno.env.get('AI_MODEL') ||
    (provider === 'anthropic' ? 'claude-sonnet-4-5' : 'gemini-2.5-flash')

  if (!profile.is_platform_admin) {
    const { data: ok } = await admin.rpc('ai_take_use', { p_user: me.user.id, p_limit: DAILY_LIMIT })
    if (ok !== true) return json(429, { error: 'daily_limit' })
  }

  try {
    const result = provider === 'anthropic'
      ? await callAnthropic(key, model, instruction(name, course), text)
      : await callGemini(key, model, instruction(name, course), text)
    return json(200, { ok: true, provider, model, ...result })
  } catch (e) {
    const status = (e as { status?: number }).status ?? 0
    // Never echo provider error text back (it can contain account details).
    if (status === 401 || status === 403 || status === 400 && String((e as Error).message).includes('API key')) {
      return json(502, { error: 'ai_key_rejected' })
    }
    if (status === 429) return json(503, { error: 'ai_busy' })
    return json(502, { error: 'ai_failed' })
  }
})
