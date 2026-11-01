// Account actions that need the service key: reset a password, change a login email, pause/resume
// (blocks sign-in as well as data), and delete. The DATABASE decides who may act on whom
// (public.can_manage_user, run with the CALLER's JWT, which also enforces the administrator's second step).
// The service-role key lives only here, in the function's own environment, never in the app.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { ...cors, 'Content-Type': 'application/json' },
})
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

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

  let i: Record<string, unknown>
  try { i = await req.json() } catch { return json(400, { error: 'invalid_json' }) }
  const str = (k: string) => (typeof i[k] === 'string' ? (i[k] as string).trim() : '')
  const action = str('action')
  const userId = str('user_id')
  if (!UUID.test(userId)) return json(400, { error: 'invalid_user' })

  // Authorization, decided by the database for the person who is calling.
  const { data: allowed, error: allowError } = await userClient.rpc('can_manage_user', { p_target: userId })
  if (allowError || allowed !== true) return json(403, { error: 'not_authorized' })

  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } })
  const FOREVER = '876000h' // about 100 years: "banned" until resumed

  switch (action) {
    case 'reset_password': {
      const password = typeof i.password === 'string' ? i.password : ''
      if (password.length < 8) return json(400, { error: 'weak_password' })
      const { error } = await admin.auth.admin.updateUserById(userId, { password, email_confirm: true })
      if (error) return json(400, { error: 'reset_failed', detail: error.message })
      return json(200, { ok: true })
    }
    case 'update_login': {
      const email = str('email').toLowerCase()
      if (!EMAIL.test(email)) return json(400, { error: 'invalid_email' })
      const { error } = await admin.auth.admin.updateUserById(userId, { email, email_confirm: true })
      if (error) {
        const m = (error.message ?? '').toLowerCase()
        return json(m.includes('already') ? 409 : 400, { error: m.includes('already') ? 'email_taken' : 'update_failed', detail: error.message })
      }
      // The profile keeps its own copy of the email (service role: not subject to the profile guard).
      const { error: pErr } = await admin.from('profiles').update({ email, email_verified: true }).eq('id', userId)
      if (pErr) return json(400, { error: 'update_failed', detail: pErr.message })
      return json(200, { ok: true })
    }
    case 'pause': {
      const { error } = await userClient.rpc('admin_set_status', { p_user: userId, p_pause: true, p_reason: str('reason') })
      if (error) return json(400, { error: 'pause_failed', detail: error.message })
      await admin.auth.admin.updateUserById(userId, { ban_duration: FOREVER })
      return json(200, { ok: true })
    }
    case 'resume': {
      const { error } = await userClient.rpc('admin_set_status', { p_user: userId, p_pause: false, p_reason: '' })
      if (error) return json(400, { error: 'resume_failed', detail: error.message })
      await admin.auth.admin.updateUserById(userId, { ban_duration: 'none' })
      return json(200, { ok: true })
    }
    case 'delete': {
      const { error } = await admin.auth.admin.deleteUser(userId)
      if (!error) return json(200, { ok: true })
      // Something they wrote still points at them (announcements, grades). Keep the record, remove the access.
      await admin.from('profiles').update({ status: 'rejected' }).eq('id', userId)
      await admin.auth.admin.updateUserById(userId, { ban_duration: FOREVER })
      return json(200, { ok: true, soft: true, detail: error.message })
    }
    default:
      return json(400, { error: 'unknown_action' })
  }
})
