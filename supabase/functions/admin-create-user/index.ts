// Creates a sign-in account (already confirmed, with a password) AND its profile, for someone else.
// Used by the app manager (any campus, any role) and by a campus administrator (own campus,
// student or lecturer). The database decides who may (public.can_provision, run with the CALLER's JWT);
// the service-role key lives only here, in the function's own environment, never in the app.
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
  const code = str('university_code').toUpperCase()
  const role = str('role').toLowerCase()
  const fullName = str('full_name')
  const email = str('email').toLowerCase()
  const idNumber = str('id_number')
  const programme = str('programme')
  const password = typeof i.password === 'string' ? i.password : ''

  if (!['student', 'lecturer', 'admin'].includes(role)) return json(400, { error: 'invalid_role' })
  if (fullName.length < 3) return json(400, { error: 'invalid_name' })
  if (!EMAIL.test(email)) return json(400, { error: 'invalid_email' })
  if (idNumber.length < 2) return json(400, { error: 'invalid_number' })
  if (password.length < 8) return json(400, { error: 'weak_password' })

  // Authorization, decided by the database for the person who is calling.
  const { data: allowed, error: allowError } = await userClient.rpc('can_provision', {
    p_university_code: code, p_role: role,
  })
  if (allowError || allowed !== true) return json(403, { error: 'not_authorized' })

  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } })
  const { data: uni } = await admin.from('universities').select('id').eq('code', code).eq('is_platform', false).maybeSingle()
  if (!uni) return json(404, { error: 'unknown_campus' })

  // app_metadata can only be written here (service role); the sign-up trigger trusts it.
  const { data, error } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { full_name: fullName },
    app_metadata: {
      provisioned: true, role, university: code, id_number: idNumber,
      ...(role === 'student' ? { programme } : { department: programme }),
    },
  })
  if (error) {
    const msg = (error.message ?? '').toLowerCase()
    if (error.code === 'email_exists' || msg.includes('already')) return json(409, { error: 'email_taken' })
    if (msg.includes('number_taken')) return json(409, { error: 'number_taken' })
    if (msg.includes('password')) return json(400, { error: 'weak_password' })
    return json(400, { error: 'create_failed', detail: error.message })
  }
  return json(200, { ok: true, user_id: data.user?.id })
})
