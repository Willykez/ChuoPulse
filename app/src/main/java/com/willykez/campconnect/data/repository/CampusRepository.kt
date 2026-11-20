package com.willykez.campconnect.data.repository

import com.willykez.campconnect.data.auth.AuthRepository
import com.willykez.campconnect.data.auth.DeviceSession
import com.willykez.campconnect.data.auth.Session
import com.willykez.campconnect.data.model.AccountStatus
import com.willykez.campconnect.util.DeadlineTime
import com.willykez.campconnect.data.model.StartResult
import com.willykez.campconnect.data.model.QuizScore
import com.willykez.campconnect.data.model.QuizQuestion
import com.willykez.campconnect.data.model.QuizError
import com.willykez.campconnect.data.model.QuizAttempt
import com.willykez.campconnect.data.model.Quiz
import com.willykez.campconnect.data.model.QuestionType
import com.willykez.campconnect.data.model.AttemptStatus
import com.willykez.campconnect.data.model.Announcement
import com.willykez.campconnect.data.model.AnnouncementPriority
import com.willykez.campconnect.data.model.AppLanguage
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.data.model.AudienceType
import com.willykez.campconnect.data.model.AuditLog
import com.willykez.campconnect.data.model.ClassSession
import com.willykez.campconnect.data.model.ContentApprovalStatus
import com.willykez.campconnect.data.model.DeadlineItem
import com.willykez.campconnect.data.model.NotificationItem
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.SessionStatus
import com.willykez.campconnect.data.model.StudentSubmission
import com.willykez.campconnect.data.model.StudyMaterial
import com.willykez.campconnect.data.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.willykez.campconnect.data.supabase.Backend
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AudienceOption(
  val label: String,
  val type: AudienceType,
  val name: String,
  val code: String
)

enum class PostError {
  NOT_ALLOWED,
  CAMPUS_REGISTRY_ONLY,
  AUDIENCE_NOT_YOURS,
  URGENT_NOT_ALLOWED,
  URGENT_LIMIT_REACHED,
  RESPONSIBLE_NOT_FOR_COURSE
}

enum class AssignLecturersError { NOT_ALLOWED, UNKNOWN_COURSE, NOT_A_LECTURER, HAS_CLASSES }

data class AssignLecturersResult(val error: AssignLecturersError? = null, val names: String = "")

enum class SessionError { NOT_ALLOWED, UNKNOWN_COURSE, LECTURER_NOT_FOR_COURSE, TIME_INVALID, ROOM_REQUIRED, ROOM_BUSY, LECTURER_BUSY }

data class RoomOption(val name: String, val busyWith: String?)

enum class PlatformError { NOT_ALLOWED, CAMPUS_EXISTS, DETAILS_MISSING, FAILED }
enum class WipeScope { ANNOUNCEMENTS, TIMETABLE, MATERIALS, WORKS, NOTIFICATIONS, CATALOG, PEOPLE, EVERYTHING }

data class PlatformResult(
  val ok: Boolean,
  val platformError: PlatformError? = null,
  val authError: com.willykez.campconnect.data.auth.AuthError? = null
)

data class CampusSummary(
  val university: com.willykez.campconnect.data.model.University,
  val people: Int,
  val pending: Int,
  val admins: Int,
  val programmes: Int,
  val courses: Int,
  val rooms: Int,
  val sessions: Int,
  val announcements: Int,
  val materials: Int,
  val works: Int
)

class CampusRepository {

  companion object {
    const val SUBMIT_GRACE_MS = 10_000L
    const val DAILY_URGENT_LIMIT = 2
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

  val catalog = CampusCatalog()

  val auth = AuthRepository(catalog)
  val session: StateFlow<Session?> = auth.session
  val devices: StateFlow<Map<String, List<DeviceSession>>> = auth.devices

  private val signedOutPlaceholder = UserProfile(
    id = "none",
    fullName = "",
    email = "",
    role = Role.STUDENT,
    status = AccountStatus.PENDING
  )

  val currentUser: StateFlow<UserProfile> = combine(auth.session, auth.accounts) { s, accounts ->
    if (s == null) signedOutPlaceholder else accounts[s.userId]?.profile ?: signedOutPlaceholder
  }.stateIn(scope, SharingStarted.Eagerly, signedOutPlaceholder)

  private val _appLanguage = MutableStateFlow(AppLanguage.ENGLISH)
  val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

  private val _isOffline = MutableStateFlow(false)
  val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

  private val _lastSyncedAt = MutableStateFlow(System.currentTimeMillis())
  val lastSyncedAt: StateFlow<Long> = _lastSyncedAt.asStateFlow()

  private val _is24HourFormat = MutableStateFlow(true)
  val is24HourFormat: StateFlow<Boolean> = _is24HourFormat.asStateFlow()

  private val _sessions = MutableStateFlow<List<ClassSession>>(emptyList())

  private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())

  private val _materials = MutableStateFlow<List<StudyMaterial>>(emptyList())

  private val _deadlines = MutableStateFlow<List<DeadlineItem>>(emptyList())

  private val _submissions = MutableStateFlow<List<StudentSubmission>>(emptyList())

  private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())

  private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())

  private val _readReceipts = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

  private val _remindedAnnouncements = MutableStateFlow<Set<String>>(emptySet())
  val remindedAnnouncements: StateFlow<Set<String>> = _remindedAnnouncements.asStateFlow()

  private val _doneTicks = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

  private val _downloads = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

  val sessions: StateFlow<List<ClassSession>> = combine(_sessions, currentUser) { list, user ->
    list.filter { canSeeSession(it, user) }
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  val announcements: StateFlow<List<Announcement>> =
    combine(_announcements, currentUser, _readReceipts) { list, user, receipts ->
      list.filter { canSeeAnnouncement(it, user) }.map { a ->
        val readers = receipts[a.id].orEmpty()
        a.copy(
          isRead = user.id in readers || a.authorId == user.id,
          readCount = a.readCount + readers.size
        )
      }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  val materials: StateFlow<List<StudyMaterial>> =
    combine(_materials, currentUser, _downloads) { list, user, downloads ->
      val mine = downloads[user.id].orEmpty()
      list.filter { canSeeMaterial(it, user) }.map { it.copy(isDownloaded = it.id in mine) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  val deadlines: StateFlow<List<DeadlineItem>> =
    combine(_deadlines, currentUser, _doneTicks) { list, user, ticks ->
      val mine = if (user.role == Role.STUDENT) ticks[user.id].orEmpty() else emptySet()
      list.filter { canSeeDeadline(it, user) }.map { it.copy(isCompleted = it.id in mine) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  val submissions: StateFlow<List<StudentSubmission>> = combine(_submissions, currentUser) { list, user ->
    list.filter { canSeeSubmission(it, user) }
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  val notifications: StateFlow<List<NotificationItem>> = combine(_notifications, currentUser) { list, user ->
    list.filter { it.userId == user.id }
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  val auditLogs: StateFlow<List<AuditLog>> = combine(_auditLogs, currentUser) { list, user ->
    if (user.role == Role.ADMIN) list.filter { it.universityId == user.universityId } else emptyList()
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  val pendingUsers: StateFlow<List<UserProfile>> = combine(auth.accounts, currentUser) { accounts, user ->
      accounts.values
        .filter { it.profile.status == AccountStatus.PENDING && it.emailVerified && it.profile.universityId == user.universityId }
        .map { it.profile }
    }
    .stateIn(scope, SharingStarted.Eagerly, emptyList())

  private fun baseCourse(code: String): String = code.substringBefore('-')

  private fun isActive(user: UserProfile) = user.status == AccountStatus.APPROVED

  private fun universityOf(userId: String): String =
    auth.accounts.value[userId]?.profile?.universityId ?: ""

  private fun sameCampus(ownerId: String, user: UserProfile): Boolean {
    val owner = auth.accounts.value[ownerId]?.profile?.universityId ?: return true
    return owner == user.universityId
  }

  private fun canSeeSession(s: ClassSession, user: UserProfile): Boolean {
    if (!isActive(user)) return false
    if (!sameCampus(s.lecturerId, user)) return false
    if (user.role == Role.ADMIN) return true
    if (s.lecturerId == user.id) return true
    if (s.approvalStatus != ContentApprovalStatus.APPROVED) return false
    return when (user.role) {
      Role.STUDENT -> baseCourse(s.courseCode) in user.enrolledCourses
      Role.LECTURER -> baseCourse(s.courseCode) in user.teachesCourses
      Role.ADMIN -> true
    }
  }

  private fun canSeeAnnouncement(a: Announcement, user: UserProfile): Boolean {
    if (!isActive(user)) return false
    if (!sameCampus(a.authorId, user)) return false
    if (user.role == Role.ADMIN) return true
    if (a.authorId == user.id) return true
    if (a.approvalStatus != ContentApprovalStatus.APPROVED || a.isRetracted) return false
    return when (a.audienceType) {
      AudienceType.CAMPUS -> true
      AudienceType.PROGRAMME -> when (user.role) {
        Role.STUDENT -> user.programme == a.audienceCode
        Role.LECTURER -> user.teachesCourses.any { it in catalog.catalogFor(user.universityId)[a.audienceCode].orEmpty() }
        Role.ADMIN -> true
      }
      AudienceType.COURSE -> when (user.role) {
        Role.STUDENT -> a.audienceCode in user.enrolledCourses
        Role.LECTURER -> a.audienceCode in user.teachesCourses
        Role.ADMIN -> true
      }
    }
  }

  private fun canSeeMaterial(m: StudyMaterial, user: UserProfile): Boolean {
    if (!isActive(user)) return false
    if (!sameCampus(m.uploaderId, user)) return false
    if (user.role == Role.ADMIN) return true
    if (m.uploaderId == user.id) return true
    if (m.approvalStatus != ContentApprovalStatus.APPROVED) return false
    val code = baseCourse(m.courseCode)
    return when (user.role) {
      Role.STUDENT -> code in user.enrolledCourses
      Role.LECTURER -> code in user.teachesCourses
      Role.ADMIN -> true
    }
  }

  private fun canSeeDeadline(d: DeadlineItem, user: UserProfile): Boolean {
    if (!isActive(user)) return false
    if (!sameCampus(d.lecturerId, user)) return false
    if (user.role == Role.ADMIN) return true
    if (d.lecturerId == user.id) return true
    if (d.approvalStatus != ContentApprovalStatus.APPROVED) return false
    return when (user.role) {
      Role.STUDENT -> d.courseCode == "ACADEMIC" || baseCourse(d.courseCode) in user.enrolledCourses
      Role.LECTURER -> baseCourse(d.courseCode) in user.teachesCourses
      Role.ADMIN -> true
    }
  }

  private fun canSeeSubmission(s: StudentSubmission, user: UserProfile): Boolean {
    if (!isActive(user)) return false
    if (!sameCampus(s.studentId, user)) return false
    return when (user.role) {
      Role.STUDENT -> s.studentId == user.id
      Role.LECTURER -> baseCourse(s.courseCode) in user.teachesCourses
      Role.ADMIN -> true
    }
  }

  private fun activeProfiles(): List<UserProfile> =
    auth.accounts.value.values.map { it.profile }
      .filter { it.status == AccountStatus.APPROVED && it.universityId == currentUser.value.universityId }

  private fun courseStudentIds(courseCode: String): List<String> =
    activeProfiles().filter { it.role == Role.STUDENT && baseCourse(courseCode) in it.enrolledCourses }.map { it.id }

  private fun audienceRecipientIds(type: AudienceType, code: String): List<String> = when (type) {
    AudienceType.CAMPUS -> activeProfiles().map { it.id }
    AudienceType.PROGRAMME -> activeProfiles().filter { it.role == Role.STUDENT && it.programme == code }.map { it.id }
    AudienceType.COURSE -> courseStudentIds(code)
  }

  init {
    scope.launch {
      auth.session.collect { s -> if (s != null) _lastSyncedAt.value = System.currentTimeMillis() }
    }
  }

  fun canEditOrDelete(uploaderOrAuthorId: String): Boolean {
    val user = currentUser.value
    if (!isActive(user)) return false
    return user.role == Role.ADMIN || user.id == uploaderOrAuthorId
  }

  fun canEditSession(s: ClassSession): Boolean {
    val user = currentUser.value
    if (!isActive(user)) return false
    return user.role == Role.ADMIN || user.id == s.lecturerId ||
      (user.role == Role.STUDENT && user.isClassRep && baseCourse(s.courseCode) in user.enrolledCourses)
  }

  fun canApproveOrDisapprove(): Boolean {
    val user = currentUser.value
    return isActive(user) && user.role == Role.ADMIN
  }

  fun audienceOptions(): List<AudienceOption> {
    val user = currentUser.value
    if (!isActive(user)) return emptyList()
    fun courseOption(code: String): AudienceOption {
      val title = catalog.coursesFor(user.universityId)[code]
        ?: _sessions.value.firstOrNull { baseCourse(it.courseCode) == code }?.courseTitle
      return AudienceOption(code, AudienceType.COURSE, if (title != null) "$code - $title" else code, code)
    }
    return when (user.role) {
      Role.ADMIN -> {
        val courses = catalog.coursesFor(user.universityId).keys.sorted().map { courseOption(it) }
        val programmes = catalog.programmesFor(user.universityId).map { AudienceOption(it, AudienceType.PROGRAMME, it, it) }
        listOf(AudienceOption("Whole campus", AudienceType.CAMPUS, "Whole campus", "")) + programmes + courses
      }
      Role.LECTURER -> {
        val courses = user.teachesCourses.sorted().map { courseOption(it) }
        val programmes = catalog.catalogFor(user.universityId)
          .filter { (_, codes) -> codes.any { it in user.teachesCourses } }
          .keys.map { AudienceOption(it, AudienceType.PROGRAMME, it, it) }
        courses + programmes
      }
      Role.STUDENT -> if (!user.isClassRep) emptyList() else {
        val prog = user.programme?.let { listOf(AudienceOption(it, AudienceType.PROGRAMME, it, it)) }.orEmpty()
        user.enrolledCourses.sorted().map { courseOption(it) } + prog
      }
    }
  }

  fun urgentLeftToday(): Int {
    val user = currentUser.value
    return when (user.role) {
      Role.ADMIN -> Int.MAX_VALUE
      Role.LECTURER -> (DAILY_URGENT_LIMIT - urgentSentToday(user.id)).coerceAtLeast(0)
      Role.STUDENT -> 0
    }
  }

  fun canUseUrgentFor(type: AudienceType): Boolean {
    val user = currentUser.value
    return user.role == Role.ADMIN || (user.role == Role.LECTURER && type == AudienceType.COURSE)
  }

  private val urgentCounts = mutableMapOf<String, Int>()

  private fun todayKey(userId: String): String =
    userId + "|" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

  private fun urgentSentToday(userId: String): Int = urgentCounts[todayKey(userId)] ?: 0

  fun setLanguage(language: AppLanguage) {
    _appLanguage.value = language
  }

  fun toggle24HourFormat() {
    _is24HourFormat.value = !_is24HourFormat.value
  }

  fun toggleOfflineSimulation() {
    _isOffline.value = !_isOffline.value
    if (!_isOffline.value) _lastSyncedAt.value = System.currentTimeMillis()
  }

  fun triggerSync() {
    syncNow()
  }

  fun signOut() {
    wipeLocalCopies(currentUser.value.id)
    auth.signOut()
  }

  fun signOutEverywhere() {
    val id = currentUser.value.id
    wipeLocalCopies(id)
    auth.signOutEverywhere(id)
  }

  fun signOutDevice(deviceId: String) {
    val id = currentUser.value.id
    if (deviceId == auth.thisDeviceId) wipeLocalCopies(id)
    auth.signOutDevice(id, deviceId)
  }

  private fun wipeLocalCopies(userId: String) {
    _downloads.value = _downloads.value - userId
    _lastSyncedAt.value = 0L
    _sessions.value = emptyList(); _announcements.value = emptyList(); _materials.value = emptyList()
    _deadlines.value = emptyList(); _submissions.value = emptyList(); _notifications.value = emptyList()
    _readReceipts.value = emptyMap(); _doneTicks.value = emptyMap()
    _quizzes.value = emptyList(); _attempts.value = emptyList(); attemptAlias.clear()
    _classMembers.value = emptyList(); _repLecturers.value = emptyMap()
    CloudFiles.clear()
  }

  fun markAnnouncementRead(id: String) {
    val user = currentUser.value
    if (user.role != Role.STUDENT || !isActive(user)) return
    val readers = _readReceipts.value[id].orEmpty()
    if (user.id in readers) return
    _readReceipts.value = _readReceipts.value + (id to (readers + user.id))
    if (isUuid(id)) push(refresh = false, silent = true) {
      remote.insertRow("announcement_reads", JSONObject().put("announcement_id", id).put("user_id", user.id))
    }
  }

  fun toggleDeadlineCompleted(id: String) {
    val user = currentUser.value
    if (user.role != Role.STUDENT || !isActive(user)) return
    val mine = _doneTicks.value[user.id].orEmpty()
    val nowDone = id !in mine
    val next = if (nowDone) mine + id else mine - id
    _doneTicks.value = _doneTicks.value + (user.id to next)
    if (isUuid(id)) push(refresh = false, silent = true) {
      if (nowDone) remote.insertRow("deadline_done", JSONObject().put("deadline_id", id).put("student_id", user.id))
      else remote.deleteRows("deadline_done", "deadline_id=eq.$id&student_id=eq.${user.id}")
    }
  }

  fun toggleMaterialDownloaded(id: String) {
    val user = currentUser.value
    if (!isActive(user)) return
    val mine = _downloads.value[user.id].orEmpty()
    val next = if (id in mine) mine - id else mine + id
    _downloads.value = _downloads.value + (user.id to next)
  }

  fun markAllNotificationsRead() {
    val uid = currentUser.value.id
    _notifications.value = _notifications.value.map { if (it.userId == uid) it.copy(isRead = true) else it }
    push(refresh = false, silent = true) {
      remote.rpcCall("mark_all_notifications_read", JSONObject())
    }
  }

  fun submitStudentWork(
    deadlineId: String,
    courseCode: String,
    assignmentTitle: String,
    notes: String,
    attachment: AttachmentFile
  ) {
    val student = currentUser.value
    if (student.role != Role.STUDENT || !isActive(student)) return
    val deadline = _deadlines.value.find { it.id == deadlineId } ?: return
    if (!canSeeDeadline(deadline, student)) return
    val now = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date()) + " EAT"
    val newSub = StudentSubmission(
      id = "sub-" + UUID.randomUUID().toString().take(6),
      deadlineId = deadlineId,
      courseCode = courseCode,
      assignmentTitle = assignmentTitle,
      studentId = student.id,
      studentName = student.fullName,
      studentNumber = student.studentNumber ?: "STUDENT-${student.id.takeLast(4)}",
      notes = notes,
      attachment = attachment,
      submittedAt = now,
      approvalStatus = ContentApprovalStatus.PENDING_REVIEW
    )
    _submissions.value = listOf(newSub) + _submissions.value

    val alreadySubmitted = _doneTicks.value[student.id].orEmpty().contains(deadlineId)
    _deadlines.value = _deadlines.value.map { dl ->
      if (dl.id == deadlineId && !alreadySubmitted) dl.copy(submittedCount = dl.submittedCount + 1) else dl
    }
    _doneTicks.value = _doneTicks.value + (student.id to (_doneTicks.value[student.id].orEmpty() + deadlineId))

    if (isUuid(deadlineId)) push {
      val up = uploadPicked("submissions", "${student.id}/$deadlineId", attachment) ?: return@push RemoteResult(true)
      remote.insertRow("submissions", JSONObject()
        .put("deadline_id", deadlineId).put("course_code", serverCourse(courseCode)).put("student_id", student.id)
        .put("notes", notes).put("storage_path", up.first).put("file_name", attachment.fileName)
        .put("mime_type", CloudFiles.mimeOf(attachment.fileName, attachment.mimeType)).put("size_bytes", maxOf(up.second, 1L)))
    }
  }

  fun gradeStudentWork(submissionId: String, grade: String, feedback: String) {
    val actor = currentUser.value
    val existing = _submissions.value.find { it.id == submissionId } ?: return
    if (!canSeeSubmission(existing, actor) || actor.role == Role.STUDENT) return

    _submissions.value = _submissions.value.map { sub ->
      if (sub.id == submissionId) sub.copy(grade = grade, feedback = feedback, approvalStatus = ContentApprovalStatus.APPROVED) else sub
    }
    if (isUuid(submissionId)) push {
      remote.patchRows("submissions", "id=eq.$submissionId", JSONObject().put("grade", grade).put("feedback", feedback))
    }
  }

  fun deleteStudentSubmission(submissionId: String) {
    val sub = _submissions.value.find { it.id == submissionId } ?: return
    if (!canEditOrDelete(sub.studentId)) return
    _submissions.value = _submissions.value.filter { it.id != submissionId }
    if (isUuid(submissionId)) push { remote.deleteRows("submissions", "id=eq.$submissionId") }
  }

  fun publishAnnouncement(
    title: String,
    body: String,
    audienceType: AudienceType,
    audienceName: String,
    audienceCode: String,
    priority: AnnouncementPriority,
    attachment: AttachmentFile? = null,
    responsibleLecturerId: String? = null
  ): PostError? {
    val author = currentUser.value
    val rep = author.role == Role.STUDENT && author.isClassRep
    if (!isActive(author) || (author.role == Role.STUDENT && !rep)) return PostError.NOT_ALLOWED
    if (rep && priority == AnnouncementPriority.URGENT) return PostError.URGENT_NOT_ALLOWED

    when (audienceType) {
      AudienceType.CAMPUS -> if (author.role != Role.ADMIN) {
        return PostError.CAMPUS_REGISTRY_ONLY
      }
      AudienceType.COURSE -> if ((author.role == Role.LECTURER && audienceCode !in author.teachesCourses) ||
        (rep && audienceCode !in author.enrolledCourses)) {
        return PostError.AUDIENCE_NOT_YOURS
      }
      AudienceType.PROGRAMME -> if ((author.role == Role.LECTURER &&
        author.teachesCourses.none { it in catalog.catalogFor(author.universityId)[audienceCode].orEmpty() }) ||
        (rep && audienceCode != author.programme)
      ) {
        return PostError.AUDIENCE_NOT_YOURS
      }
    }

    var responsible: UserProfile? = null
    if (responsibleLecturerId != null && responsibleLecturerId != author.id) {
      responsible = auth.accounts.value[responsibleLecturerId]?.profile
      val valid = responsible != null && responsible.role == Role.LECTURER && responsible.status == AccountStatus.APPROVED &&
        responsible.universityId == author.universityId &&
        (audienceType != AudienceType.COURSE || audienceCode in responsible.teachesCourses)
      if (!valid) return PostError.RESPONSIBLE_NOT_FOR_COURSE
    }

    if (priority == AnnouncementPriority.URGENT && author.role == Role.LECTURER) {
      if (audienceType != AudienceType.COURSE) {
        return PostError.URGENT_NOT_ALLOWED
      }
      if (urgentSentToday(author.id) >= DAILY_URGENT_LIMIT) {
        return PostError.URGENT_LIMIT_REACHED
      }
    }
    if (priority == AnnouncementPriority.URGENT && author.role == Role.LECTURER) {
      urgentCounts[todayKey(author.id)] = urgentSentToday(author.id) + 1
    }

    val newAnn = Announcement(
      id = "ann-" + UUID.randomUUID().toString().take(6),
      authorId = author.id,
      authorName = author.fullName,
      authorRole = author.cohort ?: "Faculty Member",
      title = title,
      body = body,
      audienceType = audienceType,
      audienceName = audienceName,
      audienceCode = audienceCode,
      responsibleLecturerId = responsible?.id,
      responsibleLecturerName = responsible?.fullName,
      publishedAt = "Just now",
      priority = priority,
      readCount = 0,
      totalStudents = audienceRecipientIds(audienceType, audienceCode).count { id ->
        activeProfiles().any { it.id == id && it.role == Role.STUDENT }
      },
      isRead = true,
      attachment = attachment,
      approvalStatus = ContentApprovalStatus.APPROVED
    )
    _announcements.value = listOf(newAnn) + _announcements.value
    push {
      val row = JSONObject().put("author_id", author.id).put("title", title.trim()).put("body", body.trim())
        .put("audience", audienceType.name.lowercase()).put("priority", priority.name.lowercase())
      when (audienceType) {
        AudienceType.PROGRAMME -> row.put("programme_id", auth.programmeUuid(author.universityId, audienceCode) ?: return@push RemoteResult(false, RemoteError.BAD_DETAILS))
        AudienceType.COURSE -> row.put("course_code", serverCourse(audienceCode))
        AudienceType.CAMPUS -> Unit
      }
      responsible?.let { row.put("responsible_lecturer_id", it.id) }
      if (attachment?.uriString != null) {
        val up = uploadPicked("announcements", author.id, attachment) ?: return@push RemoteResult(true)
        row.put("attachment_path", up.first).put("attachment_size", up.second)
      }
      remote.insertRow("announcements", row)
    }
    return null
  }

  fun updateAnnouncement(id: String, newTitle: String, newBody: String, newPriority: AnnouncementPriority) {
    val ann = _announcements.value.find { it.id == id } ?: return
    if (!canEditOrDelete(ann.authorId)) return
    val author = currentUser.value
    if (newPriority == AnnouncementPriority.URGENT && ann.priority != AnnouncementPriority.URGENT && author.role != Role.ADMIN) return

    _announcements.value = _announcements.value.map {
      if (it.id == id) it.copy(title = newTitle, body = newBody, priority = newPriority) else it
    }
    if (isUuid(id)) push {
      remote.patchRows("announcements", "id=eq.$id", JSONObject().put("title", newTitle.trim()).put("body", newBody.trim()).put("priority", newPriority.name.lowercase()))
    }
  }

  fun deleteAnnouncement(id: String) {
    val ann = _announcements.value.find { it.id == id } ?: return
    if (!canEditOrDelete(ann.authorId)) return
    _announcements.value = _announcements.value.filter { it.id != id }
    _readReceipts.value = _readReceipts.value - id
    if (isUuid(id)) push { remote.deleteRows("announcements", "id=eq.$id") }
  }

  fun retractAnnouncement(id: String): Boolean {
    val ann = _announcements.value.find { it.id == id } ?: return false
    if (!canEditOrDelete(ann.authorId) || ann.isRetracted) return false
    val actor = currentUser.value

    _announcements.value = _announcements.value.map { if (it.id == id) it.copy(isRetracted = true) else it }
    if (isUuid(id)) push { remote.rpcCall("retract_announcement", JSONObject().put("p_id", id)) }
    return true
  }

  fun remindUnread(id: String): Int {
    val ann = _announcements.value.find { it.id == id } ?: return 0
    if (!canEditOrDelete(ann.authorId) || ann.isRetracted || id in _remindedAnnouncements.value) return 0
    val readers = _readReceipts.value[id].orEmpty()
    val targets = audienceRecipientIds(ann.audienceType, ann.audienceCode)
      .filter { it !in readers && it != ann.authorId }
    _remindedAnnouncements.value = _remindedAnnouncements.value + id
    if (isUuid(id)) push { remote.rpcCall("remind_unread", JSONObject().put("p_id", id)) }
    return targets.size
  }

  fun setAnnouncementApproval(id: String, status: ContentApprovalStatus) {
    if (!canApproveOrDisapprove()) return
    _announcements.value = _announcements.value.map { if (it.id == id) it.copy(approvalStatus = status) else it }
    if (isUuid(id)) push { remote.patchRows("announcements", "id=eq.$id", JSONObject().put("approval", CloudSync.approvalText(status))) }
  }

  fun uploadMaterial(
    title: String,
    courseCode: String,
    courseTitle: String,
    description: String,
    attachment: AttachmentFile
  ): Boolean {
    val author = currentUser.value
    val rep = author.role == Role.STUDENT && author.isClassRep
    if (!isActive(author) || (author.role == Role.STUDENT && !rep)) return false
    if (author.role == Role.LECTURER && baseCourse(courseCode) !in author.teachesCourses) return false
    if (rep && baseCourse(courseCode) !in author.enrolledCourses) return false

    val newMat = StudyMaterial(
      id = "mat-" + UUID.randomUUID().toString().take(6),
      courseCode = courseCode,
      courseTitle = courseTitle,
      title = title,
      uploaderId = author.id,
      uploadedBy = author.fullName,
      uploadedAt = "Today",
      attachment = attachment,
      isDownloaded = false,
      description = description,
      approvalStatus = ContentApprovalStatus.APPROVED
    )
    _materials.value = listOf(newMat) + _materials.value
    _downloads.value = _downloads.value + (author.id to (_downloads.value[author.id].orEmpty() + newMat.id))

    push {
      val full = serverCourse(baseCourse(courseCode))
      val up = uploadPicked("materials", "$full/${author.id}", attachment) ?: return@push RemoteResult(true)
      remote.insertRow("study_materials", JSONObject()
        .put("course_code", full).put("title", title.trim()).put("description", description)
        .put("uploader_id", author.id).put("storage_path", up.first).put("file_name", attachment.fileName)
        .put("mime_type", CloudFiles.mimeOf(attachment.fileName, attachment.mimeType)).put("size_bytes", maxOf(up.second, 1L)))
    }
    return true
  }

  fun updateMaterial(id: String, newTitle: String, newDescription: String) {
    val mat = _materials.value.find { it.id == id } ?: return
    if (!canEditOrDelete(mat.uploaderId)) return
    _materials.value = _materials.value.map { if (it.id == id) it.copy(title = newTitle, description = newDescription) else it }
    if (isUuid(id)) push { remote.patchRows("study_materials", "id=eq.$id", JSONObject().put("title", newTitle.trim()).put("description", newDescription)) }
  }

  fun deleteMaterial(id: String) {
    val mat = _materials.value.find { it.id == id } ?: return
    if (!canEditOrDelete(mat.uploaderId)) return
    _materials.value = _materials.value.filter { it.id != id }
    if (isUuid(id)) push { remote.deleteRows("study_materials", "id=eq.$id") }
  }

  fun setMaterialApproval(id: String, status: ContentApprovalStatus) {
    if (!canApproveOrDisapprove()) return
    _materials.value = _materials.value.map { if (it.id == id) it.copy(approvalStatus = status) else it }
    if (isUuid(id)) push { remote.patchRows("study_materials", "id=eq.$id", JSONObject().put("approval", CloudSync.approvalText(status))) }
  }

  fun updateSessionVenue(sessionId: String, newVenue: String, reason: String) {
    val sess = _sessions.value.find { it.id == sessionId } ?: return
    if (!canEditSession(sess)) return

    _sessions.value = _sessions.value.map { s ->
      if (s.id == sessionId) {
        s.copy(venue = newVenue, originalVenue = s.originalVenue ?: s.venue, status = SessionStatus.CHANGED, changeReason = reason)
      } else s
    }
    if (isUuid(sessionId)) push { remote.patchRows("class_sessions", "id=eq.$sessionId", JSONObject().put("venue", newVenue.trim()).put("change_reason", reason)) }
  }

  fun cancelSession(sessionId: String, reason: String) {
    val sess = _sessions.value.find { it.id == sessionId } ?: return
    if (!canEditSession(sess)) return

    _sessions.value = _sessions.value.map { s ->
      if (s.id == sessionId) s.copy(status = SessionStatus.CANCELLED, changeReason = reason) else s
    }
    if (isUuid(sessionId)) push { remote.patchRows("class_sessions", "id=eq.$sessionId", JSONObject().put("status", "cancelled").put("change_reason", reason)) }
  }

  fun restoreSession(sessionId: String) {
    val sess = _sessions.value.find { it.id == sessionId } ?: return
    if (!canEditSession(sess)) return
    _sessions.value = _sessions.value.map { s ->
      if (s.id == sessionId) s.copy(status = SessionStatus.SCHEDULED, changeReason = null, venue = s.originalVenue ?: s.venue) else s
    }
    if (isUuid(sessionId)) push {
      remote.patchRows("class_sessions", "id=eq.$sessionId", JSONObject()
        .put("status", "scheduled").put("change_reason", JSONObject.NULL).put("venue", sess.originalVenue ?: sess.venue))
    }
  }

  fun deleteSession(sessionId: String) {
    val sess = _sessions.value.find { it.id == sessionId } ?: return
    if (!canEditSession(sess)) return
    _sessions.value = _sessions.value.filter { it.id != sessionId }
    if (isUuid(sessionId)) push { remote.deleteRows("class_sessions", "id=eq.$sessionId") }
  }

  fun setSessionApproval(sessionId: String, status: ContentApprovalStatus) {
    if (!canApproveOrDisapprove()) return
    _sessions.value = _sessions.value.map { if (it.id == sessionId) it.copy(approvalStatus = status) else it }
    if (isUuid(sessionId)) push { remote.patchRows("class_sessions", "id=eq.$sessionId", JSONObject().put("approval", CloudSync.approvalText(status))) }
  }

  fun addDeadline(
    courseCode: String,
    courseTitle: String,
    title: String,
    instructions: String,
    dueDateTime: String,
    acceptedFormats: String,
    attachment: AttachmentFile? = null
  ): Boolean {
    val author = currentUser.value
    val rep = author.role == Role.STUDENT && author.isClassRep
    if (!isActive(author) || (author.role == Role.STUDENT && !rep)) return false
    if (author.role == Role.LECTURER && baseCourse(courseCode) !in author.teachesCourses) return false
    if (rep && baseCourse(courseCode) !in author.enrolledCourses) return false

    val newDl = DeadlineItem(
      id = "dl-" + UUID.randomUUID().toString().take(6),
      courseCode = courseCode,
      courseTitle = courseTitle,
      lecturerId = author.id,
      lecturerName = author.fullName,
      title = title,
      instructions = instructions,
      dueDateTime = dueDateTime,
      urgencyText = "Upcoming",
      acceptedFormats = acceptedFormats.ifBlank { "PDF or Images (Max 15 MB)" },
      isCompleted = false,
      submittedCount = 0,
      totalStudents = courseStudentIds(courseCode).size,
      approvalStatus = ContentApprovalStatus.APPROVED,
      attachment = attachment
    )
    _deadlines.value = listOf(newDl) + _deadlines.value

    push {
      val due = DeadlineTime.parse(dueDateTime) ?: return@push RemoteResult(false, RemoteError.BAD_DETAILS)
      val row = JSONObject().put("lecturer_id", author.id).put("title", title.trim()).put("instructions", instructions)
        .put("due_at", CloudSync.isoOf(due)).put("accepted_formats", acceptedFormats.ifBlank { "PDF or images (max 15 MB)" })
      if (courseCode != "ACADEMIC") row.put("course_code", serverCourse(baseCourse(courseCode)))
      remote.insertRow("deadlines", row)
    }
    return true
  }

  fun updateDeadline(
    id: String,
    newTitle: String,
    newInstructions: String,
    newDueDateTime: String,
    acceptedFormats: String? = null,
    attachment: AttachmentFile? = null,
    clearAttachment: Boolean = false
  ) {
    val dl = _deadlines.value.find { it.id == id } ?: return
    if (!canEditOrDelete(dl.lecturerId)) return
    _deadlines.value = _deadlines.value.map {
      if (it.id != id) it else it.copy(
        title = newTitle,
        instructions = newInstructions,
        dueDateTime = newDueDateTime,
        acceptedFormats = acceptedFormats?.ifBlank { null } ?: it.acceptedFormats,
        attachment = when {
          clearAttachment -> null
          attachment != null -> attachment
          else -> it.attachment
        }
      )
    }
    if (newDueDateTime != dl.dueDateTime) {
    } else if (newTitle != dl.title || newInstructions != dl.instructions) {
    }
    if (isUuid(id)) push {
      val ch = JSONObject().put("title", newTitle.trim()).put("instructions", newInstructions)
      DeadlineTime.parse(newDueDateTime)?.let { ch.put("due_at", CloudSync.isoOf(it)) }
      acceptedFormats?.ifBlank { null }?.let { ch.put("accepted_formats", it) }
      remote.patchRows("deadlines", "id=eq.$id", ch)
    }
  }

  fun deleteDeadline(id: String) {
    val dl = _deadlines.value.find { it.id == id } ?: return
    if (!canEditOrDelete(dl.lecturerId)) return
    _deadlines.value = _deadlines.value.filter { it.id != id }
    if (isUuid(id)) push { remote.deleteRows("deadlines", "id=eq.$id") }
  }

  fun setDeadlineApproval(id: String, status: ContentApprovalStatus) {
    if (!canApproveOrDisapprove()) return
    _deadlines.value = _deadlines.value.map { if (it.id == id) it.copy(approvalStatus = status) else it }
    if (isUuid(id)) push { remote.patchRows("deadlines", "id=eq.$id", JSONObject().put("approval", CloudSync.approvalText(status))) }
  }

  private val _quizzes = MutableStateFlow<List<Quiz>>(emptyList())
  private val _attempts = MutableStateFlow<List<QuizAttempt>>(emptyList())

  val quizzes: StateFlow<List<Quiz>> = combine(_quizzes, currentUser) { list, user ->
    list.filter { canSeeQuiz(it, user) }.map { if (user.role == Role.STUDENT) it.withoutAnswers() else it }
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  val attempts: StateFlow<List<QuizAttempt>> = combine(_attempts, _quizzes, currentUser) { list, quizList, user ->
    list.filter { a -> quizList.firstOrNull { it.id == a.quizId }?.let { canSeeAttempt(a, it, user) } ?: false }
  }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  private fun canSeeQuiz(q: Quiz, user: UserProfile): Boolean {
    if (!isActive(user) || !sameCampus(q.lecturerId, user)) return false
    return when (user.role) {
      Role.ADMIN -> true
      Role.LECTURER -> q.lecturerId == user.id || baseCourse(q.courseCode) in user.teachesCourses
      Role.STUDENT -> baseCourse(q.courseCode) in user.enrolledCourses
    }
  }

  private fun canSeeAttempt(a: QuizAttempt, q: Quiz, user: UserProfile): Boolean {
    if (!isActive(user) || !sameCampus(q.lecturerId, user)) return false
    return when (user.role) {
      Role.STUDENT -> a.studentId == user.id
      Role.LECTURER -> q.lecturerId == user.id
      Role.ADMIN -> true
    }
  }

  private fun validateWindow(durationMinutes: Int, opensAt: Long, closesAt: Long, now: Long): QuizError? = when {
    durationMinutes !in 1..180 -> QuizError.BAD_DURATION
    closesAt <= opensAt || closesAt <= now -> QuizError.BAD_WINDOW
    closesAt - opensAt < durationMinutes * 60_000L -> QuizError.BAD_WINDOW
    else -> null
  }

  private fun validateQuestions(questions: List<QuizQuestion>): QuizError? {
    if (questions.isEmpty() || questions.size > 50) return QuizError.NO_QUESTIONS
    for (q in questions) {
      if (q.text.isBlank() || q.points !in 1..100) return QuizError.BAD_QUESTION
      if (q.type == QuestionType.MULTIPLE_CHOICE) {
        if (q.options.size !in 2..6 || q.options.any { it.isBlank() }) return QuizError.BAD_QUESTION
        if (q.correctOption == null || q.correctOption !in q.options.indices) return QuizError.BAD_QUESTION
      }
    }
    return null
  }

  fun createQuiz(
    courseCode: String,
    title: String,
    instructions: String,
    durationMinutes: Int,
    opensAt: Long,
    closesAt: Long,
    questions: List<QuizQuestion>,
    now: Long = System.currentTimeMillis()
  ): QuizError? {
    val author = currentUser.value
    if (!isActive(author) || author.role == Role.STUDENT) return QuizError.NOT_ALLOWED
    val code = courseCode.trim().uppercase()
    val courseTitle = catalog.coursesFor(author.universityId)[code] ?: return QuizError.UNKNOWN_COURSE
    if (author.role == Role.LECTURER && baseCourse(code) !in author.teachesCourses) return QuizError.NOT_ALLOWED
    if (title.isBlank()) return QuizError.TITLE_REQUIRED
    validateQuestions(questions)?.let { return it }
    validateWindow(durationMinutes, opensAt, closesAt, now)?.let { return it }

    val quiz = Quiz(
      id = "quiz-" + UUID.randomUUID().toString().take(6),
      courseCode = code, courseTitle = courseTitle,
      lecturerId = author.id, lecturerName = author.fullName,
      title = title.trim(), instructions = instructions.trim(),
      durationMinutes = durationMinutes, opensAt = opensAt, closesAt = closesAt,
      questions = questions.mapIndexed { i, q -> q.copy(id = "q-${i + 1}", text = q.text.trim()) }
    )
    _quizzes.value = listOf(quiz) + _quizzes.value
    push {
      val qs = JSONArray()
      questions.forEach { q ->
        val o = JSONObject().put("kind", if (q.type == QuestionType.MULTIPLE_CHOICE) "multiple_choice" else "short_answer")
          .put("body", q.text.trim()).put("points", q.points)
        if (q.type == QuestionType.MULTIPLE_CHOICE) o.put("options", JSONArray(q.options)).put("correct", q.correctOption ?: 0)
        else o.put("model_answer", q.modelAnswer)
        qs.put(o)
      }
      remote.rpcCall("create_quiz", JSONObject().put("p_course", serverCourse(code)).put("p_title", title.trim())
        .put("p_instructions", instructions.trim()).put("p_duration", durationMinutes)
        .put("p_opens", CloudSync.isoOf(opensAt)).put("p_closes", CloudSync.isoOf(closesAt)).put("p_questions", qs))
    }
    return null
  }

  fun rescheduleQuiz(quizId: String, durationMinutes: Int, opensAt: Long, closesAt: Long, now: Long = System.currentTimeMillis()): QuizError? {
    val quiz = _quizzes.value.find { it.id == quizId } ?: return QuizError.NOT_FOUND
    if (!canEditOrDelete(quiz.lecturerId)) return QuizError.NOT_ALLOWED
    validateWindow(durationMinutes, opensAt, closesAt, now)?.let { return it }
    _quizzes.value = _quizzes.value.map { if (it.id == quizId) it.copy(durationMinutes = durationMinutes, opensAt = opensAt, closesAt = closesAt) else it }
    if (isUuid(quizId)) push {
      remote.rpcCall("reschedule_quiz", JSONObject().put("p_quiz", quizId).put("p_duration", durationMinutes)
        .put("p_opens", CloudSync.isoOf(opensAt)).put("p_closes", CloudSync.isoOf(closesAt)))
    }
    return null
  }

  fun deleteQuiz(quizId: String): QuizError? {
    val quiz = _quizzes.value.find { it.id == quizId } ?: return QuizError.NOT_FOUND
    if (!canEditOrDelete(quiz.lecturerId)) return QuizError.NOT_ALLOWED
    _quizzes.value = _quizzes.value.filterNot { it.id == quizId }
    _attempts.value = _attempts.value.filterNot { it.quizId == quizId }
    if (isUuid(quizId)) push { remote.rpcCall("delete_quiz", JSONObject().put("p_quiz", quizId)) }
    return null
  }

  fun finalizeExpiredAttempts(now: Long = System.currentTimeMillis()) {
    if (_attempts.value.none { it.status == AttemptStatus.IN_PROGRESS && it.deadlineAt + SUBMIT_GRACE_MS <= now }) return
    _attempts.value = _attempts.value.map {
      if (it.status == AttemptStatus.IN_PROGRESS && it.deadlineAt + SUBMIT_GRACE_MS <= now)
        it.copy(status = AttemptStatus.AUTO_SUBMITTED, submittedAt = it.deadlineAt) else it
    }
  }

  fun startAttempt(quizId: String, now: Long = System.currentTimeMillis()): StartResult {
    val student = currentUser.value
    if (!isActive(student) || student.role != Role.STUDENT) return StartResult(error = QuizError.NOT_ALLOWED)
    val quiz = _quizzes.value.find { it.id == quizId }?.takeIf { canSeeQuiz(it, student) }
      ?: return StartResult(error = QuizError.NOT_FOUND)
    finalizeExpiredAttempts(now)
    _attempts.value.find { it.quizId == quizId && it.studentId == student.id }?.let {
      return if (it.status == AttemptStatus.IN_PROGRESS && it.deadlineAt > now) StartResult(attempt = it) else StartResult(error = QuizError.ALREADY_SUBMITTED)
    }
    if (now < quiz.opensAt) return StartResult(error = QuizError.NOT_OPEN_YET)
    if (now >= quiz.closesAt) return StartResult(error = QuizError.CLOSED)
    val attempt = QuizAttempt(
      id = "att-" + UUID.randomUUID().toString().take(6),
      quizId = quizId, studentId = student.id, studentName = student.fullName,
      studentNumber = student.studentNumber ?: "STUDENT-${student.id.takeLast(4)}",
      startedAt = now,
      deadlineAt = minOf(now + quiz.durationMinutes * 60_000L, quiz.closesAt)
    )
    _attempts.value = listOf(attempt) + _attempts.value
    if (isUuid(quizId)) push(refresh = false) {
      val res = remote.rpcCall("start_quiz_attempt", JSONObject().put("p_quiz", quizId))
      if (res.ok) runCatching { attemptAlias[attempt.id] = JSONArray(res.body).getJSONObject(0).getString("attempt_id") }
      res
    }
    return StartResult(attempt = attempt)
  }

  private fun ownAttempt(attemptId: String): QuizAttempt? =
    _attempts.value.find { it.id == attemptId && it.studentId == currentUser.value.id && currentUser.value.role == Role.STUDENT }

  private fun cleanAnswer(question: QuizQuestion, raw: String): String? = when (question.type) {
    QuestionType.MULTIPLE_CHOICE -> raw.trim().toIntOrNull()?.takeIf { it in question.options.indices }?.toString()
    QuestionType.SHORT_ANSWER -> raw.take(2000)
  }

  private fun applyAnswers(attempt: QuizAttempt, answers: Map<String, String>): QuizAttempt {
    val quiz = _quizzes.value.find { it.id == attempt.quizId } ?: return attempt
    val merged = attempt.answers.toMutableMap()
    answers.forEach { (qid, raw) ->
      val q = quiz.questions.find { it.id == qid } ?: return@forEach
      val cleaned = cleanAnswer(q, raw)
      if (cleaned == null || cleaned.isBlank()) merged.remove(qid) else merged[qid] = cleaned
    }
    return attempt.copy(answers = merged)
  }

  fun saveAnswer(attemptId: String, questionId: String, answer: String, now: Long = System.currentTimeMillis()): QuizError? {
    finalizeExpiredAttempts(now)
    val attempt = ownAttempt(attemptId) ?: return QuizError.NOT_FOUND
    if (attempt.status != AttemptStatus.IN_PROGRESS) {
      return if (attempt.status == AttemptStatus.AUTO_SUBMITTED) QuizError.TIME_UP else QuizError.ALREADY_SUBMITTED
    }
    if (now >= attempt.deadlineAt) return QuizError.TIME_UP
    val updated = applyAnswers(attempt, mapOf(questionId to answer))
    _attempts.value = _attempts.value.map { if (it.id == attemptId) updated else it }
    if (isUuid(questionId)) push(refresh = false, silent = true) {
      val server = attemptAlias[attemptId] ?: return@push RemoteResult(true)
      remote.rpcCall("save_quiz_answer", JSONObject().put("p_attempt", server).put("p_question", questionId)
        .put("p_answer", updated.answers[questionId].orEmpty()))
    }
    return null
  }

  fun submitAttempt(
    attemptId: String,
    finalAnswers: Map<String, String> = emptyMap(),
    timedOut: Boolean = false,
    now: Long = System.currentTimeMillis()
  ): QuizError? {
    val attempt = ownAttempt(attemptId) ?: return QuizError.NOT_FOUND
    if (attempt.status != AttemptStatus.IN_PROGRESS) return null
    val inTime = now <= attempt.deadlineAt + SUBMIT_GRACE_MS
    var working = if (inTime) applyAnswers(attempt, finalAnswers) else attempt
    val expired = timedOut || now >= attempt.deadlineAt
    working = working.copy(
      status = if (expired) AttemptStatus.AUTO_SUBMITTED else AttemptStatus.SUBMITTED,
      submittedAt = minOf(now, attempt.deadlineAt)
    )
    _attempts.value = _attempts.value.map { if (it.id == attemptId) working else it }
    push {
      val server = attemptAlias[attemptId] ?: return@push RemoteResult(true)
      val ans = JSONObject()
      working.answers.forEach { (q, a) -> ans.put(q, a) }
      remote.rpcCall("submit_quiz_attempt", JSONObject().put("p_attempt", server).put("p_answers", ans))
    }
    val quiz = _quizzes.value.find { it.id == attempt.quizId }
    if (quiz != null) {
    }
    return null
  }

  fun gradeAttempt(attemptId: String, awarded: Map<String, Int>): QuizError? {
    val attempt = _attempts.value.find { it.id == attemptId } ?: return QuizError.NOT_FOUND
    val quiz = _quizzes.value.find { it.id == attempt.quizId } ?: return QuizError.NOT_FOUND
    if (!canEditOrDelete(quiz.lecturerId)) return QuizError.NOT_ALLOWED
    if (attempt.status == AttemptStatus.IN_PROGRESS) return QuizError.NOT_ALLOWED
    val written = quiz.questions.filter { it.type == QuestionType.SHORT_ANSWER }.associateBy { it.id }
    val clean = awarded.filterKeys { it in written }.mapValues { (id, pts) -> pts.coerceIn(0, written.getValue(id).points) }
    _attempts.value = _attempts.value.map { if (it.id == attemptId) it.copy(awarded = clean, marked = true) else it }
    if (isUuid(attemptId)) push {
      val pts = JSONObject()
      clean.forEach { (q, p) -> pts.put(q, p) }
      remote.rpcCall("grade_quiz_attempt", JSONObject().put("p_attempt", attemptId).put("p_points", pts))
    }
    return null
  }

  fun scoreOf(attemptId: String): QuizScore? {
    val attempt = _attempts.value.find { it.id == attemptId } ?: return null
    val quiz = _quizzes.value.find { it.id == attempt.quizId } ?: return null
    val user = currentUser.value
    if (!canSeeAttempt(attempt, quiz, user) || attempt.status == AttemptStatus.IN_PROGRESS) return null
    val choice = quiz.questions.filter { it.type == QuestionType.MULTIPLE_CHOICE }
    val written = quiz.questions.filter { it.type == QuestionType.SHORT_ANSWER }
    return QuizScore(
      choicePoints = choice.filter { attempt.answers[it.id]?.toIntOrNull() == it.correctOption }.sumOf { it.points },
      choiceTotal = choice.sumOf { it.points },
      writtenPoints = written.sumOf { attempt.awarded[it.id] ?: 0 },
      writtenTotal = written.sumOf { it.points },
      complete = written.isEmpty() || attempt.marked
    )
  }

  suspend fun approveUser(userId: String, role: Role, programme: String?, teaches: Set<String>): Boolean {
    if (!canApproveOrDisapprove()) return false
    val before = pendingUsers.value.find { it.id == userId } ?: return false
    val roleName = if (role == Role.LECTURER) "lecturer" else "student"
    val courseCodes = if (role == Role.LECTURER) teaches.map { it.trim().uppercase() } else emptyList()
    val res = remote.approve(userId, roleName, auth.programmeUuid(before.universityId, programme ?: before.programme), courseCodes)
    if (!res.ok) return false
    auth.loadAccounts()
    return true
  }

  suspend fun rejectUser(userId: String): Boolean {
    if (!canApproveOrDisapprove()) return false
    if (pendingUsers.value.none { it.id == userId }) return false
    val res = remote.reject(userId)
    if (res.ok) auth.loadAccounts()
    return res.ok
  }

  suspend fun setClassRep(studentId: String, on: Boolean): Boolean {
    val me = currentUser.value
    if (!isActive(me) || me.role == Role.STUDENT || me.isPlatformAdmin) return false
    val res = remote.rpcCall("set_class_rep", JSONObject().put("p_user", studentId).put("p_on", on))
    if (!res.ok) onCloudMessage?.invoke(
      if (res.body.contains("student_has_no_programme")) "That student has no programme yet."
      else CloudSync.friendly(res))
    pullNow()
    return res.ok
  }

  private fun isCampusAdmin(): Boolean {
    val u = currentUser.value
    return isActive(u) && u.role == Role.ADMIN && !u.isPlatformAdmin
  }

  fun addProgramme(name: String): Boolean {
    if (!isCampusAdmin()) return false
    val ok = catalog.addProgramme(currentUser.value.universityId, name)
    if (ok) {
      val uni = auth.universityUuid(currentUser.value.universityId)
      if (uni != null) push { remote.insertRow("programmes", JSONObject().put("university_id", uni).put("name", name.trim())) }
    }
    return ok
  }

  fun addCourse(code: String, title: String, programme: String?): Boolean {
    if (!isCampusAdmin()) return false
    val uni = currentUser.value.universityId
    val ok = catalog.addCourse(uni, code, title, programme)
    if (ok) {
      val clean = code.trim().uppercase().replace(" ", "")
      if (!programme.isNullOrBlank()) auth.enrollProgramme(uni, programme.trim(), clean)
      push {
        val row = JSONObject().put("code", clean).put("title", title.trim())
        if (!programme.isNullOrBlank()) row.put("programme", programme.trim())
        remote.rpcCall("import_courses", JSONObject().put("p_rows", JSONArray().put(row)))
      }
    }
    return ok
  }

  fun importCourses(text: String): ImportResult {
    if (!isCampusAdmin()) return ImportResult(0, 0)
    val uni = currentUser.value.universityId
    val before = catalog.coursesFor(uni).keys
    val result = catalog.importCourses(uni, text)
    catalog.catalogFor(uni).forEach { (programme, codes) ->
      codes.filter { it !in before }.forEach { auth.enrollProgramme(uni, programme, it) }
    }
    if (result.added > 0) {
      val titles = catalog.coursesFor(uni)
      val programmes = catalog.catalogFor(uni)
      push {
        val rows = JSONArray()
        titles.filterKeys { it !in before }.forEach { (code, title) ->
          val row = JSONObject().put("code", code).put("title", title)
          programmes.entries.firstOrNull { code in it.value }?.key?.let { row.put("programme", it) }
          rows.put(row)
        }
        remote.rpcCall("import_courses", JSONObject().put("p_rows", rows))
      }
    }
    return result
  }

  fun setProgrammeCourses(programme: String, codes: Set<String>): Boolean {
    if (!isCampusAdmin()) return false
    val uni = currentUser.value.universityId
    val before = catalog.catalogFor(uni).entries.firstOrNull { it.key.equals(programme.trim(), true) }?.value.orEmpty()
    if (!catalog.setProgrammeCourses(uni, programme, codes)) return false
    (codes - before).forEach { auth.enrollProgramme(uni, programme.trim(), it) }
    push {
      remote.rpcCall(
        "set_programme_courses",
        JSONObject().put("p_programme_name", programme.trim()).put("p_codes", JSONArray(codes.toList()))
      )
    }
    return true
  }

  fun addRoom(name: String): Boolean {
    if (!isCampusAdmin()) return false
    val ok = catalog.addRoom(currentUser.value.universityId, name)
    if (ok) {
      val uni = auth.universityUuid(currentUser.value.universityId)
      if (uni != null) push { remote.insertRow("rooms", JSONObject().put("university_id", uni).put("name", name.trim())) }
    }
    return ok
  }

  fun campusLecturers(): List<UserProfile> =
    if (!isCampusAdmin()) emptyList()
    else activeProfiles().filter { it.role == Role.LECTURER }.sortedBy { it.fullName }

  fun setCourseLecturers(courseCode: String, lecturerIds: Set<String>): AssignLecturersResult {
    if (!isCampusAdmin()) return AssignLecturersResult(AssignLecturersError.NOT_ALLOWED)
    val uni = currentUser.value.universityId
    val code = courseCode.trim().uppercase()
    if (code !in catalog.coursesFor(uni)) return AssignLecturersResult(AssignLecturersError.UNKNOWN_COURSE)

    val lecturers = activeProfiles().filter { it.role == Role.LECTURER }.associateBy { it.id }
    if (lecturerIds.any { it !in lecturers }) return AssignLecturersResult(AssignLecturersError.NOT_A_LECTURER)

    val before = lecturers.values.filter { code in it.teachesCourses }.map { it.id }.toSet()
    val added = lecturerIds - before
    val removed = before - lecturerIds
    if (added.isEmpty() && removed.isEmpty()) return AssignLecturersResult()

    val blocking = removed.filter { id ->
      _sessions.value.any { it.lecturerId == id && baseCourse(it.courseCode) == code && it.status != SessionStatus.CANCELLED }
    }
    if (blocking.isNotEmpty()) {
      return AssignLecturersResult(AssignLecturersError.HAS_CLASSES, blocking.mapNotNull { lecturers[it]?.fullName }.sorted().joinToString(", "))
    }

    (added).forEach { id -> auth.setTeaching(id, lecturers.getValue(id).teachesCourses + code) }
    (removed).forEach { id -> auth.setTeaching(id, lecturers.getValue(id).teachesCourses - code) }

    push {
      remote.rpcCall("set_course_lecturers", JSONObject().put("p_course", code).put("p_lecturers", JSONArray(lecturerIds.toList())))
    }
    if (added.isNotEmpty()) {
    }
    if (removed.isNotEmpty()) {
    }
    return AssignLecturersResult()
  }

  fun schedulableCourses(): List<Pair<String, String>> {
    val user = currentUser.value
    if (!isActive(user) || user.isPlatformAdmin) return emptyList()
    val all = catalog.coursesFor(user.universityId)
    return when (user.role) {
      Role.ADMIN -> all.toList().sortedBy { it.first }
      Role.LECTURER -> all.filterKeys { it in user.teachesCourses }.toList().sortedBy { it.first }
      Role.STUDENT -> if (user.isClassRep) all.filterKeys { it in user.enrolledCourses }.toList().sortedBy { it.first } else emptyList()
    }
  }

  fun lecturersFor(courseCode: String): List<UserProfile> {
    val known = activeProfiles().filter { it.role == Role.LECTURER && courseCode in it.teachesCourses }.sortedBy { it.fullName }
    if (known.isNotEmpty() || !currentUser.value.isClassRep) return known
    val me = currentUser.value
    return _repLecturers.value[courseCode].orEmpty().map { (id, name) ->
      UserProfile(id = id, fullName = name, email = "", role = Role.LECTURER, status = AccountStatus.APPROVED,
        universityId = me.universityId, teachesCourses = setOf(courseCode))
    }
  }

  fun roomsWithAvailability(day: Int, start: String, end: String): List<RoomOption> {
    val user = currentUser.value
    val busy = _sessions.value.filter {
      universityOf(it.lecturerId) == user.universityId && it.status != SessionStatus.CANCELLED &&
        it.dayOfWeek == day && it.startTime < end && start < it.endTime
    }
    return catalog.roomsFor(user.universityId).map { room ->
      RoomOption(room, busy.firstOrNull { it.venue.equals(room, ignoreCase = true) }?.courseCode)
    }
  }

  fun addSession(courseCode: String, lecturerId: String, day: Int, start: String, end: String, venue: String): SessionError? {
    val user = currentUser.value
    val rep = user.role == Role.STUDENT && user.isClassRep
    if (!isActive(user) || user.isPlatformAdmin || (user.role == Role.STUDENT && !rep)) return SessionError.NOT_ALLOWED
    val title = catalog.coursesFor(user.universityId)[courseCode] ?: return SessionError.UNKNOWN_COURSE
    if (user.role == Role.LECTURER && courseCode !in user.teachesCourses) return SessionError.NOT_ALLOWED
    if (rep && courseCode !in user.enrolledCourses) return SessionError.NOT_ALLOWED

    val lecturer = auth.accounts.value[lecturerId]?.profile ?: lecturersFor(courseCode).firstOrNull { it.id == lecturerId }
    if (lecturer == null || lecturer.role != Role.LECTURER || lecturer.status != AccountStatus.APPROVED ||
      lecturer.universityId != user.universityId || courseCode !in lecturer.teachesCourses
    ) return SessionError.LECTURER_NOT_FOR_COURSE

    val timeOk = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    if (day !in 1..6 || !timeOk.matches(start) || !timeOk.matches(end) || start >= end) return SessionError.TIME_INVALID
    if (venue.isBlank()) return SessionError.ROOM_REQUIRED

    val sameTime = _sessions.value.filter {
      universityOf(it.lecturerId) == user.universityId && it.status != SessionStatus.CANCELLED &&
        it.dayOfWeek == day && it.startTime < end && start < it.endTime
    }
    if (sameTime.any { it.venue.equals(venue.trim(), ignoreCase = true) }) return SessionError.ROOM_BUSY
    if (sameTime.any { it.lecturerId == lecturerId }) return SessionError.LECTURER_BUSY

    val programme = catalog.catalogFor(user.universityId).entries.firstOrNull { courseCode in it.value }?.key ?: ""
    val session = ClassSession(
      id = "sess-" + UUID.randomUUID().toString().take(6),
      courseCode = courseCode,
      courseTitle = title,
      lecturerId = lecturer.id,
      lecturerName = lecturer.fullName,
      venue = venue.trim(),
      dayOfWeek = day,
      startTime = start,
      endTime = end,
      programme = programme,
      cohort = ""
    )
    _sessions.value = _sessions.value + session
    push {
      remote.insertRow("class_sessions", JSONObject()
        .put("course_code", serverCourse(courseCode)).put("lecturer_id", lecturer.id).put("day_of_week", day)
        .put("start_time", start).put("end_time", end).put("venue", venue.trim()))
    }
    return null
  }

  private val _dataVersion = MutableStateFlow(0)

  private fun isAppManager(): Boolean = currentUser.value.let { it.isPlatformAdmin && isActive(it) }

  private val _remoteSummaries = MutableStateFlow<Map<String, RemoteCampusSummary>>(emptyMap())

  private val remote = RemoteAdmin(token = { auth.accessToken }, refresh = { auth.refreshAccessToken() })

  val peopleAdmin = PeopleAdmin(remote)

  init {
    Backend.tokenSource = { auth.accessToken }
    Backend.tokenRefresh = { auth.refreshAccessToken() }
  }

  private val cloud = CloudSync(remote)
  private val pushMutex = Mutex()
  private val pullMutex = Mutex()
  private val attemptAlias = java.util.concurrent.ConcurrentHashMap<String, String>()
  private val _repLecturers = MutableStateFlow<Map<String, List<Pair<String, String>>>>(emptyMap())
  private val _classMembers = MutableStateFlow<List<ClassMember>>(emptyList())

  val classMembers: StateFlow<List<ClassMember>> = _classMembers.asStateFlow()

  var onCloudMessage: ((String) -> Unit)? = null

  private val _syncing = MutableStateFlow(false)
  val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

  private val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
  private fun isUuid(id: String) = uuidRegex.matches(id)

  private fun uniCode(): String = catalog.university(currentUser.value.universityId)?.shortName.orEmpty()

  private fun serverCourse(short: String): String = auth.serverCourseCode(uniCode(), short)

  private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifBlank { "file" }

  private fun push(refresh: Boolean = true, silent: Boolean = false, block: suspend () -> RemoteResult) {
    if (!Backend.live) return
    scope.launch(Dispatchers.IO) {
      pushMutex.withLock {
        val res = try { block() } catch (e: Exception) { RemoteResult(false, RemoteError.NETWORK) }
        if (!res.ok && !silent) onCloudMessage?.invoke(CloudSync.friendly(res))
      }
      if (refresh) pullNow()
    }
  }

  fun syncNow() {
    if (!Backend.live) return
    scope.launch(Dispatchers.IO) { pullNow() }
  }

  private suspend fun pullNow() {
    pullMutex.withLock {
      val me = currentUser.value
      if (!isActive(me) || me.isPlatformAdmin || auth.accessToken == null) return
      val code = catalog.university(me.universityId)?.shortName ?: return
      _syncing.value = true
      try {
        if (me.role == Role.ADMIN) auth.loadAccounts()
        val snap = cloud.pull(auth.accounts.value[me.id]?.profile ?: me, code)
        if (snap == null) { _isOffline.value = true; return }
        applySnapshot(me, snap)
        if (me.role == Role.ADMIN) cloud.auditLog(me)?.let { _auditLogs.value = it }
        _isOffline.value = false
        _lastSyncedAt.value = System.currentTimeMillis()
        prefetchFiles(snap.files)
      } finally {
        _syncing.value = false
      }
    }
  }

  private fun applySnapshot(me: UserProfile, snap: CloudSnapshot) {
    catalog.replaceCampusLists(me.universityId, snap.courses, snap.rooms)
    catalog.replaceCampusProgrammes(me.universityId, snap.programmes)
    auth.updateProfile(me.id) {
      it.copy(
        enrolledCourses = if (it.role == Role.STUDENT) snap.enrolled else it.enrolledCourses,
        teachesCourses = if (it.role == Role.LECTURER) snap.teaches else it.teachesCourses,
        isClassRep = snap.isClassRep
      )
    }
    _sessions.value = snap.sessions
    _announcements.value = snap.announcements
    _materials.value = snap.materials
    _deadlines.value = snap.deadlines
    _submissions.value = snap.submissions
    _notifications.value = snap.notifications
    _readReceipts.value = snap.reads.associateWith { setOf(me.id) }
    _doneTicks.value = _doneTicks.value + (me.id to snap.done)
    _repLecturers.value = snap.courseLecturers
    _classMembers.value = if (me.role == Role.ADMIN) {
      auth.accounts.value.values.map { it.profile }
        .filter { it.role == Role.STUDENT && it.status == AccountStatus.APPROVED && !it.programme.isNullOrBlank() }
        .map { ClassMember(it.id, it.fullName, it.studentNumber.orEmpty(), it.programme.orEmpty(), it.isClassRep) }
        .sortedBy { it.name }
    } else snap.classMembers

    val serverToLocal = attemptAlias.entries.associate { it.value to it.key }
    val fromServer = snap.attempts.map { a -> serverToLocal[a.id]?.let { local -> a.copy(id = local) } ?: a }
    val pendingLocal = _attempts.value.filter {
      it.studentId == me.id && it.id.startsWith("att-") && it.id !in attemptAlias.keys &&
        it.status == AttemptStatus.IN_PROGRESS && fromServer.none { s -> s.quizId == it.quizId && s.studentId == it.studentId }
    }
    _quizzes.value = snap.quizzes
    _attempts.value = pendingLocal + fromServer
    _dataVersion.value++
  }

  private suspend fun prefetchFiles(files: List<FileRef>) {
    for (f in files.take(40)) {
      if (f.size > 25L * 1024 * 1024) continue
      val bytes = remote.downloadFile(f.bucket, f.path) ?: continue
      val uri = CloudFiles.store(f.bucket, f.path, bytes) ?: continue
      when (f.kind) {
        "ann" -> _announcements.value = _announcements.value.map { if (it.id == f.itemId) it.copy(attachment = it.attachment?.copy(uriString = uri)) else it }
        "mat" -> _materials.value = _materials.value.map { if (it.id == f.itemId) it.copy(attachment = it.attachment.copy(uriString = uri)) else it }
        "sub" -> _submissions.value = _submissions.value.map { if (it.id == f.itemId) it.copy(attachment = it.attachment.copy(uriString = uri)) else it }
      }
    }
  }

  private suspend fun uploadPicked(bucket: String, folder: String, file: AttachmentFile): Pair<String, Long>? {
    val bytes = CloudFiles.read(file.uriString)
    if (bytes == null || bytes.isEmpty()) { onCloudMessage?.invoke("That file could not be read."); return null }
    val path = "$folder/${System.currentTimeMillis()}_${safeName(file.fileName)}"
    val up = remote.uploadFile(bucket, path, bytes, CloudFiles.mimeOf(file.fileName, file.mimeType))
    if (!up.ok) { onCloudMessage?.invoke(CloudSync.friendly(up)); return null }
    return path to bytes.size.toLong()
  }

  init {
    scope.launch(Dispatchers.IO) {
      while (true) {
        delay(60_000)
        if (auth.session.value != null) pullNow()
      }
    }
  }

  val campuses: StateFlow<List<CampusSummary>> =
    combine(catalog.universities, _remoteSummaries, _dataVersion) { universities, remoteByCode, _ ->
      universities.map { u ->
        val r = remoteByCode[u.shortName]
        CampusSummary(
          university = u,
          people = r?.people ?: 0, pending = r?.pending ?: 0, admins = r?.admins ?: 0,
          programmes = r?.programmes ?: 0, courses = r?.courses ?: 0, rooms = r?.rooms ?: 0,
          sessions = r?.sessions ?: 0, announcements = r?.announcements ?: 0,
          materials = r?.materials ?: 0, works = r?.works ?: 0
        )
      }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

  suspend fun createCampus(
    code: String,
    name: String,
    adminName: String,
    adminEmail: String,
    adminNumber: String,
    adminPassword: String,
    location: String = "",
    contactEmail: String = "",
    contactPhone: String = ""
  ): PlatformResult {
    if (!isAppManager()) return PlatformResult(false, PlatformError.NOT_ALLOWED)
    val shortName = code.trim().uppercase()
    if (!Regex("[A-Z0-9_-]{2,20}").matches(shortName) || shortName == "PLATFORM" || name.trim().length < 2) {
      return PlatformResult(false, PlatformError.DETAILS_MISSING)
    }
    val pre = auth.checkAdminDetails(adminName, adminEmail, adminNumber, adminPassword)
    if (pre != null) return PlatformResult(false, authError = pre)

    val made = remote.createUniversity(shortName, name.trim())
    if (!made.ok) return PlatformResult(false, remoteToPlatform(made.error), remoteToAuth(made.error))
    val admin = remote.provisionUser(shortName, "admin", adminName.trim(), adminEmail.trim().lowercase(), adminNumber.trim(), null, adminPassword)
    if (!admin.ok) {
      remote.deleteUniversity(made.detail)
      return PlatformResult(false, remoteToPlatform(admin.error), remoteToAuth(admin.error))
    }
    if (location.isNotBlank() || contactEmail.isNotBlank() || contactPhone.isNotBlank()) {
      remote.updateDetails(made.detail, name.trim(), location.trim(), contactEmail.trim(), contactPhone.trim())
    }
    auth.loadDirectory()
    refreshCampuses()
    return PlatformResult(true)
  }

  private fun remoteToPlatform(e: RemoteError?): PlatformError? = when (e) {
    RemoteError.NOT_ALLOWED -> PlatformError.NOT_ALLOWED
    RemoteError.CAMPUS_EXISTS -> PlatformError.CAMPUS_EXISTS
    RemoteError.BAD_DETAILS -> PlatformError.DETAILS_MISSING
    RemoteError.EMAIL_TAKEN, RemoteError.NUMBER_TAKEN, RemoteError.WEAK_PASSWORD, RemoteError.INVALID_NAME,
    RemoteError.INVALID_EMAIL, RemoteError.INVALID_NUMBER -> null
    else -> PlatformError.FAILED
  }

  private fun remoteToAuth(e: RemoteError?): com.willykez.campconnect.data.auth.AuthError? = when (e) {
    RemoteError.EMAIL_TAKEN -> com.willykez.campconnect.data.auth.AuthError.EMAIL_TAKEN
    RemoteError.NUMBER_TAKEN -> com.willykez.campconnect.data.auth.AuthError.NUMBER_TAKEN
    RemoteError.WEAK_PASSWORD -> com.willykez.campconnect.data.auth.AuthError.PASSWORD_WEAK
    RemoteError.INVALID_NAME -> com.willykez.campconnect.data.auth.AuthError.NAME_REQUIRED
    RemoteError.INVALID_EMAIL -> com.willykez.campconnect.data.auth.AuthError.EMAIL_INVALID
    RemoteError.INVALID_NUMBER -> com.willykez.campconnect.data.auth.AuthError.NUMBER_INVALID
    else -> null
  }

  suspend fun refreshCampuses() {
    if (!isAppManager()) return
    val list = remote.campusSummaries() ?: return
    _remoteSummaries.value = list.associateBy { it.code }
    cloud.auditLog(currentUser.value)?.let { _auditLogs.value = it }
    _dataVersion.value++
  }

  suspend fun wipeCampus(universityId: String, scope: WipeScope): Boolean {
    if (!isAppManager() || universityId == PlatformConfig.ID) return false
    val uuid = auth.universityUuid(universityId) ?: return false
    val res = remote.wipeCampus(uuid, scope.name.lowercase())
    refreshCampuses()
    return res.ok
  }

  val myCampus: StateFlow<com.willykez.campconnect.data.model.University?> =
    combine(currentUser, catalog.universities) { user, universities ->
      if (user.isPlatformAdmin) null else universities.firstOrNull { it.id == user.universityId }
    }.stateIn(scope, SharingStarted.Eagerly, null)

  val campusPaused: StateFlow<Boolean> = myCampus
    .map { it?.underMaintenance == true }
    .stateIn(scope, SharingStarted.Eagerly, false)

  suspend fun setCampusMaintenance(universityId: String, on: Boolean, note: String): Boolean {
    if (!isAppManager() || universityId == PlatformConfig.ID) return false
    val uuid = auth.universityUuid(universityId) ?: return false
    val res = remote.setMaintenance(uuid, on, note.trim())
    if (res.ok) { auth.loadDirectory(); _dataVersion.value++ }
    return res.ok
  }

  suspend fun updateCampusDetails(
    universityId: String,
    name: String?,
    location: String,
    contactEmail: String,
    contactPhone: String
  ): Boolean {
    val me = currentUser.value
    val manager = isAppManager()
    val ownAdmin = isActive(me) && me.role == Role.ADMIN && !me.isPlatformAdmin && me.universityId == universityId
    if (!manager && !ownAdmin) return false
    if (name != null && manager && name.trim().length < 2) return false
    val uuid = auth.universityUuid(universityId) ?: return false
    val res = remote.updateDetails(uuid, if (manager) name else null, location.trim(), contactEmail.trim(), contactPhone.trim())
    if (res.ok) { auth.loadDirectory(); _dataVersion.value++ }
    return res.ok
  }

  suspend fun addCampusUser(
    universityId: String,
    role: Role,
    fullName: String,
    email: String,
    idNumber: String,
    programme: String?,
    password: String
  ): PlatformResult {
    val me = currentUser.value
    val manager = isAppManager()
    val ownAdmin = isActive(me) && me.role == Role.ADMIN && !me.isPlatformAdmin && me.universityId == universityId
    if (!manager && !ownAdmin) return PlatformResult(false, PlatformError.NOT_ALLOWED)
    val code = catalog.university(universityId)?.shortName ?: return PlatformResult(false, PlatformError.DETAILS_MISSING)
    if (role == Role.STUDENT && programme.isNullOrBlank()) {
      return PlatformResult(false, authError = com.willykez.campconnect.data.auth.AuthError.PROGRAMME_REQUIRED)
    }
    val pre = auth.checkAdminDetails(fullName, email, idNumber, password)
    if (pre != null) return PlatformResult(false, authError = pre)
    val res = remote.provisionUser(code, role.name.lowercase(), fullName.trim(), email.trim().lowercase(), idNumber.trim(), programme, password)
    if (!res.ok) return PlatformResult(false, remoteToPlatform(res.error), remoteToAuth(res.error))
    if (ownAdmin) auth.loadAccounts() else refreshCampuses()
    return PlatformResult(true)
  }

  suspend fun deleteCampus(universityId: String): Boolean {
    if (!isAppManager() || universityId == PlatformConfig.ID) return false
    val uuid = auth.universityUuid(universityId) ?: return false
    remote.wipeCampus(uuid, "everything")
    val res = remote.deleteUniversity(uuid)
    if (res.ok) { auth.loadDirectory(); refreshCampuses() }
    return res.ok
  }

  fun exportMyData(): String {
    val user = currentUser.value
    val notes = _notifications.value.filter { it.userId == user.id }
    val ticks = _doneTicks.value[user.id].orEmpty()
    val reads = _readReceipts.value.count { (_, readers) -> user.id in readers }
    val myDevices = auth.devices.value[user.id].orEmpty()
    val mySubmissions = _submissions.value.filter { it.studentId == user.id }
    return buildString {
      appendLine("ChuoPulse: data stored about you")
      appendLine()
      appendLine("Name: ${user.fullName}")
      appendLine("Email: ${user.email}")
      appendLine("Role: ${user.role.name}")
      appendLine("Registration/Staff number: ${user.studentNumber ?: user.staffNumber ?: "none"}")
      appendLine("Programme/Department: ${user.programme ?: "none"}")
      appendLine("Enrolled courses: ${user.enrolledCourses.sorted().joinToString(", ").ifBlank { "none" }}")
      appendLine("Courses taught: ${user.teachesCourses.sorted().joinToString(", ").ifBlank { "none" }}")
      appendLine()
      appendLine("Notifications: ${notes.size}")
      appendLine("Deadlines ticked done: ${ticks.size}")
      appendLine("Announcements opened: $reads")
      appendLine("Submissions: ${mySubmissions.size}")
      appendLine("Signed-in phones: ${myDevices.size}")
    }
  }

  private val _deletionRequests = MutableStateFlow<Set<String>>(emptySet())
  val deletionRequests: StateFlow<Set<String>> = _deletionRequests.asStateFlow()

  fun requestDataDeletion() {
    val user = currentUser.value
    if (user.id in _deletionRequests.value || !isActive(user)) return
    _deletionRequests.value = _deletionRequests.value + user.id
    val admins = activeProfiles().filter { it.role == Role.ADMIN }.map { it.id }
  }

}
