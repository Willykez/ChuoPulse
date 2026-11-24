package com.willykez.campconnect.data.docs

import org.json.JSONArray
import org.json.JSONObject

data class LecturerRef(val id: String, val name: String)

data class SessionRef(
  val course: String,
  val lecturerId: String,
  val day: Int,
  val start: String,
  val end: String,
  val venue: String
)

data class CatalogContext(
  val programmes: Set<String>,
  val courses: Set<String>,
  val lecturers: List<LecturerRef>,
  val rooms: Set<String>,
  val sessions: List<SessionRef>
)

data class Issue(val text: String, val blocking: Boolean)

data class PProgramme(val name: String, val include: Boolean = true)

data class PCourse(
  val code: String,
  val title: String,
  val programme: String,
  val include: Boolean = true
)

data class PAssignment(
  val course: String,
  val lecturerName: String,
  val lecturerId: String?,
  val include: Boolean = true
)

data class PSession(
  val id: Int,
  val course: String,
  val lecturerName: String,
  val lecturerId: String?,
  val day: Int,
  val start: String,
  val end: String,
  val venue: String,
  val kind: String,
  val include: Boolean = true,
  val issues: List<Issue> = emptyList()
) {
  val blocked: Boolean get() = issues.any { it.blocking }
  val willApply: Boolean get() = include && !blocked
}

data class TimetableProposal(
  val programmes: List<PProgramme>,
  val courses: List<PCourse>,
  val assignments: List<PAssignment>,
  val sessions: List<PSession>,
  val rooms: List<String>,
  val warnings: List<String>
) {
  val sessionsToApply: List<PSession> get() = sessions.filter { it.willApply }
  val assignmentsToApply: List<PAssignment> get() = assignments.filter { it.include && it.lecturerId != null }
  val coursesToApply: List<PCourse> get() = courses.filter { it.include }
  val programmesToApply: List<PProgramme> get() = programmes.filter { it.include }
  val isEmpty: Boolean get() = programmes.isEmpty() && courses.isEmpty() && assignments.isEmpty() && sessions.isEmpty()
  val itemsToApply: Int
    get() = programmesToApply.size + coursesToApply.size + assignmentsToApply.size + sessionsToApply.size
}

sealed interface LecturerMatch {
  data class Found(val ref: LecturerRef) : LecturerMatch
  data object Ambiguous : LecturerMatch
  data object None : LecturerMatch
}

object TimetableLogic {
  private val TITLES = setOf(
    "dr", "prof", "professor", "mr", "mrs", "ms", "miss", "eng", "engr", "ir", "rev", "fr", "sr", "mwl", "mwalimu",
    "dkt", "daktari", "bw", "bi", "lecturer", "tutor", "assoc", "associate", "asst", "assistant", "phd", "msc", "bsc"
  )

  fun nameTokens(name: String): List<String> =
    name.lowercase()
      .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
      .split(Regex("\\s+"))
      .filter { it.isNotBlank() && it !in TITLES }

  private fun tokenFits(proposed: String, candidate: String): Boolean =
    proposed == candidate || (proposed.length == 1 && candidate.startsWith(proposed)) || (candidate.length == 1 && proposed.startsWith(candidate))

  private fun fits(proposed: List<String>, candidate: List<String>): Boolean {
    if (proposed.isEmpty() || candidate.isEmpty()) return false
    val shorter = if (proposed.size <= candidate.size) proposed else candidate
    val longer = if (proposed.size <= candidate.size) candidate else proposed
    if (shorter.size < 2 && longer.size > 1 && shorter[0].length == 1) return false
    val used = BooleanArray(longer.size)
    for (t in shorter) {
      val at = longer.indices.firstOrNull { !used[it] && tokenFits(t, longer[it]) } ?: return false
      used[at] = true
    }
    return true
  }

  fun match(name: String, lecturers: List<LecturerRef>): LecturerMatch {
    val wanted = nameTokens(name)
    if (wanted.isEmpty()) return LecturerMatch.None
    val exact = lecturers.filter { nameTokens(it.name).sorted() == wanted.sorted() }
    if (exact.size == 1) return LecturerMatch.Found(exact[0])
    if (exact.size > 1) return LecturerMatch.Ambiguous
    val loose = lecturers.filter { fits(wanted, nameTokens(it.name)) }
    return when (loose.size) {
      0 -> LecturerMatch.None
      1 -> LecturerMatch.Found(loose[0])
      else -> LecturerMatch.Ambiguous
    }
  }

  fun contextJson(ctx: CatalogContext): JSONObject = JSONObject()
    .put("programmes", JSONArray(ctx.programmes.sorted().take(100)))
    .put("courses", JSONArray(ctx.courses.sorted().take(2000)))
    .put("lecturers", JSONArray(ctx.lecturers.map { it.name }.distinct().take(500)))
    .put("rooms", JSONArray(ctx.rooms.sorted().take(200)))

  private fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()

  private fun JSONObject.objects(key: String): List<JSONObject> =
    optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()

  fun parse(json: JSONObject, ctx: CatalogContext): TimetableProposal {
    val knownProgrammes = ctx.programmes.map { it.lowercase() }.toSet()
    val knownCourses = ctx.courses.map { it.uppercase() }.toSet()

    val programmes = json.objects("programmes")
      .map { it.optString("name").trim() }
      .filter { it.isNotEmpty() && it.lowercase() !in knownProgrammes }
      .map { PProgramme(it) }

    val courses = json.objects("courses")
      .filter { it.optString("code").uppercase() !in knownCourses }
      .map { PCourse(it.optString("code").uppercase(), it.optString("title"), it.optString("programme")) }

    val assignments = json.objects("assignments").map { o ->
      val name = o.optString("lecturer")
      PAssignment(o.optString("course").uppercase(), name, (match(name, ctx.lecturers) as? LecturerMatch.Found)?.ref?.id)
    }
    val lecturerOfCourse = assignments.filter { it.lecturerId != null }.associate { it.course to it }

    var next = 1
    val sessions = json.objects("sessions").map { o ->
      val course = o.optString("course").uppercase()
      val name = o.optString("lecturer")
      val byName = (match(name, ctx.lecturers) as? LecturerMatch.Found)?.ref
      val viaAssignment = lecturerOfCourse[course]
      PSession(
        id = next++, course = course,
        lecturerName = byName?.name ?: name.ifBlank { viaAssignment?.lecturerName.orEmpty() },
        lecturerId = byName?.id ?: if (name.isBlank()) viaAssignment?.lecturerId else null,
        day = o.optInt("day"), start = o.optString("start"), end = o.optString("end"),
        venue = o.optString("venue"), kind = o.optString("kind").ifBlank { "lecture" }
      )
    }
    val base = TimetableProposal(programmes, courses, assignments, sessions, json.strings("rooms"), json.strings("warnings"))
    val checked = validate(base, ctx)
    return checked.copy(sessions = checked.sessions.map { it.copy(include = !it.blocked) })
  }

  private fun overlaps(aStart: String, aEnd: String, bStart: String, bEnd: String): Boolean =
    aStart < bEnd && bStart < aEnd

  fun validate(p: TimetableProposal, ctx: CatalogContext): TimetableProposal {
    val addedCourses = p.courses.filter { it.include }.map { it.code }.toSet()
    val known = ctx.courses.map { it.uppercase() }.toSet() + addedCourses
    val live = p.sessions.filter { it.include }
    val sessions = p.sessions.map { s ->
      val issues = mutableListOf<Issue>()
      if (s.course !in known) issues += Issue("${s.course} is not in your catalog and is not in the list of courses to add.", true)
      if (s.lecturerId == null) {
        issues += Issue(
          if (s.lecturerName.isBlank()) "No lecturer given. Choose one." else "No lecturer matches \"${s.lecturerName}\". Choose one.",
          true
        )
      }
      if (s.start >= s.end) issues += Issue("It must end after it starts.", true)
      if (s.day !in 1..6) issues += Issue("Day must be Monday to Saturday.", true)
      if (s.venue.isBlank()) issues += Issue("Add a venue.", true)
      if (issues.none { it.blocking && (it.text.startsWith("It must") || it.text.startsWith("Day")) }) {
        ctx.sessions.firstOrNull { it.day == s.day && it.venue.equals(s.venue, true) && overlaps(s.start, s.end, it.start, it.end) }?.let {
          issues += Issue("${s.venue} is already used then by ${it.course} (${it.start}-${it.end}).", true)
        }
        s.lecturerId?.let { lid ->
          ctx.sessions.firstOrNull { it.day == s.day && it.lecturerId == lid && overlaps(s.start, s.end, it.start, it.end) }?.let {
            issues += Issue("That lecturer already teaches ${it.course} then (${it.start}-${it.end}).", true)
          }
        }
        live.firstOrNull { o -> o.id != s.id && o.day == s.day && o.venue.equals(s.venue, true) && overlaps(s.start, s.end, o.start, o.end) }?.let {
          issues += Issue("${s.venue} clashes with ${it.course} in this list (${it.start}-${it.end}).", true)
        }
        if (s.lecturerId != null) {
          live.firstOrNull { o -> o.id != s.id && o.day == s.day && o.lecturerId == s.lecturerId && overlaps(s.start, s.end, o.start, o.end) }?.let {
            issues += Issue("That lecturer also has ${it.course} in this list then (${it.start}-${it.end}).", true)
          }
        }
      }
      s.copy(issues = issues)
    }
    return p.copy(sessions = sessions)
  }
}

object TimetableText {
  const val MAX_CHARS = 60_000

  fun fromSheets(sheets: List<SheetData>): String = buildString {
    for (sheet in sheets) {
      if (sheets.size > 1) append("## Sheet: ").append(sheet.name).append('\n')
      for (row in sheet.rows) append(row.joinToString(" | ") { it.replace('\n', ' ') }.trimEnd(' ', '|')).append('\n')
      append('\n')
    }
  }.take(MAX_CHARS)

  fun fromBlocks(blocks: List<DocBlock>): String = buildString {
    for (b in blocks) when (b) {
      is DocBlock.Heading -> append("\n# ").append(b.text).append('\n')
      is DocBlock.Paragraph -> append(b.text).append('\n')
      is DocBlock.ListItem -> append("- ").append(b.text).append('\n')
      is DocBlock.Table -> {
        for (row in b.rows) append(row.joinToString(" | ") { it.replace('\n', ' ') }).append('\n')
        append('\n')
      }
    }
  }.take(MAX_CHARS)
}
