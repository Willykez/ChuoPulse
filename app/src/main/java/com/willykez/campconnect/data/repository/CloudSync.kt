package com.willykez.campconnect.data.repository

import android.net.Uri
import android.webkit.MimeTypeMap
import com.willykez.campconnect.data.model.*
import com.willykez.campconnect.data.supabase.Backend
import com.willykez.campconnect.util.DeadlineTime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A file that lives in cloud storage and is not on this phone yet. */
data class FileRef(val kind: String, val itemId: String, val bucket: String, val path: String, val size: Long)

/** A student a lecturer or administrator may make a class representative. */
data class ClassMember(val id: String, val name: String, val number: String, val programme: String, val isRep: Boolean)

/** Everything one person is allowed to see, as the server returned it. */
data class CloudSnapshot(
  val courses: Map<String, String>,                 // CS210 -> title
  val programmes: Map<String, Set<String>>,         // programme name -> course codes
  val rooms: List<String>,
  val enrolled: Set<String>,
  val teaches: Set<String>,
  val isClassRep: Boolean,
  val sessions: List<ClassSession>,
  val announcements: List<Announcement>,
  val materials: List<StudyMaterial>,
  val deadlines: List<DeadlineItem>,
  val submissions: List<StudentSubmission>,
  val notifications: List<NotificationItem>,
  val reads: Set<String>,
  val done: Set<String>,
  val quizzes: List<Quiz>,
  val attempts: List<QuizAttempt>,
  val files: List<FileRef>,
  /** course -> lecturers (id to name), for a class representative scheduling a class. */
  val courseLecturers: Map<String, List<Pair<String, String>>>,
  val classMembers: List<ClassMember>
)

/** Opened or downloaded copies of cloud files, kept on the phone so documents also open offline. */
object CloudFiles {
  private fun dir(): File? = Backend.appContext?.let { File(it.filesDir, "cloud").apply { mkdirs() } }

  private fun localName(bucket: String, path: String) = bucket + "__" + path.replace('/', '_').replace(':', '_')

  fun cachedUri(bucket: String, path: String): String? {
    val f = dir()?.let { File(it, localName(bucket, path)) } ?: return null
    return if (f.exists() && f.length() > 0) Uri.fromFile(f).toString() else null
  }

  fun store(bucket: String, path: String, bytes: ByteArray): String? {
    val f = dir()?.let { File(it, localName(bucket, path)) } ?: return null
    return try { f.writeBytes(bytes); Uri.fromFile(f).toString() } catch (e: Exception) { null }
  }

  /** Reads a picked file (content:// or file://). */
  fun read(uriString: String?): ByteArray? {
    val ctx = Backend.appContext ?: return null
    val uri = uriString?.let { Uri.parse(it) } ?: return null
    return try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (e: Exception) { null }
  }

  fun mimeOf(fileName: String, fallback: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: fallback
  }

  /** Clears everything kept for this phone (sign-out). */
  fun clear() { dir()?.listFiles()?.forEach { it.delete() } }
}

/** Reads the server into the app's own models, and maps app values back to what the server stores. */
class CloudSync(private val remote: RemoteAdmin) {

  companion object {
    fun short(code: String) = code.substringAfter(':')

    private val eat: TimeZone = TimeZone.getTimeZone("Africa/Dar_es_Salaam")

    fun millis(iso: String?): Long? =
      if (iso.isNullOrBlank() || iso == "null") null else runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

    fun stamp(iso: String?): String {
      val m = millis(iso) ?: return ""
      return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).apply { timeZone = eat }.format(Date(m)) + " EAT"
    }

    /** The server wants an ISO moment; the app keeps deadline text such as "Friday, 24 Oct 2026 • 17:00 EAT". */
    fun isoOf(millis: Long): String =
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    fun sizeText(bytes: Long): String = when {
      bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
      bytes >= 1024 -> "${bytes / 1024} KB"
      else -> "$bytes B"
    }

    fun approval(v: String?) = when (v) {
      "pending_review", "draft" -> ContentApprovalStatus.PENDING_REVIEW
      "rejected" -> ContentApprovalStatus.DISAPPROVED
      else -> ContentApprovalStatus.APPROVED
    }

    fun approvalText(s: ContentApprovalStatus) = when (s) {
      ContentApprovalStatus.APPROVED -> "approved"
      ContentApprovalStatus.PENDING_REVIEW -> "pending_review"
      ContentApprovalStatus.DISAPPROVED -> "rejected"
    }

    /** Plain-words reason for a server refusal. */
    fun friendly(r: RemoteResult): String {
      val t = (r.detail + " " + r.body).lowercase()
      return when {
        t.contains("limit_reached") -> "You reached today's limit for this. Try again tomorrow."
        t.contains("file_too_large") -> "That file is too large."
        t.contains("venue_busy") -> "That room is already used at that time."
        t.contains("reason_required") -> "Please give a reason for the change."
        t.contains("urgent_course_only") || t.contains("urgent_requires_registry") -> "Urgent is only for the registry or a lecturer's own course."
        t.contains("responsible_not_for_course") -> "That lecturer does not teach this course."
        t.contains("file_not_uploaded") -> "The file did not upload. Check your connection and try again."
        t.contains("lecturer_has_classes") -> "That lecturer still has live classes in this course."
        r.error == RemoteError.NOT_ALLOWED -> "You are not allowed to do that."
        r.error == RemoteError.NETWORK -> "No connection. Your change was not saved."
        else -> "The server could not save that. Please try again."
      }
    }
  }

  private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }

  private suspend fun rows(table: String, qs: String): JSONArray = remote.query(table, qs) ?: JSONArray()

  private inline fun JSONArray.each(block: (JSONObject) -> Unit) { for (i in 0 until length()) block(getJSONObject(i)) }

  /** Reads everything for [me]. Null when the catalogue could not be read (offline). */
  suspend fun pull(me: UserProfile, uniCode: String): CloudSnapshot? {
    // --- campus catalogue ---
    val courseRows = remote.query("courses", "select=code,short_code,title&order=short_code&limit=2000") ?: return null
    val courses = mutableMapOf<String, String>()
    courseRows.each { courses[it.optString("short_code")] = it.optString("title") }
    val progNames = mutableMapOf<String, String>()
    rows("programmes", "select=id,name&order=name").each { progNames[it.optString("id")] = it.optString("name") }
    val programmes = mutableMapOf<String, MutableSet<String>>()
    progNames.values.forEach { programmes[it] = mutableSetOf() }
    rows("programme_courses", "select=programme_id,course_code&limit=5000").each {
      val n = progNames[it.optString("programme_id")] ?: return@each
      programmes.getOrPut(n) { mutableSetOf() }.add(short(it.optString("course_code")))
    }
    val rooms = mutableListOf<String>()
    rows("rooms", "select=name&order=name").each { rooms.add(it.optString("name")) }

    // --- people (names that are public inside the campus) and my own links ---
    class Person(val name: String, val role: String, val rep: Boolean)
    val people = mutableMapOf<String, Person>()
    rows("staff_directory", "select=id,full_name,role,is_class_rep").each {
      people[it.optString("id")] = Person(it.optString("full_name"), it.optString("role"), it.optBoolean("is_class_rep"))
    }
    val enrolled = mutableSetOf<String>()
    rows("enrollments", "select=course_code&student_id=eq.${me.id}").each { enrolled.add(short(it.optString("course_code"))) }
    val teaches = mutableSetOf<String>()
    rows("teaching_assignments", "select=course_code&lecturer_id=eq.${me.id}").each { teaches.add(short(it.optString("course_code"))) }
    val rep = rows("profiles", "select=is_class_rep&id=eq.${me.id}").let { it.length() > 0 && it.getJSONObject(0).optBoolean("is_class_rep") }

    fun programmeOf(course: String): String = programmes.entries.firstOrNull { course in it.value }?.key ?: ""
    fun titleOf(course: String): String = courses[course] ?: course
    fun nameOf(id: String): String = people[id]?.name ?: if (id == me.id) me.fullName else "Staff"

    // --- timetable ---
    val sessions = mutableListOf<ClassSession>()
    rows("class_sessions", "select=*&order=day_of_week,start_time&limit=2000").each {
      val c = short(it.optString("course_code"))
      sessions.add(ClassSession(
        id = it.optString("id"), courseCode = c, courseTitle = titleOf(c),
        lecturerId = it.optString("lecturer_id"), lecturerName = nameOf(it.optString("lecturer_id")),
        venue = it.optString("venue"), originalVenue = it.str("original_venue"),
        dayOfWeek = it.optInt("day_of_week", 1),
        startTime = it.optString("start_time").take(5), endTime = it.optString("end_time").take(5),
        status = when (it.optString("status")) { "changed" -> SessionStatus.CHANGED; "cancelled" -> SessionStatus.CANCELLED; else -> SessionStatus.SCHEDULED },
        changeReason = it.str("change_reason"), programme = programmeOf(c), cohort = "",
        approvalStatus = approval(it.optString("approval"))
      ))
    }

    // --- announcements ---
    val reads = mutableSetOf<String>()
    rows("announcement_reads", "select=announcement_id&user_id=eq.${me.id}&limit=2000").each { reads.add(it.optString("announcement_id")) }
    val files = mutableListOf<FileRef>()
    val announcements = mutableListOf<Announcement>()
    val annRows = rows("announcements", "select=*&order=created_at.desc&limit=300")
    annRows.each {
      val type = when (it.optString("audience")) { "programme" -> AudienceType.PROGRAMME; "course" -> AudienceType.COURSE; else -> AudienceType.CAMPUS }
      val progName = it.str("programme_id")?.let { id -> progNames[id] }.orEmpty()
      val course = it.str("course_code")?.let { c -> short(c) }.orEmpty()
      val authorId = it.optString("author_id")
      val author = people[authorId]
      val path = it.str("attachment_path")
      val resp = it.str("responsible_lecturer_id")
      if (path != null && CloudFiles.cachedUri("announcements", path) == null) files.add(FileRef("ann", it.optString("id"), "announcements", path, it.optLong("attachment_size")))
      announcements.add(Announcement(
        id = it.optString("id"), authorId = authorId, authorName = nameOf(authorId),
        authorRole = when { author?.role == "admin" -> "Academic Registry"; author?.role == "lecturer" -> "Lecturer"; author?.rep == true -> "Class representative"; else -> "Staff" },
        title = it.optString("title"), body = it.optString("body"), audienceType = type,
        audienceName = when (type) { AudienceType.CAMPUS -> "Whole campus"; AudienceType.PROGRAMME -> progName; AudienceType.COURSE -> if (course in courses) "$course - ${titleOf(course)}" else course },
        audienceCode = when (type) { AudienceType.CAMPUS -> ""; AudienceType.PROGRAMME -> progName; AudienceType.COURSE -> course },
        responsibleLecturerId = resp, responsibleLecturerName = resp?.let { r -> nameOf(r) },
        publishedAt = stamp(it.optString("created_at")),
        priority = when (it.optString("priority")) { "urgent" -> AnnouncementPriority.URGENT; "academic" -> AnnouncementPriority.ACADEMIC; else -> AnnouncementPriority.NORMAL },
        isRead = it.optString("id") in reads || authorId == me.id,
        attachment = path?.let { p -> AttachmentFile(
          uriString = CloudFiles.cachedUri("announcements", p), fileName = p.substringAfterLast('/'),
          fileSize = sizeText(it.optLong("attachment_size")), mimeType = CloudFiles.mimeOf(p, "application/pdf")) },
        isRetracted = !it.isNull("retracted_at"), approvalStatus = approval(it.optString("approval"))
      ))
    }
    // Reach numbers for the notices this person wrote (only the author and the registry may ask).
    val mine = announcements.filter { it.authorId == me.id }.take(15)
    val stats = mutableMapOf<String, Pair<Int, Int>>()
    for (a in mine) {
      val r = remote.rpcCall("announcement_stats", JSONObject().put("p_id", a.id))
      if (r.ok) runCatching { JSONArray(r.body).getJSONObject(0).let { o -> stats[a.id] = o.optInt("delivered") to o.optInt("opened") } }
    }
    val announcementsOut = announcements.map { a -> stats[a.id]?.let { (d, o) -> a.copy(totalStudents = d, readCount = o) } ?: a.copy(totalStudents = 0) }

    // --- materials ---
    val materials = mutableListOf<StudyMaterial>()
    rows("study_materials", "select=*&order=created_at.desc&limit=500").each {
      val c = short(it.optString("course_code"))
      val path = it.optString("storage_path")
      if (CloudFiles.cachedUri("materials", path) == null) files.add(FileRef("mat", it.optString("id"), "materials", path, it.optLong("size_bytes")))
      materials.add(StudyMaterial(
        id = it.optString("id"), courseCode = c, courseTitle = titleOf(c), title = it.optString("title"),
        uploaderId = it.optString("uploader_id"), uploadedBy = nameOf(it.optString("uploader_id")),
        uploadedAt = stamp(it.optString("created_at")),
        attachment = AttachmentFile(
          uriString = CloudFiles.cachedUri("materials", path), fileName = it.optString("file_name"),
          fileSize = sizeText(it.optLong("size_bytes")), mimeType = it.optString("mime_type").ifBlank { "application/pdf" }),
        description = it.optString("description"), approvalStatus = approval(it.optString("approval"))
      ))
    }

    // --- assignments and calendar entries (deadlines) ---
    val done = mutableSetOf<String>()
    rows("deadline_done", "select=deadline_id&limit=2000").each { done.add(it.optString("deadline_id")) }
    val submittedBy = mutableMapOf<String, Int>()
    val deadlines = mutableListOf<DeadlineItem>()
    val dlRows = rows("deadlines", "select=*&order=due_at&limit=500")
    dlRows.each {
      val c = it.str("course_code")?.let { x -> short(x) } ?: "ACADEMIC"
      val due = millis(it.optString("due_at")) ?: 0L
      val dueText = DeadlineTime.format(due)
      deadlines.add(DeadlineItem(
        id = it.optString("id"), courseCode = c, courseTitle = if (c == "ACADEMIC") "Academic calendar" else titleOf(c),
        lecturerId = it.optString("lecturer_id"), lecturerName = nameOf(it.optString("lecturer_id")),
        title = it.optString("title"), instructions = it.optString("instructions"), dueDateTime = dueText,
        urgencyText = DeadlineTime.urgency(dueText, "Upcoming"),
        acceptedFormats = it.optString("accepted_formats").ifBlank { "PDF or Images (Max 15 MB)" },
        isCompleted = it.optString("id") in done, approvalStatus = approval(it.optString("approval"))
      ))
    }

    // --- submitted work (a student sees their own; a lecturer the work for their courses) ---
    val names = mutableMapOf<String, Pair<String, String>>()
    val members = mutableMapOf<String, ClassMember>()
    if (me.role != Role.STUDENT) {
      rows("class_list", "select=student_id,full_name,student_number,is_class_rep,programme_id&limit=5000").each {
        val sid = it.optString("student_id")
        names[sid] = it.optString("full_name") to it.optString("student_number")
        members[sid] = ClassMember(sid, it.optString("full_name"), it.optString("student_number"),
          progNames[it.optString("programme_id")].orEmpty(), it.optBoolean("is_class_rep"))
      }
    }
    val courseLecturers = mutableMapOf<String, List<Pair<String, String>>>()
    if (rep) {
      for (c in enrolled) {
        val r = remote.rpcCall("course_lecturer_list", JSONObject().put("p_course", c))
        if (r.ok) runCatching {
          val a = JSONArray(r.body)
          courseLecturers[c] = (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> o.optString("lecturer_id") to o.optString("full_name") } }
        }
      }
    }
    val titleOfDeadline = deadlines.associate { it.id to it.title }
    val submissions = mutableListOf<StudentSubmission>()
    rows("submissions", "select=*&order=submitted_at.desc&limit=1000").each {
      val c = short(it.optString("course_code"))
      val sid = it.optString("student_id")
      val path = it.optString("storage_path")
      if (CloudFiles.cachedUri("submissions", path) == null) files.add(FileRef("sub", it.optString("id"), "submissions", path, it.optLong("size_bytes")))
      val (nm, num) = if (sid == me.id) me.fullName to (me.studentNumber ?: "") else names[sid] ?: ("Student" to "")
      submittedBy[it.optString("deadline_id")] = (submittedBy[it.optString("deadline_id")] ?: 0) + 1
      submissions.add(StudentSubmission(
        id = it.optString("id"), deadlineId = it.optString("deadline_id"), courseCode = c,
        assignmentTitle = titleOfDeadline[it.optString("deadline_id")] ?: "Assignment",
        studentId = sid, studentName = nm, studentNumber = num, notes = it.optString("notes"),
        attachment = AttachmentFile(
          uriString = CloudFiles.cachedUri("submissions", path), fileName = it.optString("file_name"),
          fileSize = sizeText(it.optLong("size_bytes")), mimeType = it.optString("mime_type").ifBlank { "application/pdf" }),
        submittedAt = stamp(it.optString("submitted_at")), grade = it.str("grade"), feedback = it.str("feedback"),
        approvalStatus = if (it.str("grade") != null) ContentApprovalStatus.APPROVED else ContentApprovalStatus.PENDING_REVIEW
      ))
    }
    val deadlinesOut = deadlines.map { d ->
      val total = if (d.courseCode == "ACADEMIC") 0 else 0
      d.copy(submittedCount = if (me.role == Role.STUDENT) 0 else submittedBy[d.id] ?: 0, totalStudents = total)
    }

    // --- notifications ---
    val notifications = mutableListOf<NotificationItem>()
    rows("notifications", "select=*&order=created_at.desc&limit=150").each {
      notifications.add(NotificationItem(
        id = it.optString("id"), userId = it.optString("user_id"), title = it.optString("title"), message = it.optString("message"),
        type = when (it.optString("type")) {
          "timetable_change" -> NotificationType.TIMETABLE_CHANGE; "deadline_alert" -> NotificationType.DEADLINE_ALERT
          "grade_posted" -> NotificationType.GRADE_POSTED; "submission_received" -> NotificationType.SUBMISSION_RECEIVED
          "approval" -> NotificationType.APPROVAL; else -> NotificationType.ANNOUNCEMENT },
        timestamp = stamp(it.optString("created_at")), isRead = it.optBoolean("is_read")
      ))
    }

    // --- quizzes (answers only for people who may see them: the server decides) ---
    val quizzes = mutableListOf<Quiz>()
    val attempts = mutableListOf<QuizAttempt>()
    val quizRows = rows("quizzes", "select=*&order=opens_at.desc&limit=200")
    val qIds = mutableListOf<String>()
    quizRows.each { qIds.add(it.optString("id")) }
    if (qIds.isNotEmpty()) {
      val inList = qIds.joinToString(",")
      val qs = rows("quiz_questions", "select=*&quiz_id=in.($inList)&order=position")
      val keys = mutableMapOf<String, JSONObject>()
      rows("quiz_answer_keys", "select=*&limit=5000").each { keys[it.optString("question_id")] = it }
      val byQuiz = mutableMapOf<String, MutableList<QuizQuestion>>()
      qs.each { q ->
        val kind = if (q.optString("kind") == "multiple_choice") QuestionType.MULTIPLE_CHOICE else QuestionType.SHORT_ANSWER
        val opts = mutableListOf<String>()
        q.optJSONArray("options")?.let { a -> for (i in 0 until a.length()) opts.add(a.optString(i)) }
        val key = keys[q.optString("id")]
        byQuiz.getOrPut(q.optString("quiz_id")) { mutableListOf() }.add(QuizQuestion(
          id = q.optString("id"), type = kind, text = q.optString("body"), options = opts,
          correctOption = key?.let { k -> if (k.isNull("correct_option")) null else k.optInt("correct_option") },
          modelAnswer = key?.optString("model_answer").orEmpty().let { if (it == "null") "" else it },
          points = q.optInt("points", 1)))
      }
      quizRows.each {
        val c = short(it.optString("course_code"))
        quizzes.add(Quiz(
          id = it.optString("id"), courseCode = c, courseTitle = titleOf(c), lecturerId = it.optString("created_by"),
          lecturerName = nameOf(it.optString("created_by")), title = it.optString("title"),
          instructions = it.optString("instructions"), durationMinutes = it.optInt("duration_minutes", 10),
          opensAt = millis(it.optString("opens_at")) ?: 0L, closesAt = millis(it.optString("closes_at")) ?: 0L,
          questions = byQuiz[it.optString("id")].orEmpty()))
      }
      val atRows = rows("quiz_attempts", "select=*&quiz_id=in.($inList)&order=started_at.desc&limit=2000")
      val ansByAttempt = mutableMapOf<String, MutableMap<String, String>>()
      val awardedBy = mutableMapOf<String, MutableMap<String, Int>>()
      rows("quiz_answers", "select=*&limit=20000").each {
        val at = it.optString("attempt_id")
        val qid = it.optString("question_id")
        val ans = it.optString("answer")
        ansByAttempt.getOrPut(at) { mutableMapOf() }[qid] = ans
        if (!it.isNull("awarded_points")) awardedBy.getOrPut(at) { mutableMapOf() }[qid] = it.optInt("awarded_points")
      }
      atRows.each {
        val sid = it.optString("student_id")
        val (nm, num) = if (sid == me.id) me.fullName to (me.studentNumber ?: "") else names[sid] ?: ("Student" to "")
        val st = when (it.optString("status")) { "submitted" -> AttemptStatus.SUBMITTED; "auto_submitted" -> AttemptStatus.AUTO_SUBMITTED; else -> AttemptStatus.IN_PROGRESS }
        attempts.add(QuizAttempt(
          id = it.optString("id"), quizId = it.optString("quiz_id"), studentId = sid, studentName = nm, studentNumber = num,
          startedAt = millis(it.optString("started_at")) ?: 0L, deadlineAt = millis(it.optString("deadline_at")) ?: 0L,
          status = st, answers = ansByAttempt[it.optString("id")].orEmpty(),
          submittedAt = millis(it.optString("submitted_at")), awarded = awardedBy[it.optString("id")].orEmpty(),
          marked = it.optBoolean("marked")))
      }
    }

    return CloudSnapshot(courses, programmes, rooms, enrolled, teaches, rep, sessions, announcementsOut, materials,
      deadlinesOut, submissions, notifications, reads, done, quizzes, attempts, files, courseLecturers,
      members.values.sortedBy { it.name })
  }
}
