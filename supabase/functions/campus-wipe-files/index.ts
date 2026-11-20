// After the app manager wipes a campus, remove its files from Storage.
//
// The database deletes the rows and, in the SAME transaction, queues every file path
// (public.storage_cleanup_queue). So even if this function is interrupted, nothing is forgotten:
// the storage-cleanup function (or a retry of this one) finishes the job.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { ...cors, 'Content-Type': 'application/json' },
})
const CHUNK = 200

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  if (req.method !== 'POST') return json(405, { error: 'method_not_allowed' })

  const url = Deno.env.get('SUPABASE_URL')
  const anon = Deno.env.get('SUPABASE_ANON_KEY')
  const service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!url || !anon || !service) return json(500, { error: 'server_not_configured' })

  const authHeader = req.headers.get('Authorization') ?? ''
  if (!authHeader.startsWith('Bearer ')) return json(401, { error: 'missing_bearer_token' })
  const token = authHeader.slice('Bearer '.length)
  const userClient = createClient(url, anon, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  })
  const { data: userData, error: authError } = await userClient.auth.getUser(token)
  if (authError || !userData.user) return json(401, { error: 'invalid_session' })

  let input: { university_id?: string; scope?: string }
  try { input = await req.json() } catch { return json(400, { error: 'invalid_json' }) }
  const campusId = input.university_id ?? ''
  const scope = input.scope ?? ''
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
  const scopes = new Set(['announcements', 'timetable', 'materials', 'works', 'notifications', 'catalog', 'people', 'everything'])
  if (!uuid.test(campusId) || !scopes.has(scope)) return json(400, { error: 'invalid_campus_or_scope' })

  // The database's authorization-checked transaction, run with the caller's JWT (not service role).
  const { data: wipeResult, error: wipeError } = await userClient.rpc('platform_wipe_campus', {
    p_university: campusId,
    p_scope: scope,
  })
  if (wipeError) {
    const denied = wipeError.code === '42501' || /not_allowed|permission/i.test(wipeError.message)
    return json(denied ? 403 : 400, { error: denied ? 'not_authorized' : 'campus_wipe_failed', detail: wipeError.message })
  }

  const objects = wipeResult?.storage_objects_to_remove ?? {}
  const buckets = ['materials', 'submissions', 'announcements'] as const
  const serviceClient = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } })
  let removed = 0
  const failures: Array<{ bucket: string; count: number; message: string }> = []
  for (const bucket of buckets) {
    const paths: string[] = Array.isArray(objects[bucket])
      ? [...new Set<string>(objects[bucket].filter((p: unknown): p is string => typeof p === 'string' && p.length > 0))]
      : []
    for (let n = 0; n < paths.length; n += CHUNK) {
      const part = paths.slice(n, n + CHUNK)
      const { error } = await serviceClient.storage.from(bucket).remove(part)
      if (error) {
        failures.push({ bucket, count: part.length, message: error.message })   // stays queued: retried later
      } else {
        removed += part.length
        await serviceClient.rpc('storage_cleanup_done_paths', { p_bucket: bucket, p_paths: part })
      }
    }
  }
  // The database deletion is committed. Anything that failed is still in the queue and will be retried.
  return json(failures.length ? 207 : 200, {
    ok: failures.length === 0,
    removed_files: removed,
    failed_files: failures.reduce((sum, f) => sum + f.count, 0),
    failures,
    retry: failures.length ? 'queued: the storage-cleanup function will remove them' : undefined,
  })
})
