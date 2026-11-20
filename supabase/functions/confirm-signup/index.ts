// Marks a freshly registered, still-pending account's email as confirmed so the person can sign in
// without an emailed code. An administrator still has to approve the account before it can do anything.
//
// SECURITY: this function is public (no JWT: the caller is not signed in yet), so it must prove the
// caller knows the account's password. It does that by asking the auth server itself: GoTrue answers
// "email_not_confirmed" ONLY when the e-mail + password pair is correct. Without that proof anybody
// could confirm anybody's pending account just by knowing the e-mail address.
//
// The app must send { email, password }. During a rollout of an older app you can set the secret
//   CONFIRM_SIGNUP_LEGACY=true      (accepts { email } alone: the old, weaker behaviour; remove it afterwards)
//
// Deploy WITHOUT JWT verification:   supabase functions deploy confirm-signup --no-verify-jwt
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

// Best-effort throttle per address (per function instance). The auth server has its own limits too;
// this only keeps one address from using up the shared sign-in allowance of this function.
const attempts = new Map<string, { n: number; at: number }>()
function throttled(email: string): boolean {
  const now = Date.now()
  const a = attempts.get(email)
  if (!a || now - a.at > 60_000) { attempts.set(email, { n: 1, at: now }); return false }
  a.n += 1
  if (attempts.size > 5000) attempts.clear()
  return a.n > 5
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  if (req.method !== 'POST') return json(405, { error: 'method_not_allowed' })
  const url = Deno.env.get('SUPABASE_URL')
  const anon = Deno.env.get('SUPABASE_ANON_KEY')
  const service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!url || !service || !anon) return json(500, { error: 'server_not_configured' })
  const legacy = Deno.env.get('CONFIRM_SIGNUP_LEGACY') === 'true'

  let body: { email?: unknown; password?: unknown }
  try { body = await req.json() } catch { return json(400, { error: 'invalid_json' }) }
  const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : ''
  const password = typeof body.password === 'string' ? body.password : ''
  if (!EMAIL.test(email)) return json(400, { error: 'invalid_email' })
  if (!password && !legacy) return json(400, { error: 'password_required' })
  if (throttled(email)) return json(429, { error: 'too_many_attempts' })

  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } })
  const { data: id } = await admin.rpc('pending_unconfirmed_user', { p_email: email })
  // The same answer whether or not an account matched, so this cannot be used to look people up.
  if (typeof id !== 'string') return json(200, { ok: true })

  if (password) {
    const probe = createClient(url, anon, { auth: { persistSession: false, autoRefreshToken: false } })
    const { error } = await probe.auth.signInWithPassword({ email, password })
    const proven = error?.code === 'email_not_confirmed' || /not confirmed/i.test(error?.message ?? '')
    if (!proven) return json(200, { ok: true })   // wrong password: do nothing, say nothing
  }
  await admin.auth.admin.updateUserById(id, { email_confirm: true })
  return json(200, { ok: true })
})
