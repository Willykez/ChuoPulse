// Account actions that need the service key: reset a password, change a login email, pause/resume
// (blocks sign-in as well as data), delete, and erase (privacy request). The DATABASE decides who may
// act on whom (public.can_manage_user, run with the CALLER's JWT, which also enforces the
// administrator's second step). The service-role key lives only here, never in the app.
//
// Every powerful action is written to the audit log BEFORE it is carried out (if the log cannot be
// written, nothing happens), and actions that must lock someone out also end their sessions.
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

  // Audit first. Returns a Response when the trail could not be written (and then nothing is done).
  const audit = async (what: string, details = ''): Promise<Response | null> => {
    const { error } = await userClient.rpc('log_account_action', { p_target: userId, p_action: what, p_details: details })
    return error ? json(500, { error: 'audit_failed' }) : null
  }
  const endSessions = async () => { await admin.rpc('revoke_user_sessions', { p_user: userId }) }
  const randomSecret = () => crypto.randomUUID() + crypto.randomUUID()

  // Keep the person's record (their submissions and grades hang on it) but remove everything personal
  // from the sign-in account too, and lock it for good.
  const lockAccount = async () => {
    await admin.auth.admin.updateUserById(userId, {
      email: `erased-${userId.replace(/-/g, '').slice(0, 12)}@invalid.invalid`,
      password: randomSecret(), email_confirm: true, ban_duration: FOREVER,
    })
  }
  // Delete = remove the sign-in account (and with it the profile and its work). If something they wrote
  // still points at them (announcements, grades), keep the record: erase the personal data and lock it.
  const removeAccount = async () => {
    const { error } = await admin.auth.admin.deleteUser(userId)
    if (!error) return { soft: false as const }
    await admin.rpc('erase_person_data', { p_user: userId })
    await lockAccount()
    return { soft: true as const, detail: error.message }
  }

  switch (action) {
    case 'reset_password': {
      const password = typeof i.password === 'string' ? i.password : ''
      if (password.length < 8) return json(400, { error: 'weak_password' })
      const stop = await audit('PASSWORD_RESET'); if (stop) return stop
      const { error } = await admin.auth.admin.updateUserById(userId, { password, email_confirm: true })
      if (error) return json(400, { error: 'reset_failed', detail: error.message })
      await endSessions()   // whoever held the old password is signed out everywhere
      return json(200, { ok: true })
    }
    case 'update_login': {
      const email = str('email').toLowerCase()
      if (!EMAIL.test(email)) return json(400, { error: 'invalid_email' })
      const stop = await audit('LOGIN_EMAIL_CHANGED', `new address: ${email}`); if (stop) return stop
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
      await endSessions()
      return json(200, { ok: true })
    }
    case 'resume': {
      const { error } = await userClient.rpc('admin_set_status', { p_user: userId, p_pause: false, p_reason: '' })
      if (error) return json(400, { error: 'resume_failed', detail: error.message })
      await admin.auth.admin.updateUserById(userId, { ban_duration: 'none' })
      return json(200, { ok: true })
    }
    case 'delete': {
      const stop = await audit('ACCOUNT_DELETED'); if (stop) return stop
      await endSessions()
      const r = await removeAccount()
      return json(200, r.soft ? { ok: true, soft: true, detail: r.detail } : { ok: true })
    }
    case 'erase': {
      // A privacy request: personal data goes, the academic record stays (attributed to "Deleted user").
      const stop = await audit('ACCOUNT_ERASED'); if (stop) return stop
      await endSessions()
      const { error: eErr } = await admin.rpc('erase_person_data', { p_user: userId })
      if (eErr) return json(400, { error: 'erase_failed', detail: eErr.message })
      await lockAccount()   // never deleteUser here: it would cascade away the grades we are keeping
      return json(200, { ok: true, erased: true })
    }
    default:
      return json(400, { error: 'unknown_action' })
  }
})
