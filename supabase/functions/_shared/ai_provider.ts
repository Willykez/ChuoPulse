const TIMEOUT_MS = 45_000

export class ProviderError extends Error {
  constructor(message: string, public status: number) { super(message) }
}

export interface ModelCall {
  provider: string
  key: string
  model: string
  prompt: string
  text: string
  geminiSchema?: unknown
  maxTokens?: number
}

async function post(url: string, init: RequestInit): Promise<Response> {
  let last: Response | undefined
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      last = await fetch(url, { ...init, signal: AbortSignal.timeout(TIMEOUT_MS) })
    } catch {
      throw new ProviderError('timeout', 504)
    }
    if (last.status !== 429 && last.status < 500) return last
    if (attempt === 0) await new Promise((r) => setTimeout(r, 1200))
  }
  return last!
}

export function parseJson(text: string): unknown {
  const cleaned = text.trim().replace(/^```(?:json)?/i, '').replace(/```$/, '').trim()
  return JSON.parse(cleaned)
}

export async function callModel(c: ModelCall): Promise<unknown> {
  const content = `${c.prompt}\n\n--- DOCUMENT ---\n${c.text}`
  if (c.provider === 'anthropic') {
    const r = await post('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-api-key': c.key, 'anthropic-version': '2023-06-01' },
      body: JSON.stringify({ model: c.model, max_tokens: c.maxTokens ?? 1500, temperature: 0.2, messages: [{ role: 'user', content }] }),
    })
    if (!r.ok) throw new ProviderError(await r.text(), r.status)
    const body = await r.json()
    return parseJson((body?.content ?? []).map((p: { text?: string }) => p.text ?? '').join(''))
  }
  const r = await post(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(c.model)}:generateContent`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': c.key },
    body: JSON.stringify({
      contents: [{ role: 'user', parts: [{ text: content }] }],
      generationConfig: {
        temperature: 0.2,
        maxOutputTokens: c.maxTokens ?? 8192,
        responseMimeType: 'application/json',
        ...(c.geminiSchema ? { responseSchema: c.geminiSchema } : {}),
      },
    }),
  })
  if (!r.ok) throw new ProviderError(await r.text(), r.status)
  const body = await r.json()
  if (body?.promptFeedback?.blockReason) throw new ProviderError('blocked', 422)
  return parseJson(body?.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text ?? '').join('') ?? '')
}

export function providerErrorResponse(e: unknown): { status: number; error: string } {
  const status = (e as { status?: number }).status ?? 0
  const message = String((e as Error).message ?? '')
  if (status === 401 || status === 403 || (status === 400 && message.includes('API key'))) return { status: 502, error: 'ai_key_rejected' }
  if (status === 422) return { status: 422, error: 'ai_blocked' }
  if (status === 429) return { status: 503, error: 'ai_busy' }
  if (status === 504) return { status: 504, error: 'ai_timeout' }
  return { status: 502, error: 'ai_failed' }
}
