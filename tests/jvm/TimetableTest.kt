import com.willykez.campconnect.data.docs.*
import org.json.JSONObject

var pass = 0; var fail = 0
fun check(n: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL $n") } }
fun found(m: LecturerMatch) = (m as? LecturerMatch.Found)?.ref?.id

fun main() {
  val lecs = listOf(LecturerRef("l1", "John Mwita"), LecturerRef("l2", "Mary Kimaro"), LecturerRef("l3", "Asha Juma"))
  // ---- name matching
  check("title stripped", found(TimetableLogic.match("Dr. John Mwita", lecs)) == "l1")
  check("order swapped", found(TimetableLogic.match("Mwita John", lecs)) == "l1")
  check("initial + surname", found(TimetableLogic.match("J. Mwita", lecs)) == "l1")
  check("surname only, unique", found(TimetableLogic.match("Kimaro", lecs)) == "l2")
  check("Swahili honorific", found(TimetableLogic.match("Mwl. Kimaro", lecs)) == "l2")
  check("Prof + case", found(TimetableLogic.match("PROF ASHA JUMA", lecs)) == "l3")
  check("unknown person -> none", TimetableLogic.match("Peter Smith", lecs) is LecturerMatch.None)
  check("lone initial never guesses", TimetableLogic.match("J", lecs) is LecturerMatch.None)
  check("blank / titles only -> none", TimetableLogic.match("", lecs) is LecturerMatch.None && TimetableLogic.match("Dr.", lecs) is LecturerMatch.None)
  val twins = lecs + LecturerRef("l4", "James Mwita")
  check("two Mwitas + initial J -> ambiguous, not a guess", TimetableLogic.match("J. Mwita", twins) is LecturerMatch.Ambiguous)
  check("two Mwitas + surname only -> ambiguous", TimetableLogic.match("Mwita", twins) is LecturerMatch.Ambiguous)
  check("full name still exact among twins", found(TimetableLogic.match("James Mwita", twins)) == "l4")
  check("different first name does not match", TimetableLogic.match("Peter Mwita", lecs).let { it is LecturerMatch.None })

  // ---- proposal parsing + validation
  val ctx = CatalogContext(
    programmes = setOf("BSc Computer Science"), courses = setOf("CS210", "CS220"), lecturers = lecs, rooms = setOf("LH1"),
    sessions = listOf(SessionRef("CS220", "l2", 1, "09:00", "11:00", "LH1"))
  )
  val json = JSONObject("""{
    "programmes":[{"name":"BSc Computer Science"},{"name":"BSc IT"}],
    "courses":[{"code":"CS210","title":"Databases","programme":""},{"code":"is202","title":"Systems","programme":"BSc IT"}],
    "assignments":[{"course":"IS202","lecturer":"Dr J. Mwita"},{"course":"IS202","lecturer":"Nobody Known"}],
    "sessions":[
      {"course":"IS202","lecturer":"Dr J. Mwita","day":1,"start":"08:00","end":"10:00","venue":"LH2","kind":"lecture"},
      {"course":"CS210","lecturer":"Mary Kimaro","day":1,"start":"10:00","end":"12:00","venue":"lh1"},
      {"course":"CS210","lecturer":"Peter Smith","day":2,"start":"08:00","end":"09:00","venue":"LH4"},
      {"course":"UNK999","lecturer":"Asha Juma","day":2,"start":"10:00","end":"11:00","venue":"LH5"},
      {"course":"IS202","lecturer":"Dr J. Mwita","day":1,"start":"09:00","end":"10:30","venue":"LH3"},
      {"course":"IS202","lecturer":"","day":3,"start":"08:00","end":"10:00","venue":"LH2"},
      {"course":"CS210","lecturer":"Asha Juma","day":4,"start":"11:00","end":"10:00","venue":"LH6"},
      {"course":"CS210","lecturer":"Asha Juma","day":5,"start":"08:00","end":"09:00","venue":"LH6"}
    ],
    "rooms":["LH2","Hall"],"warnings":["Page 2 was blurry"]}""")
  val p = TimetableLogic.parse(json, ctx)
  check("known programme filtered, new one kept", p.programmes.map { it.name } == listOf("BSc IT"))
  check("known course filtered, new course uppercased", p.courses.map { it.code } == listOf("IS202") && p.courses[0].programme == "BSc IT")
  check("assignment matched by name, unknown lecturer left unresolved", p.assignments[0].lecturerId == "l1" && p.assignments[1].lecturerId == null)
  check("assignments to apply skip the unresolved", p.assignmentsToApply.size == 1)
  val s = p.sessions
  check("a session that clashes with another in the list starts excluded", s[0].blocked && !s[0].include)
  check("room clash with EXISTING timetable (case-insensitive)", s[1].blocked && s[1].issues.any { it.text.contains("already used") } && !s[1].include)
  check("unmatched lecturer -> blocked, asks to choose", s[2].blocked && s[2].lecturerId == null && s[2].issues.any { it.text.contains("Peter Smith") })
  check("unknown course -> blocked", s[3].blocked && s[3].issues.any { it.text.contains("UNK999") })
  check("lecturer double-booked INSIDE the list flags both", s[0].issues.any { it.text.contains("also has") } && s[4].issues.any { it.text.contains("also has") })
  check("lecturer inferred from the course assignment when blank", s[5].lecturerId == "l1" && !s[5].blocked && s[5].include)
  check("end before start -> blocked, no clash noise", s[6].blocked && s[6].issues.size == 1 && s[6].issues[0].text.contains("end after"))
  check("independent clean session applies", s[7].willApply)
  // the administrator removes one of the clashing pair, re-validates: the other becomes clean
  val edited = p.copy(sessions = p.sessions.map { if (it.id == s[4].id) it.copy(include = false) else if (it.id == s[0].id) it.copy(include = true) else it })
  val again = TimetableLogic.validate(edited, ctx)
  check("after dropping one of the pair the other is clean", again.sessions[0].issues.isEmpty() && again.sessions[0].willApply)
  // editing a venue fixes an existing-timetable clash
  val fixed = TimetableLogic.validate(again.copy(sessions = again.sessions.map { if (it.id == s[1].id) it.copy(venue = "LH9", include = true) else it }), ctx)
  val fixedBoth = TimetableLogic.validate(fixed.copy(sessions = fixed.sessions.map { if (it.id == s[1].id) it.copy(start = "11:00", end = "12:30") else it }), ctx)
  check("moving the time as well makes it fully clean", fixedBoth.sessions[1].issues.isEmpty() && fixedBoth.sessions[1].willApply)
  check("changing the venue clears the ROOM clash but the lecturer clash with CS220 remains", fixed.sessions[1].issues.none { it.text.contains("already used") } && fixed.sessions[1].issues.any { it.text.contains("already teaches CS220") })
  // choosing a lecturer clears the unresolved-lecturer block
  val chosen = TimetableLogic.validate(fixed.copy(sessions = fixed.sessions.map { if (it.id == s[2].id) it.copy(lecturerId = "l3", lecturerName = "Asha Juma", include = true) else it }), ctx)
  check("choosing a lecturer clears that block (and finds none new)", chosen.sessions[2].issues.isEmpty())
  // excluding a course removes the support for its sessions
  val noCourse = TimetableLogic.validate(chosen.copy(courses = chosen.courses.map { it.copy(include = false) }), ctx)
  check("un-ticking a new course blocks the sessions that need it", noCourse.sessions[0].issues.any { it.text.contains("IS202") })
  check("itemsToApply counts only what will happen", chosen.itemsToApply == chosen.programmesToApply.size + chosen.coursesToApply.size + chosen.assignmentsToApply.size + chosen.sessionsToApply.size)
  check("warnings and rooms carried over", p.warnings == listOf("Page 2 was blurry") && p.rooms == listOf("LH2", "Hall"))
  check("empty proposal", TimetableLogic.parse(JSONObject("{}"), ctx).isEmpty)

  // ---- request context never contains ids
  val cj = TimetableLogic.contextJson(ctx).toString()
  check("context has names, not ids", cj.contains("John Mwita") && !cj.contains("l1\"") && cj.contains("CS210") && cj.contains("BSc Computer Science"))

  // ---- text for the model
  val sheets = listOf(SheetData("Mon", listOf(listOf("Time", "Course", "Venue", ""), listOf("08:00", "CS210", "LH1", ""))), SheetData("Tue", listOf(listOf("09:00", "IS202", "LH2"))))
  val t = TimetableText.fromSheets(sheets)
  check("sheets -> labelled pipe rows, trailing empties trimmed", t.contains("## Sheet: Mon") && t.contains("Time | Course | Venue\n") && t.contains("08:00 | CS210 | LH1") && t.contains("## Sheet: Tue"))
  check("single sheet has no label", !TimetableText.fromSheets(sheets.take(1)).contains("## Sheet"))
  val b = TimetableText.fromBlocks(listOf(DocBlock.Heading(1, "Year 2"), DocBlock.Paragraph("Semester One"), DocBlock.ListItem("Bring ID", 0), DocBlock.Table(listOf(listOf("Day", "Venue"), listOf("Mon", "LH1\nBlock A")))))
  check("blocks -> headings, bullets, flattened table cells", b.contains("# Year 2") && b.contains("- Bring ID") && b.contains("Day | Venue") && b.contains("Mon | LH1 Block A"))
  check("text is capped", TimetableText.fromSheets(listOf(SheetData("x", List(5000) { listOf("a".repeat(50)) }))).length <= TimetableText.MAX_CHARS)

  println("TimetableLogic: $pass passed, $fail failed"); System.exit(if (fail == 0) 0 else 1)
}
