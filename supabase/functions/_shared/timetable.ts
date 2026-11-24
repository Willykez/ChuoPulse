export interface Proposal {
  programmes: Array<{ name: string }>
  courses: Array<{ code: string; title: string; programme: string }>
  assignments: Array<{ course: string; lecturer: string }>
  sessions: Array<{ course: string; lecturer: string; day: number; start: string; end: string; venue: string; kind: string }>
  rooms: string[]
  warnings: string[]
}

export interface KnownContext {
  courses: string[]
}

const LIMITS = { programmes: 40, courses: 300, assignments: 400, sessions: 400, rooms: 120, warnings: 40 }

const DAYS: Record<string, number> = {
  mon: 1, monday: 1, jumatatu: 1, jtatu: 1,
  tue: 2, tues: 2, tuesday: 2, jumanne: 2,
  wed: 3, weds: 3, wednesday: 3, jumatano: 3,
  thu: 4, thur: 4, thurs: 4, thursday: 4, alhamisi: 4,
  fri: 5, friday: 5, ijumaa: 5,
  sat: 6, saturday: 6, jumamosi: 6,
}

const KINDS = new Set(['lecture', 'lab', 'tutorial'])
const CODE = /^[A-Z0-9][A-Z0-9._-]{1,23}$/

export const clean = (v: unknown, max: number): string =>
  (typeof v === 'string' || typeof v === 'number' ? String(v) : '').replace(/\s+/g, ' ').trim().slice(0, max)

export function normaliseCode(v: unknown): string {
  return clean(v, 40).toUpperCase().replace(/\s+/g, '')
}

export function parseDay(v: unknown): number | null {
  if (typeof v === 'number' && Number.isInteger(v)) return v >= 1 && v <= 6 ? v : null
  const s = clean(v, 20).toLowerCase().replace(/[.,]/g, '')
  if (/^[1-6]$/.test(s)) return Number(s)
  return DAYS[s] ?? null
}

export function parseTime(v: unknown): string | null {
  const s = clean(v, 20).toLowerCase().replace(/\./g, ':').replace(/h(?=\d)/, ':').replace(/\s+/g, '')
  const m = s.match(/^(\d{1,2})(?::(\d{2}))?(am|pm)?$/)
  if (!m) return null
  let h = Number(m[1])
  const min = m[2] === undefined ? 0 : Number(m[2])
  if (min > 59) return null
  if (m[3]) {
    if (h < 1 || h > 12) return null
    if (m[3] === 'pm' && h < 12) h += 12
    if (m[3] === 'am' && h === 12) h = 0
  }
  if (h > 23) return null
  return `${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}`
}

const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : [])
const obj = (v: unknown): Record<string, unknown> => (v && typeof v === 'object' ? (v as Record<string, unknown>) : {})

export function normaliseTimetable(raw: unknown, known: KnownContext): Proposal {
  const input = obj(raw)
  const warnings: string[] = []
  const warn = (m: string) => { if (warnings.length < LIMITS.warnings) warnings.push(m) }

  const programmes: Proposal['programmes'] = []
  const seenProg = new Set<string>()
  for (const p of arr(input.programmes)) {
    const name = clean(obj(p).name ?? p, 120)
    if (!name || seenProg.has(name.toLowerCase())) continue
    seenProg.add(name.toLowerCase())
    if (programmes.length < LIMITS.programmes) programmes.push({ name })
  }

  const courses: Proposal['courses'] = []
  const seenCourse = new Set<string>()
  for (const c of arr(input.courses)) {
    const o = obj(c)
    const code = normaliseCode(o.code)
    if (!CODE.test(code)) { if (code) warn(`Skipped a course with an unusable code: ${clean(o.code, 30)}`); continue }
    if (seenCourse.has(code)) continue
    seenCourse.add(code)
    const title = clean(o.title, 160) || code
    const programme = clean(o.programme, 120)
    if (programme && !seenProg.has(programme.toLowerCase()) && programmes.length < LIMITS.programmes) {
      seenProg.add(programme.toLowerCase())
      programmes.push({ name: programme })
    }
    if (courses.length < LIMITS.courses) courses.push({ code, title, programme })
  }

  const known_ = new Set(known.courses.map(normaliseCode))
  const knowsCourse = (code: string) => seenCourse.has(code) || known_.has(code)

  const assignments: Proposal['assignments'] = []
  const seenAssign = new Set<string>()
  for (const a of arr(input.assignments)) {
    const o = obj(a)
    const course = normaliseCode(o.course)
    const lecturer = clean(o.lecturer, 100)
    if (!CODE.test(course) || !lecturer) continue
    const key = `${course}|${lecturer.toLowerCase()}`
    if (seenAssign.has(key)) continue
    seenAssign.add(key)
    if (!knowsCourse(course)) warn(`${course} is not in your catalog yet and was not listed as a course.`)
    if (assignments.length < LIMITS.assignments) assignments.push({ course, lecturer })
  }

  const sessions: Proposal['sessions'] = []
  const seenSession = new Set<string>()
  const rooms: string[] = []
  const seenRoom = new Set<string>()
  for (const s of arr(input.sessions)) {
    const o = obj(s)
    const course = normaliseCode(o.course)
    const day = parseDay(o.day)
    const start = parseTime(o.start)
    const end = parseTime(o.end)
    const venue = clean(o.venue, 60)
    const label = course || 'a session'
    if (!CODE.test(course)) { warn('Skipped a session without a usable course code.'); continue }
    if (day === null) { warn(`Skipped ${label}: day not understood (${clean(o.day, 20)}). Timetables run Monday to Saturday.`); continue }
    if (!start || !end) { warn(`Skipped ${label} on day ${day}: time not understood.`); continue }
    if (end <= start) { warn(`Skipped ${label}: it ends (${end}) before it starts (${start}).`); continue }
    if (!venue) { warn(`Skipped ${label} on day ${day} ${start}: no venue.`); continue }
    const lecturer = clean(o.lecturer, 100)
    const kindRaw = clean(o.kind, 20).toLowerCase()
    const kind = KINDS.has(kindRaw) ? kindRaw : 'lecture'
    const key = `${course}|${day}|${start}|${end}|${venue.toLowerCase()}`
    if (seenSession.has(key)) continue
    seenSession.add(key)
    if (!knowsCourse(course)) warn(`${course} is not in your catalog yet and was not listed as a course.`)
    if (sessions.length >= LIMITS.sessions) { warn('Too many sessions: only the first 400 were kept.'); break }
    sessions.push({ course, lecturer, day, start, end, venue, kind })
    if (!seenRoom.has(venue.toLowerCase())) { seenRoom.add(venue.toLowerCase()); if (rooms.length < LIMITS.rooms) rooms.push(venue) }
  }
  for (const r of arr(input.rooms)) {
    const name = clean(r, 60)
    if (name && !seenRoom.has(name.toLowerCase())) { seenRoom.add(name.toLowerCase()); if (rooms.length < LIMITS.rooms) rooms.push(name) }
  }
  for (const w of arr(input.warnings)) { const m = clean(w, 200); if (m) warn(m) }

  return { programmes, courses, assignments, sessions, rooms, warnings }
}
