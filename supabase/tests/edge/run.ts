// Run:  deno run --allow-all --import-map=import_map.json run.ts
import { calls } from './supabase_stub.ts'
// deno-lint-ignore no-explicit-any
const g = globalThis as any
Deno.env.set('SUPABASE_URL', 'http://x'); Deno.env.set('SUPABASE_ANON_KEY', 'anon-key'); Deno.env.set('SUPABASE_SERVICE_ROLE_KEY', 'service-key')

const handlers: Record<string, (r: Request) => Promise<Response> | Response> = {}
let current = ''
// deno-lint-ignore no-explicit-any
;(Deno as any).serve = (h: any) => { handlers[current] = h }
for (const f of ['confirm-signup', 'admin-manage-user', 'campus-wipe-files', 'storage-cleanup', 'ai-analyze']) {
  current = f; await import(`../../functions/${f}/index.ts`)
}
let pass = 0, fail = 0
const check = (label: string, ok: boolean) => { ok ? pass++ : (fail++, console.log('  FAIL', label)); }
const req = (body: unknown, headers: Record<string, string> = {}) => new Request('http://f', { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) })
const run = async (fn: string, r: Request) => { calls.length = 0; const res = await handlers[fn](r); return { status: res.status, body: await res.json().catch(() => ({})) } }
const did = (op: string, who?: string) => calls.filter((c) => c.op === op && (!who || c.who === who))
const idx = (op: string) => calls.findIndex((c) => c.op === op)
const UID = '11111111-1111-4111-8111-111111111111'
const base = () => { g.__mock = { getUser: () => ({ data: { user: { id: 'caller' } }, error: null }), rpc: () => ({ data: true, error: null }), signIn: () => ({ error: null }), table: () => ({ data: null }), remove: () => ({ data: [], error: null }) } }
const AUTH = { Authorization: 'Bearer tok' }

// ---------------- confirm-signup
base(); g.__mock.rpc = (_w: string, n: string) => ({ data: n === 'pending_unconfirmed_user' ? 'uid-1' : null, error: null })
let r = await run('confirm-signup', req({ email: 'a@b.co' }))
check('confirm: no password -> 400', r.status === 400 && r.body.error === 'password_required' && did('admin.updateUserById').length === 0)
g.__mock.signIn = () => ({ error: { code: 'invalid_credentials', message: 'Invalid login credentials' } })
r = await run('confirm-signup', req({ email: 'b@b.co', password: 'wrongwrong' }))
check('confirm: wrong password -> 200 but NOT confirmed', r.status === 200 && did('admin.updateUserById').length === 0)
g.__mock.signIn = () => ({ error: { code: 'email_not_confirmed', message: 'Email not confirmed' } })
r = await run('confirm-signup', req({ email: 'c@b.co', password: 'rightright' }))
check('confirm: right password -> confirmed', r.status === 200 && did('admin.updateUserById').length === 1 && (did('admin.updateUserById')[0].args as { email_confirm: boolean }).email_confirm === true)
g.__mock.rpc = () => ({ data: null, error: null })
r = await run('confirm-signup', req({ email: 'nobody@b.co', password: 'whatever1' }))
check('confirm: unknown e-mail -> same 200, nothing happens', r.status === 200 && r.body.ok === true && did('auth.signInWithPassword').length === 0)
g.__mock.rpc = (_w: string, n: string) => ({ data: n === 'pending_unconfirmed_user' ? 'uid-1' : null, error: null })
Deno.env.set('CONFIRM_SIGNUP_LEGACY', 'true')
r = await run('confirm-signup', req({ email: 'd@b.co' }))
check('confirm: legacy switch accepts e-mail alone', r.status === 200 && did('admin.updateUserById').length === 1)
Deno.env.delete('CONFIRM_SIGNUP_LEGACY')
let last = 200; for (let k = 0; k < 7; k++) last = (await run('confirm-signup', req({ email: 'spam@b.co', password: 'xxxxxxxx' }))).status
check('confirm: hammering one address gets throttled (429)', last === 429)

// ---------------- admin-manage-user
base(); r = await run('admin-manage-user', req({ action: 'pause', user_id: UID }))
check('manage: no bearer -> 401', r.status === 401)
base(); g.__mock.rpc = (w: string, n: string) => n === 'can_manage_user' ? { data: false, error: null } : { data: true, error: null }
r = await run('admin-manage-user', req({ action: 'reset_password', user_id: UID, password: 'longenough' }, AUTH))
check('manage: not allowed -> 403, nothing touched', r.status === 403 && did('admin.updateUserById').length === 0 && did('rpc:log_account_action').length === 0)
base(); r = await run('admin-manage-user', req({ action: 'reset_password', user_id: UID, password: 'longenough' }, AUTH))
check('manage: reset -> audit BEFORE change, then sessions ended', r.status === 200 && idx('rpc:log_account_action') >= 0 && idx('rpc:log_account_action') < idx('admin.updateUserById') && did('rpc:revoke_user_sessions', 'admin').length === 1)
base(); r = await run('admin-manage-user', req({ action: 'reset_password', user_id: UID, password: 'short' }, AUTH))
check('manage: weak password -> 400, no audit, no change', r.status === 400 && did('admin.updateUserById').length === 0)
base(); g.__mock.rpc = (_w: string, n: string) => n === 'log_account_action' ? { data: null, error: { message: 'x' } } : { data: true, error: null }
r = await run('admin-manage-user', req({ action: 'reset_password', user_id: UID, password: 'longenough' }, AUTH))
check('manage: audit cannot be written -> 500 and NOTHING happens', r.status === 500 && r.body.error === 'audit_failed' && did('admin.updateUserById').length === 0)
base(); r = await run('admin-manage-user', req({ action: 'erase', user_id: UID }, AUTH))
check('manage: erase -> audit, erase_person_data, account locked, NEVER deleteUser', r.status === 200 && did('rpc:erase_person_data', 'admin').length === 1 && did('admin.deleteUser').length === 0
  && (did('admin.updateUserById')[0].args as { ban_duration: string; email: string }).ban_duration === '876000h' && (did('admin.updateUserById')[0].args as { email: string }).email.endsWith('@invalid.invalid'))
base(); r = await run('admin-manage-user', req({ action: 'delete', user_id: UID }, AUTH))
check('manage: delete (clean) -> deleteUser, not soft', r.status === 200 && !r.body.soft && did('admin.deleteUser').length === 1 && did('rpc:erase_person_data').length === 0)
base(); g.__mock.deleteUser = () => ({ error: { message: 'fk violation' } })
r = await run('admin-manage-user', req({ action: 'delete', user_id: UID }, AUTH))
check('manage: delete blocked by FK -> record kept, erased + locked', r.status === 200 && r.body.soft === true && did('rpc:erase_person_data').length === 1 && did('admin.updateUserById').length === 1)
base(); r = await run('admin-manage-user', req({ action: 'pause', user_id: UID, reason: 'x' }, AUTH))
check('manage: pause -> status, ban and sessions ended', r.status === 200 && did('rpc:admin_set_status', 'user').length === 1 && did('admin.updateUserById').length === 1 && did('rpc:revoke_user_sessions').length === 1)
base(); r = await run('admin-manage-user', req({ action: 'nope', user_id: UID }, AUTH))
check('manage: unknown action -> 400', r.status === 400)

// ---------------- campus-wipe-files
const WID = '22222222-2222-4222-8222-222222222222'
base(); g.__mock.rpc = (_w: string, n: string) => n === 'platform_wipe_campus'
  ? { data: { storage_objects_to_remove: { materials: ['m/1', 'm/2', 'm/1'], submissions: ['s/1'], announcements: [] } }, error: null } : { data: 1, error: null }
r = await run('campus-wipe-files', req({ university_id: WID, scope: 'everything' }, AUTH))
check('wipe: files removed per bucket, de-duplicated, queue marked done', r.status === 200 && r.body.removed_files === 3 && did('storage.remove').length === 2 && did('rpc:storage_cleanup_done_paths').length === 2)
base(); g.__mock.rpc = (_w: string, n: string) => n === 'platform_wipe_campus' ? { data: { storage_objects_to_remove: { materials: ['m/1'], submissions: ['s/1'], announcements: [] } }, error: null } : { data: 1, error: null }
g.__mock.remove = (b: string) => b === 'materials' ? { error: { message: 'boom' } } : { data: [], error: null }
r = await run('campus-wipe-files', req({ university_id: WID, scope: 'everything' }, AUTH))
check('wipe: a failed bucket -> 207, stays queued (not marked done)', r.status === 207 && r.body.failed_files === 1 && did('rpc:storage_cleanup_done_paths').length === 1)
base(); g.__mock.rpc = () => ({ data: null, error: { code: '42501', message: 'not_allowed' } })
r = await run('campus-wipe-files', req({ university_id: WID, scope: 'everything' }, AUTH))
check('wipe: not the manager -> 403, no storage calls', r.status === 403 && did('storage.remove').length === 0)
base(); r = await run('campus-wipe-files', req({ university_id: 'nope', scope: 'drop table' }, AUTH))
check('wipe: bad input -> 400', r.status === 400)

// ---------------- storage-cleanup
Deno.env.set('CLEANUP_SECRET', 'topsecret')
base(); g.__mock.rpc = (w: string, n: string) => n === 'is_platform_admin' ? { data: false, error: null } : { data: [], error: null }
r = await run('storage-cleanup', req({}, AUTH))
check('cleanup: neither secret nor manager -> 403', r.status === 403)
base(); g.__mock.rpc = (_w: string, n: string) => n === 'storage_cleanup_next' ? ({ data: g.__rows.splice(0), error: null }) : { data: 1, error: null }
g.__rows = [{ id: 1, bucket: 'materials', path: 'a' }, { id: 2, bucket: 'materials', path: 'b' }, { id: 3, bucket: 'submissions', path: 'c' }]
r = await run('storage-cleanup', req({}, { 'x-cleanup-secret': 'topsecret' }))
check('cleanup: secret ok -> grouped by bucket, removed, marked done', r.status === 200 && r.body.removed === 3 && did('storage.remove').length === 2 && did('rpc:storage_cleanup_done').length === 2)
g.__rows = [{ id: 9, bucket: 'materials', path: 'z' }]; g.__mock.remove = () => ({ error: { message: 'down' } })
r = await run('storage-cleanup', req({}, { 'x-cleanup-secret': 'topsecret' }))
check('cleanup: storage error -> recorded as failed, not marked done', r.body.failed === 1 && did('rpc:storage_cleanup_failed').length === 1 && did('rpc:storage_cleanup_done').length === 0)
r = await run('storage-cleanup', req({}, { 'x-cleanup-secret': 'wrong' }))
check('cleanup: wrong secret -> 403', r.status === 403)

// ---------------- ai-analyze
const TEXT = 'This is a long enough document about relational databases and normal forms. '.repeat(3)
const aiBase = (over: Record<string, unknown> = {}) => { base(); g.__mock.table = (t: string) => t === 'profiles' ? { data: { status: 'approved', is_platform_admin: false, university_id: 'u1', ...over } }
  : t === 'universities' ? { data: { under_maintenance: false } } : { data: { provider: 'gemini', model: 'm', api_key: 'KEY', enabled: true } } }
const okGemini = { candidates: [{ content: { parts: [{ text: JSON.stringify({ summary: 'S', key_topics: ['a'], objectives: ['b'], study_tips: ['c'] }) }] } }] }
const realFetch = globalThis.fetch
globalThis.fetch = (() => Promise.resolve(new Response(JSON.stringify(okGemini), { status: 200 }))) as typeof fetch
aiBase(); r = await run('ai-analyze', req({ text: TEXT }, AUTH))
check('ai: success -> 200, charged once, no refund', r.status === 200 && r.body.summary === 'S' && did('rpc:ai_take_use').length === 1 && did('rpc:ai_refund_use').length === 0)
globalThis.fetch = (() => Promise.resolve(new Response('quota exceeded', { status: 500 }))) as typeof fetch
aiBase(); r = await run('ai-analyze', req({ text: TEXT }, AUTH))
check('ai: provider fails -> 502 and the attempt is REFUNDED', r.status === 502 && r.body.error === 'ai_failed' && did('rpc:ai_refund_use').length === 1)
globalThis.fetch = (() => Promise.resolve(new Response('bad key', { status: 403 }))) as typeof fetch
aiBase(); r = await run('ai-analyze', req({ text: TEXT }, AUTH))
check('ai: rejected key -> 502 ai_key_rejected, refunded, provider text not echoed', r.body.error === 'ai_key_rejected' && did('rpc:ai_refund_use').length === 1 && !JSON.stringify(r.body).includes('bad key'))
globalThis.fetch = (() => Promise.resolve(new Response(JSON.stringify(okGemini), { status: 200 }))) as typeof fetch
aiBase(); g.__mock.rpc = (_w: string, n: string) => n === 'ai_take_use' ? { data: false, error: null } : { data: true, error: null }
r = await run('ai-analyze', req({ text: TEXT }, AUTH))
check('ai: daily limit -> 429, nothing refunded (nothing counted)', r.status === 429 && did('rpc:ai_refund_use').length === 0)
aiBase(); g.__mock.table = (t: string) => t === 'profiles' ? { data: { status: 'approved', is_platform_admin: false, university_id: 'u1' } } : t === 'universities' ? { data: { under_maintenance: true } } : { data: null }
r = await run('ai-analyze', req({ text: TEXT }, AUTH))
check('ai: paused campus -> 503, no quota used', r.status === 503 && r.body.error === 'campus_paused' && did('rpc:ai_take_use').length === 0)
aiBase({ is_platform_admin: true }); r = await run('ai-analyze', req({ text: TEXT }, AUTH))
check('ai: app manager is not metered', r.status === 200 && did('rpc:ai_take_use').length === 0)
aiBase({ status: 'pending' }); r = await run('ai-analyze', req({ text: TEXT }, AUTH))
check('ai: pending person -> 403', r.status === 403)
aiBase(); r = await run('ai-analyze', req({ text: TEXT }, { ...AUTH, 'content-length': '999999' }))
check('ai: oversized body -> 413', r.status === 413)
aiBase(); r = await run('ai-analyze', req({ text: 'too short' }, AUTH))
check('ai: short text -> 400', r.status === 400)
globalThis.fetch = realFetch

console.log(`edge functions: ${pass} passed, ${fail} failed`)
Deno.exit(fail ? 1 : 0)
