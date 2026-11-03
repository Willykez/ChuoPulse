// Marks a freshly registered, still-pending account's email as confirmed so the person can sign in
// without an emailed code. The app calls it only AFTER the server has accepted the person's own
// password (GoTrue answers "email_not_confirmed" only for correct credentials), and an
// administrator still has to approve the account before it can do anything.
// Deploy WITHOUT JWT verification (the caller is not signed in yet):
//   supabase functions deploy confirm-signup --no-verify-jwt
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

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  if (req.method !== 'POST') return json(405, { error: 'method_not_allowed' })
  const url = Deno.env.get('SUPABASE_URL')
  const service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!url || !service) return json(500, { error: 'server_not_configured' })

  let body: { email?: unknown }
  try { body = await req.json() } catch { return json(400, { error: 'invalid_json' }) }
  const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : ''
  if (!EMAIL.test(email)) return json(400, { error: 'invalid_email' })

  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } })
  const { data: id } = await admin.rpc('pending_unconfirmed_user', { p_email: email })
  // The same answer whether or not an account matched, so this cannot be used to look people up.
  if (typeof id === 'string') await admin.auth.admin.updateUserById(id, { email_confirm: true })
  return json(200, { ok: true })
})
