// Run:  deno run --allow-all --import-map=import_map.json run.ts
import { calls } from './supabase_stub.ts'
import { normaliseTimetable, parseDay, parseTime, normaliseCode } from '../../functions/_shared/timetable.ts'
// deno-lint-ignore no-explicit-any
const g = globalThis as any
Deno.env.set('SUPABASE_URL', 'http://x'); Deno.env.set('SUPABASE_ANON_KEY', 'anon-key'); Deno.env.set('SUPABASE_SERVICE_ROLE_KEY', 'service-key')

const handlers: Record<string, (r: Request) => Promise<Response> | Response> = {}
let current = ''
// deno-lint-ignore no-explicit-any
;(Deno as any).serve = (h: any) => { handlers[current] = h }
for (const f of ['confirm-signup', 'admin-manage-user', 'campus-wipe-files', 'storage-cleanup', 'ai-analyze', 'ai-timetable']) {
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


// ---------------- timetable normaliser
check('time: plain / padded / am-pm / dotted / h-form', parseTime('8') === '08:00' && parseTime('8:30') === '08:30' && parseTime('2pm') === '14:00' && parseTime('2:15 PM') === '14:15' && parseTime('8.30') === '08:30' && parseTime('14h00') === '14:00' && parseTime('12am') === '00:00' && parseTime('12pm') === '12:00')
check('time: nonsense rejected', parseTime('25:00') === null && parseTime('9:75') === null && parseTime('abc') === null && parseTime('') === null && parseTime('13pm') === null && parseTime(null) === null)
check('day: English, Swahili, numbers; Sunday and 0/7 rejected', parseDay('Mon') === 1 && parseDay('Tuesday') === 2 && parseDay('Jumatano') === 3 && parseDay('alhamisi') === 4 && parseDay('Ijumaa') === 5 && parseDay('Sat.') === 6 && parseDay(3) === 3 && parseDay('4') === 4 && parseDay('Sunday') === null && parseDay(0) === null && parseDay(7) === null && parseDay('Jumapili') === null)
check('course code is tidied', normaliseCode(' cs 210 ') === 'CS210')
const messy = {
  programmes: [{ name: 'BSc Computer Science' }, 'bsc computer science', { name: '' }, null],
  courses: [{ code: 'cs 210', title: 'Databases', programme: 'BSc Computer Science' }, { code: 'CS210', title: 'dup' }, { code: '!!', title: 'bad' }, { code: 'is202', title: '', programme: 'BSc IT' }],
  assignments: [{ course: 'cs210', lecturer: ' Dr. John  Mwita ' }, { course: 'CS210', lecturer: 'dr. john mwita' }, { course: 'CS210', lecturer: '' }],
  sessions: [
    { course: 'cs210', lecturer: 'Dr. John Mwita', day: 'Monday', start: '8', end: '10am', venue: ' LH1 ', kind: 'LAB' },
    { course: 'CS210', day: 'Mon', start: '08:00', end: '10:00', venue: 'lh1' },
    { course: 'CS210', day: 'Sunday', start: '08:00', end: '10:00', venue: 'LH1' },
    { course: 'CS210', day: 'Tue', start: '10:00', end: '09:00', venue: 'LH2' },
    { course: 'CS210', day: 'Tue', start: '09:00', end: '11:00', venue: '' },
    { course: 'IS202', day: 3, start: '14h00', end: '16:00', venue: 'Lab 2', kind: 'party' },
    { course: 'XX999', day: 'Fri', start: '09:00', end: '10:00', venue: 'LH3' },
    { day: 'Fri' },
  ],
  rooms: ['LH1', 'Hall A', ''],
  warnings: ['The document is hard to read.'],
}
const n = normaliseTimetable(messy, { courses: [] })
check('normaliser: programmes de-duplicated, auto-added from courses', n.programmes.map((p) => p.name).join('|') === 'BSc Computer Science|BSc IT')
check('normaliser: courses cleaned, duplicates and bad codes dropped, title defaults to code', n.courses.length === 2 && n.courses[0].code === 'CS210' && n.courses[1].title === 'IS202')
check('normaliser: assignments de-duplicated case-insensitively, blank lecturer dropped', n.assignments.length === 1 && n.assignments[0].lecturer === 'Dr. John Mwita')
check('normaliser: first session coerced (8 -> 08:00, 10am -> 10:00, trimmed venue, lab)', JSON.stringify(n.sessions[0]) === JSON.stringify({ course: 'CS210', lecturer: 'Dr. John Mwita', day: 1, start: '08:00', end: '10:00', venue: 'LH1', kind: 'lab' }))
check('normaliser: duplicate slot, Sunday, reversed time and missing venue are dropped', n.sessions.length === 3 && n.sessions.every((s) => s.start < s.end))
check('normaliser: unknown kind becomes lecture; 14h00 understood', n.sessions[1].kind === 'lecture' && n.sessions[1].start === '14:00')
check('normaliser: every dropped row explains itself', n.warnings.some((w) => w.includes('Sunday') || w.includes('day not understood')) && n.warnings.some((w) => w.includes('before it starts')) && n.warnings.some((w) => w.includes('no venue')))
check('normaliser: session for a course not in the catalog is kept but warned', n.sessions.some((s) => s.course === 'XX999') && n.warnings.some((w) => w.includes('XX999')))
check('normaliser: rooms gathered from sessions and the list, no blanks, no duplicates', n.rooms.join('|') === 'LH1|Lab 2|LH3|Hall A')
check('normaliser: a course already in the campus catalog does not warn', !normaliseTimetable({ sessions: [{ course: 'CS300', day: 1, start: '08:00', end: '09:00', venue: 'A' }] }, { courses: ['cs300'] }).warnings.length)
check('normaliser: survives garbage', JSON.stringify(normaliseTimetable('nonsense', { courses: [] })) === JSON.stringify({ programmes: [], courses: [], assignments: [], sessions: [], rooms: [], warnings: [] }) && normaliseTimetable(null, { courses: [] }).sessions.length === 0 && normaliseTimetable({ sessions: 'x', courses: 5 }, { courses: [] }).sessions.length === 0)
const many = { sessions: Array.from({ length: 500 }, (_, k) => ({ course: 'CS210', day: 1 + (k % 6), start: `${String(6 + (k % 12)).padStart(2, '0')}:00`, end: `${String(7 + (k % 12)).padStart(2, '0')}:00`, venue: `R${k}` })) }
check('normaliser: caps at 400 sessions with a warning', normaliseTimetable(many, { courses: ['CS210'] }).sessions.length === 400 && normaliseTimetable(many, { courses: ['CS210'] }).warnings.some((w) => w.includes('400')))
check('normaliser: very long strings are clipped', normaliseTimetable({ courses: [{ code: 'CS1', title: 'x'.repeat(1000) }] }, { courses: [] }).courses[0].title.length === 160)

// ---------------- ai-timetable
const TT_TEXT = 'Monday 08:00-10:00 CS210 Databases (Dr Mwita) LH1. Tuesday 14:00-16:00 IS202 Systems Analysis Lab 2. '.repeat(3)
const ttCfg = (t: string) => t === 'ai_settings' ? { data: { provider: 'gemini', model: 'm', api_key: 'KEY', enabled: true } } : { data: null }
const ttRpc = (admin: boolean, manager: boolean, take = true) => (w: string, n: string) => n === 'is_admin' ? { data: admin, error: null } : n === 'is_platform_admin' ? { data: manager, error: null } : n === 'ai_take_use' ? { data: take, error: null } : { data: true, error: null }
const aiJson = { candidates: [{ content: { parts: [{ text: JSON.stringify({ programmes: [], courses: [{ code: 'cs210', title: 'Databases', programme: 'BSc CS' }], assignments: [], sessions: [{ course: 'CS210', lecturer: 'Dr Mwita', day: 'Mon', start: '8', end: '10', venue: 'LH1' }], rooms: [], warnings: [] }) }] } }] }
let sentToModel = ''
globalThis.fetch = ((_u: string, init?: RequestInit) => { sentToModel = String(init?.body ?? ''); return Promise.resolve(new Response(JSON.stringify(aiJson), { status: 200 })) }) as typeof fetch
base(); g.__mock.table = ttCfg; g.__mock.rpc = ttRpc(false, false)
r = await run('ai-timetable', req({ text: TT_TEXT }, AUTH))
check('timetable ai: a non-administrator is refused (403) before any AI call', r.status === 403 && did('rpc:ai_take_use').length === 0)
g.__mock.rpc = ttRpc(true, true); r = await run('ai-timetable', req({ text: TT_TEXT }, AUTH))
check('timetable ai: the app manager is refused (campus administrators only)', r.status === 403)
g.__mock.rpc = ttRpc(true, false)
r = await run('ai-timetable', req({ text: TT_TEXT, context: { programmes: ['BSc CS'], courses: ['CS210'], lecturers: ['Dr John Mwita'], rooms: ['LH1'] } }, AUTH))
check('timetable ai: administrator gets a NORMALISED proposal (08:00-10:00, Monday = 1)', r.status === 200 && r.body.proposal.sessions[0].start === '08:00' && r.body.proposal.sessions[0].end === '10:00' && r.body.proposal.sessions[0].day === 1 && r.body.proposal.courses[0].code === 'CS210')
check('timetable ai: the campus catalog names are sent to the model, never ids or emails', sentToModel.includes('Dr John Mwita') && sentToModel.includes('BSc CS') && !sentToModel.includes('@') && !/[0-9a-f]{8}-[0-9a-f]{4}/.test(sentToModel))
check('timetable ai: document is marked untrusted in the prompt', sentToModel.includes('untrusted'))
check('timetable ai: charged once, not refunded', did('rpc:ai_take_use').length === 1 && did('rpc:ai_refund_use').length === 0)
r = await run('ai-timetable', req({ text: 'too short' }, AUTH)); check('timetable ai: short text -> 400', r.status === 400)
r = await run('ai-timetable', req({ text: TT_TEXT }, { ...AUTH, 'content-length': '9999999' })); check('timetable ai: oversized -> 413', r.status === 413)
g.__mock.rpc = ttRpc(true, false, false); r = await run('ai-timetable', req({ text: TT_TEXT }, AUTH))
check('timetable ai: daily limit -> 429, nothing refunded', r.status === 429 && did('rpc:ai_refund_use').length === 0)
g.__mock.rpc = ttRpc(true, false); globalThis.fetch = (() => Promise.resolve(new Response('upstream down', { status: 500 }))) as typeof fetch
r = await run('ai-timetable', req({ text: TT_TEXT }, AUTH))
check('timetable ai: provider failure -> 502, REFUNDED, provider text not echoed', r.status === 502 && did('rpc:ai_refund_use').length === 1 && !JSON.stringify(r.body).includes('upstream'))
globalThis.fetch = (() => Promise.resolve(new Response(JSON.stringify({ candidates: [{ content: { parts: [{ text: 'not json at all' }] } }] }), { status: 200 }))) as typeof fetch
r = await run('ai-timetable', req({ text: TT_TEXT }, AUTH)); check('timetable ai: unreadable model output -> 502 and refunded', r.status === 502 && did('rpc:ai_refund_use').length === 1)
r = await run('ai-timetable', new Request('http://f', { method: 'POST', body: '{}' })); check('timetable ai: no token -> 401', r.status === 401)
globalThis.fetch = realFetch

console.log(`edge functions: ${pass} passed, ${fail} failed`)
Deno.exit(fail ? 1 : 0)
