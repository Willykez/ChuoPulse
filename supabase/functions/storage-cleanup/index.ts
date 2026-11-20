// Removes files from Storage that the database has queued (public.storage_cleanup_queue):
// files of deleted materials / submissions / announcements, replaced attachments, wiped campuses,
// and (only when app_settings.orphan_sweep = 'on') files nobody references any more.
//
// Who may call it (either is enough):
//   * a header  x-cleanup-secret: <CLEANUP_SECRET>   (for a scheduled job; set with `supabase secrets set CLEANUP_SECRET=...`)
//   * the app manager, signed in (database-checked)
// The gateway still needs a valid JWT (the anon key is fine for the scheduled job).
//
// Schedule it every 15 minutes, for example with pg_cron + pg_net, or Supabase's scheduled functions.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info, x-cleanup-secret',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { ...cors, 'Content-Type': 'application/json' },
})
const BATCH = 200
const MAX_ROUNDS = 5

function sameSecret(a: string, b: string): boolean {
  if (a.length !== b.length || a.length === 0) return false
  let diff = 0
  for (let k = 0; k < a.length; k++) diff |= a.charCodeAt(k) ^ b.charCodeAt(k)
  return diff === 0
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors })
  if (req.method !== 'POST') return json(405, { error: 'method_not_allowed' })

  const url = Deno.env.get('SUPABASE_URL')
  const anon = Deno.env.get('SUPABASE_ANON_KEY')
  const service = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
  if (!url || !anon || !service) return json(500, { error: 'server_not_configured' })

  const secret = Deno.env.get('CLEANUP_SECRET') ?? ''
  let authorised = sameSecret(req.headers.get('x-cleanup-secret') ?? '', secret)
  if (!authorised) {
    const authHeader = req.headers.get('Authorization') ?? ''
    if (authHeader.startsWith('Bearer ')) {
      const userClient = createClient(url, anon, {
        global: { headers: { Authorization: authHeader } },
        auth: { persistSession: false, autoRefreshToken: false },
      })
      const { data } = await userClient.rpc('is_platform_admin')
      authorised = data === true
    }
  }
  if (!authorised) return json(403, { error: 'not_authorized' })

  const admin = createClient(url, service, { auth: { persistSession: false, autoRefreshToken: false } })
  let removed = 0
  let failed = 0
  for (let round = 0; round < MAX_ROUNDS; round++) {
    const { data: rows, error } = await admin.rpc('storage_cleanup_next', { p_limit: BATCH })
    if (error) return json(500, { error: 'queue_unavailable', detail: error.message })
    if (!Array.isArray(rows) || rows.length === 0) break

    const byBucket = new Map<string, Array<{ id: number; path: string }>>()
    for (const r of rows as Array<{ id: number; bucket: string; path: string }>) {
      const list = byBucket.get(r.bucket) ?? []
      list.push({ id: r.id, path: r.path })
      byBucket.set(r.bucket, list)
    }
    for (const [bucket, items] of byBucket) {
      const { error: rmError } = await admin.storage.from(bucket).remove(items.map((x) => x.path))
      if (rmError) {
        failed += items.length
        for (const x of items) await admin.rpc('storage_cleanup_failed', { p_id: x.id, p_error: rmError.message })
      } else {
        removed += items.length
        await admin.rpc('storage_cleanup_done', { p_ids: items.map((x) => x.id) })
      }
    }
  }
  return json(200, { ok: failed === 0, removed, failed })
})
