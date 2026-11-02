package com.willykez.campconnect.viewmodel

import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.willykez.campconnect.data.auth.AuthError
import com.willykez.campconnect.data.auth.AuthOutcome
import com.willykez.campconnect.data.auth.CodePurpose
import com.willykez.campconnect.data.auth.DeviceSession
import com.willykez.campconnect.data.auth.PasswordCheck
import com.willykez.campconnect.data.auth.PasswordProblem
import com.willykez.campconnect.data.model.AnnouncementPriority
import com.willykez.campconnect.data.model.StartResult
import com.willykez.campconnect.data.model.QuizScore
import com.willykez.campconnect.data.model.QuizQuestion
import com.willykez.campconnect.data.model.QuizError
import com.willykez.campconnect.data.model.AppLanguage
import com.willykez.campconnect.data.model.ClassSession
import com.willykez.campconnect.data.repository.ClassMember
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.data.model.AudienceType
import com.willykez.campconnect.data.model.ContentApprovalStatus
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.UserProfile
import com.willykez.campconnect.data.repository.AudienceOption
import com.willykez.campconnect.data.repository.AssignLecturersError
import com.willykez.campconnect.data.repository.CampusRepository
import com.willykez.campconnect.data.repository.PostError
import com.willykez.campconnect.data.repository.PlatformError
import com.willykez.campconnect.data.repository.PlatformResult
import com.willykez.campconnect.data.repository.RoomOption
import com.willykez.campconnect.data.repository.SessionError
import com.willykez.campconnect.data.repository.WipeScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ScreenTab {
  HOME,
  TIMETABLE,
  MATERIALS,
  PROCESSED_DOCUMENTS,
  CALENDAR,
  QUIZZES,
  SUBMISSIONS,
  PROFILE,
  ADMIN,
  NOTIFICATIONS
}

class CampusViewModel(
  private val repository: CampusRepository = CampusRepository()
) : ViewModel() {

  // ---------- Sign-in state ----------

  val session = repository.session

  /** Campuses offered at sign-up, and the programmes each one's administrator has set up (may be none yet). */
  val universities = repository.catalog.universities
  fun programmesFor(universityId: String): List<String> = repository.catalog.programmesFor(universityId)
  fun coursesFor(universityId: String): List<Pair<String, String>> =
    repository.catalog.coursesFor(universityId).toList().sortedBy { it.first }

  val currentUser = repository.currentUser
  val appLanguage = repository.appLanguage
  val isOffline = repository.isOffline
  val lastSyncedAt = repository.lastSyncedAt
  val is24HourFormat = repository.is24HourFormat
  val sessions = repository.sessions
  val announcements = repository.announcements
  val remindedAnnouncements = repository.remindedAnnouncements
  val materials = repository.materials
  val deadlines = repository.deadlines
  val submissions = repository.submissions
  val notifications = repository.notifications
  val pendingUsers = repository.pendingUsers
  /** Changes whenever any account changes, so screens that list people by course refresh at once. */
  val accounts = repository.auth.accounts
  val auditLogs = repository.auditLogs
  val deletionRequests = repository.deletionRequests
  val campuses = repository.campuses
  val rooms = repository.catalog.rooms
  val catalogProgrammes = repository.catalog.programmes
  val catalogCourses = repository.catalog.courses

  /** Phones signed in to the current person's account. */
  val myDevices: StateFlow<List<DeviceSession>> = combine(repository.devices, repository.currentUser) { all, user ->
    all[user.id].orEmpty()
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val thisDeviceId: String = repository.auth.thisDeviceId

  // Active Navigation Tab
  private val _currentTab = MutableStateFlow(ScreenTab.HOME)
  val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

  // Timetable State
  private val _selectedDay = MutableStateFlow(todayDay())
  val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

  private val _isWeekView = MutableStateFlow(false)
  val isWeekView: StateFlow<Boolean> = _isWeekView.asStateFlow()

  // Filter for Announcements
  private val _announcementFilter = MutableStateFlow("ALL")
  val announcementFilter: StateFlow<String> = _announcementFilter.asStateFlow()

  // Filter for Submissions
  private val _submissionFilterCourse = MutableStateFlow("ALL")
  val submissionFilterCourse: StateFlow<String> = _submissionFilterCourse.asStateFlow()

  // One-line messages shown at the bottom of the screen
  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  fun showMessage(text: String) {
    _message.value = text
  }

  fun messageShown() {
    _message.value = null
  }

  // Unread counts
  val unreadNotificationsCount: StateFlow<Int> = notifications.map { list ->
    list.count { !it.isRead }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  val unreadAnnouncementsCount: StateFlow<Int> = announcements.map { list ->
    list.count { !it.isRead }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  // ---------- Language helper ----------

  val isSwahili: Boolean get() = appLanguage.value == AppLanguage.SWAHILI

  fun t(english: String, swahili: String): String = if (isSwahili) swahili else english

  // ---------- Authentication ----------

  fun checkPassword(password: String, email: String): PasswordCheck = repository.auth.checkPassword(password, email)

  /** True while the app is checking whether the person is still signed in from last time. */
  private val _restoring = MutableStateFlow(true)
  val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

  init {
    repository.onCloudMessage = { text -> showMessage(text) }
    // Stay signed in: bring back the saved sign-in before showing any sign-in screen.
    viewModelScope.launch {
      try { repository.auth.restoreSession() } finally { _restoring.value = false }
    }
    refreshDirectory()
    // Whenever someone signs in (or their role changes), load what their role manages from the server.
    viewModelScope.launch {
      repository.currentUser.map { Triple(it.id, it.role, it.status) }.distinctUntilChanged().collect { refreshAdminData() }
    }
  }

  suspend fun signInSuspend(identifier: String, password: String): AuthOutcome =
    repository.auth.liveSignIn(identifier, password)

  suspend fun signUpSuspend(
    universityId: String, fullName: String, email: String, idNumber: String,
    isStaff: Boolean, programmeOrDepartment: String, password: String
  ): AuthOutcome =
    repository.auth.liveSignUp(universityId, fullName, email, idNumber, isStaff, programmeOrDepartment, password)

  suspend fun submitCodeSuspend(purpose: CodePurpose, email: String, code: String): AuthOutcome =
    repository.auth.liveSubmitCode(purpose, email, code)

  suspend fun resendCodeSuspend(purpose: CodePurpose, email: String) {
    repository.auth.liveResend(purpose, email)
  }

  suspend fun requestPasswordResetSuspend(email: String): AuthOutcome =
    repository.auth.liveRequestReset(email)

  suspend fun resetPasswordSuspend(email: String, code: String, newPassword: String): AuthOutcome =
    repository.auth.liveResetPassword(email, code, newPassword)

  /** Changes my own password. Returns the reason in words when it failed, or null when it worked. */
  suspend fun changePassword(current: String, new: String): String? =
    when (val r = repository.auth.changePassword(current, new)) {
      is AuthOutcome.PasswordChanged -> {
        showMessage(t("Password changed.", "Nenosiri limebadilishwa."))
        null
      }
      is AuthOutcome.Failure -> if (r.error == AuthError.WRONG_CREDENTIALS)
        t("Your current password is not correct.", "Nenosiri lako la sasa si sahihi.") else authErrorText(r.error)
      else -> authErrorText(AuthError.NETWORK)
    }

  /** Re-reads my profile from the server (approval status, role). */
  suspend fun refreshProfile(): Boolean = repository.auth.liveRefreshProfile()

  /** The administrator's second step (authenticator app). */
  suspend fun verifyMfa(code: String): AuthOutcome = repository.auth.liveVerifyMfa(code)

  /** Loads the campuses and programmes people can choose at sign-up. */
  fun refreshDirectory() {
    viewModelScope.launch { repository.auth.loadDirectory() }
  }

  fun signOut() {
    viewModelScope.launch { repository.auth.liveSignOutRemote() }
    repository.signOut()
    resetNavigation()
  }

  fun signOutEverywhere() {
    repository.signOutEverywhere()
    resetNavigation()
  }

  fun signOutDevice(deviceId: String) {
    repository.signOutDevice(deviceId)
    if (deviceId == thisDeviceId) resetNavigation()
  }

  private fun resetNavigation() {
    repository.peopleAdmin.clear()
    _currentTab.value = ScreenTab.HOME
    _selectedDay.value = todayDay()
    _isWeekView.value = false
    _announcementFilter.value = "ALL"
    _submissionFilterCourse.value = "ALL"
    _message.value = null
  }

  fun authErrorText(error: AuthError): String = when (error) {
    AuthError.NAME_REQUIRED -> t("Please enter your full name.", "Tafadhali andika jina lako kamili.")
    AuthError.EMAIL_INVALID -> t("That email address does not look right.", "Anwani hii ya barua pepe haionekani sahihi.")
    AuthError.UNIVERSITY_REQUIRED -> t("Please choose your university.", "Tafadhali chagua chuo chako.")
    AuthError.USE_EMAIL -> t(
      "That number exists at more than one university. Sign in with your email instead.",
      "Namba hiyo ipo katika vyuo zaidi ya kimoja. Ingia kwa barua pepe yako."
    )
    AuthError.EMAIL_TAKEN -> t("An account with this email already exists.", "Akaunti yenye barua pepe hii tayari ipo.")
    AuthError.NUMBER_INVALID -> t(
      "That registration or staff number is not in the right format.",
      "Namba ya usajili au ya mfanyakazi haiko katika muundo sahihi."
    )
    AuthError.NUMBER_TAKEN -> t(
      "That number is already linked to an account.",
      "Namba hiyo tayari imeunganishwa na akaunti."
    )
    AuthError.PROGRAMME_REQUIRED -> t("Please choose your programme.", "Tafadhali chagua programu yako.")
    AuthError.PASSWORD_WEAK -> t(
      "Choose a stronger password.",
      "Chagua nenosiri lenye nguvu zaidi."
    )
    AuthError.WRONG_CREDENTIALS -> t("Email or password is not correct.", "Barua pepe au nenosiri si sahihi.")
    AuthError.ACCOUNT_NOT_ACTIVE -> t("Account not active.", "Akaunti haifanyi kazi.")
    AuthError.NETWORK -> t(
      "Could not reach the server. Check your connection and try again.",
      "Imeshindikana kufikia seva. Angalia mtandao wako kisha ujaribu tena."
    )
    AuthError.CODE_WRONG -> t("That code is not correct.", "Msimbo huo si sahihi.")
    AuthError.CODE_EXPIRED -> t(
      "The code has expired. Ask for a new one.",
      "Msimbo umekwisha muda. Omba mpya."
    )
    AuthError.TOO_MANY_ATTEMPTS -> t(
      "Too many tries. Ask for a new code.",
      "Majaribio mengi mno. Omba msimbo mpya."
    )
    AuthError.EMAIL_SEND_FAILED -> t(
      "The server could not send the email. Please tell the app manager (email service is not set up for this address).",
      "Seva imeshindwa kutuma barua pepe. Tafadhali mwambie msimamizi wa programu (huduma ya barua pepe haijawekwa kwa anwani hii)."
    )
    AuthError.RATE_LIMITED -> t(
      "Too many emails were requested. Wait a few minutes and try again.",
      "Barua pepe nyingi zimeombwa. Subiri dakika chache kisha ujaribu tena."
    )
  }

  fun passwordProblemText(problem: PasswordProblem): String = when (problem) {
    PasswordProblem.TOO_SHORT -> t("At least 8 characters", "Angalau herufi 8")
    PasswordProblem.COMMON -> t("Too common: easy to guess", "Imezoeleka mno: rahisi kubashiri")
    PasswordProblem.CONTAINS_EMAIL -> t("Do not include your email name", "Usiweke jina la barua pepe yako")
    PasswordProblem.REPEATED -> t("Too repetitive", "Inarudiarudia mno")
  }

  // ---------- Navigation ----------

  /** Today as Monday = 1 ... Saturday = 6. Sunday shows Monday, the next teaching day. */
  fun todayDay(): Int = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
    java.util.Calendar.SUNDAY -> 1
    else -> java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1
  }

  fun canEditOrDelete(uploaderOrAuthorId: String): Boolean = repository.canEditOrDelete(uploaderOrAuthorId)

  fun canEditSession(session: ClassSession): Boolean = repository.canEditSession(session)

  /** True while the phone is reading or saving with the server. */
  val syncing: StateFlow<Boolean> = repository.syncing

  /** Students a lecturer or administrator can make class representatives. */
  val classMembers: StateFlow<List<ClassMember>> = repository.classMembers

  fun setClassRep(studentId: String, on: Boolean) {
    viewModelScope.launch {
      val ok = repository.setClassRep(studentId, on)
      if (ok) showMessage(if (on) t("Class representative added.", "Mwakilishi wa darasa ameongezwa.") else t("Class representative removed.", "Mwakilishi wa darasa ameondolewa."))
    }
  }

  fun canApproveOrDisapprove(): Boolean = repository.canApproveOrDisapprove()

  fun setTab(tab: ScreenTab) {
    _currentTab.value = tab
  }

  fun setSelectedDay(day: Int) {
    _selectedDay.value = day
  }

  fun toggleWeekView() {
    _isWeekView.value = !_isWeekView.value
  }

  fun setAnnouncementFilter(filter: String) {
    _announcementFilter.value = filter
  }

  fun setSubmissionFilterCourse(course: String) {
    _submissionFilterCourse.value = course
  }

  fun toggleLanguage() {
    val newLang = if (appLanguage.value == AppLanguage.ENGLISH) AppLanguage.SWAHILI else AppLanguage.ENGLISH
    repository.setLanguage(newLang)
  }

  fun toggle24HourFormat() {
    repository.toggle24HourFormat()
  }

  fun toggleOfflineSimulation() {
    repository.toggleOfflineSimulation()
  }

  fun triggerSync() {
    repository.triggerSync()
  }

  fun markAnnouncementRead(id: String) {
    repository.markAnnouncementRead(id)
  }

  fun toggleDeadlineCompleted(id: String) {
    repository.toggleDeadlineCompleted(id)
  }

  fun toggleMaterialDownloaded(id: String) {
    repository.toggleMaterialDownloaded(id)
  }

  fun markAllNotificationsRead() {
    repository.markAllNotificationsRead()
  }

  // ---------- Student work ----------

  fun submitStudentWork(
    deadlineId: String,
    courseCode: String,
    assignmentTitle: String,
    notes: String,
    attachment: AttachmentFile
  ) {
    repository.submitStudentWork(deadlineId, courseCode, assignmentTitle, notes, attachment)
  }

  fun gradeStudentWork(submissionId: String, grade: String, feedback: String) {
    repository.gradeStudentWork(submissionId, grade, feedback)
  }

  fun deleteStudentSubmission(submissionId: String) {
    repository.deleteStudentSubmission(submissionId)
  }

  // ---------- Announcements ----------

  fun audienceOptions(): List<AudienceOption> = repository.audienceOptions()

  fun urgentLeftToday(): Int = repository.urgentLeftToday()

  fun canUseUrgentFor(type: AudienceType): Boolean = repository.canUseUrgentFor(type)

  /** Returns true when the notice went out. A refusal is explained in a message. */
  fun publishAnnouncement(
    title: String,
    body: String,
    audienceType: AudienceType,
    audienceName: String,
    audienceCode: String,
    priority: AnnouncementPriority,
    attachment: AttachmentFile?,
    responsibleLecturerId: String? = null
  ): Boolean {
    val error = repository.publishAnnouncement(
      title, body, audienceType, audienceName, audienceCode, priority, attachment, responsibleLecturerId
    )
    if (error != null) {
      showMessage(postErrorText(error))
      return false
    }
    return true
  }

  private fun postErrorText(error: PostError): String = when (error) {
    PostError.NOT_ALLOWED -> t("You are not allowed to post notices.", "Huruhusiwi kuweka matangazo.")
    PostError.CAMPUS_REGISTRY_ONLY -> t(
      "Only the Academic Registry can post to the whole campus.",
      "Ni Msajili wa Taaluma pekee anayeweza kutangaza kwa chuo kizima."
    )
    PostError.AUDIENCE_NOT_YOURS -> t(
      "You can only post to courses you teach.",
      "Unaweza kutangaza kwa kozi unazofundisha tu."
    )
    PostError.URGENT_NOT_ALLOWED -> t(
      "Urgent can only be used for your own course.",
      "Dharura inaweza kutumika kwa kozi yako tu."
    )
    PostError.RESPONSIBLE_NOT_FOR_COURSE -> t(
      "That lecturer does not teach this course.",
      "Mhadhiri huyo hafundishi kozi hii."
    )
    PostError.URGENT_LIMIT_REACHED -> t(
      "Daily limit for Urgent notices reached. Use Academic instead.",
      "Kikomo cha kila siku cha matangazo ya Dharura kimefikiwa. Tumia Kitaaluma."
    )
  }

  fun updateAnnouncement(id: String, newTitle: String, newBody: String, newPriority: AnnouncementPriority) {
    repository.updateAnnouncement(id, newTitle, newBody, newPriority)
  }

  fun deleteAnnouncement(id: String) {
    repository.deleteAnnouncement(id)
  }

  fun retractAnnouncement(id: String) {
    if (repository.retractAnnouncement(id)) {
      showMessage(t("Notice withdrawn. A correction was sent.", "Tangazo limeondolewa. Marekebisho yametumwa."))
    }
  }

  fun remindUnread(id: String) {
    val count = repository.remindUnread(id)
    showMessage(
      if (count > 0) t("Reminder sent to $count people.", "Ukumbusho umetumwa kwa watu $count.")
      else t("No one left to remind.", "Hakuna wa kukumbushwa.")
    )
  }

  fun setAnnouncementApproval(id: String, status: ContentApprovalStatus) {
    repository.setAnnouncementApproval(id, status)
  }

  // ---------- Study materials ----------

  fun uploadMaterial(title: String, courseCode: String, courseTitle: String, description: String, attachment: AttachmentFile): Boolean {
    val ok = repository.uploadMaterial(title, courseCode, courseTitle, description, attachment)
    if (!ok) showMessage(t("You can only share files for courses you teach.", "Unaweza kushiriki mafaili ya kozi unazofundisha tu."))
    return ok
  }

  fun updateMaterial(id: String, newTitle: String, newDescription: String) {
    repository.updateMaterial(id, newTitle, newDescription)
  }

  fun deleteMaterial(id: String) {
    repository.deleteMaterial(id)
  }

  fun setMaterialApproval(id: String, status: ContentApprovalStatus) {
    repository.setMaterialApproval(id, status)
  }

  // ---------- Timetable ----------

  fun updateSessionVenue(sessionId: String, newVenue: String, reason: String) {
    repository.updateSessionVenue(sessionId, newVenue, reason)
  }

  fun cancelSession(sessionId: String, reason: String) {
    repository.cancelSession(sessionId, reason)
  }

  fun restoreSession(sessionId: String) {
    repository.restoreSession(sessionId)
  }

  fun deleteSession(sessionId: String) {
    repository.deleteSession(sessionId)
  }

  fun setSessionApproval(sessionId: String, status: ContentApprovalStatus) {
    repository.setSessionApproval(sessionId, status)
  }

  // ---------- Deadlines ----------

  fun addDeadline(courseCode: String, courseTitle: String, title: String, instructions: String, dueDateTime: String, acceptedFormats: String, attachment: AttachmentFile? = null): Boolean {
    val ok = repository.addDeadline(courseCode, courseTitle, title, instructions, dueDateTime, acceptedFormats, attachment)
    if (!ok) showMessage(t("You can only set deadlines for courses you teach.", "Unaweza kuweka tarehe za mwisho kwa kozi unazofundisha tu."))
    return ok
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
    repository.updateDeadline(id, newTitle, newInstructions, newDueDateTime, acceptedFormats, attachment, clearAttachment)
    showMessage(t("Deadline updated.", "Tarehe ya mwisho imesasishwa."))
  }

  fun deleteDeadline(id: String) {
    repository.deleteDeadline(id)
  }

  fun setDeadlineApproval(id: String, status: ContentApprovalStatus) {
    repository.setDeadlineApproval(id, status)
  }

  // ---------- E-learning: timed quizzes ----------

  val quizzes = repository.quizzes
  val quizAttempts = repository.attempts

  fun quizErrorText(error: QuizError): String = when (error) {
    QuizError.NOT_ALLOWED -> t("You are not allowed to do that.", "Huruhusiwi kufanya hivyo.")
    QuizError.UNKNOWN_COURSE -> t("Choose a course from your campus.", "Chagua kozi ya chuo chako.")
    QuizError.TITLE_REQUIRED -> t("Give the quiz a title.", "Weka jina la jaribio.")
    QuizError.NO_QUESTIONS -> t("Add at least one question (up to 50).", "Ongeza angalau swali moja (hadi 50).")
    QuizError.BAD_QUESTION -> t(
      "Every question needs text and points. Multiple choice needs 2 to 6 options and one right answer.",
      "Kila swali linahitaji maandishi na alama. Chaguo nyingi zinahitaji chaguo 2 hadi 6 na jibu moja sahihi."
    )
    QuizError.BAD_DURATION -> t("Time allowed must be between 1 and 180 minutes.", "Muda lazima uwe kati ya dakika 1 na 180.")
    QuizError.BAD_WINDOW -> t(
      "The quiz must close in the future, after it opens, and stay open at least as long as the time allowed.",
      "Jaribio lifungwe baadaye, baada ya kufunguliwa, na libaki wazi angalau kwa muda uliotolewa."
    )
    QuizError.NOT_FOUND -> t("That quiz is no longer available.", "Jaribio hilo halipo tena.")
    QuizError.NOT_OPEN_YET -> t("This quiz has not opened yet.", "Jaribio hili halijafunguliwa bado.")
    QuizError.CLOSED -> t("This quiz has closed.", "Jaribio hili limefungwa.")
    QuizError.ALREADY_SUBMITTED -> t("You have already taken this quiz.", "Umeshafanya jaribio hili.")
    QuizError.TIME_UP -> t("Time is up. Your answers were submitted.", "Muda umeisha. Majibu yako yamewasilishwa.")
  }

  /** Returns the reason in words when the quiz could not be created, or null when it was. */
  fun createQuiz(
    courseCode: String, title: String, instructions: String, durationMinutes: Int,
    opensAt: Long, closesAt: Long, questions: List<QuizQuestion>
  ): String? {
    val error = repository.createQuiz(courseCode, title, instructions, durationMinutes, opensAt, closesAt, questions)
    if (error == null) showMessage(t("Quiz created. Students have been notified.", "Jaribio limeundwa. Wanafunzi wamearifiwa."))
    return error?.let(::quizErrorText)
  }

  fun rescheduleQuiz(quizId: String, durationMinutes: Int, opensAt: Long, closesAt: Long): String? {
    val error = repository.rescheduleQuiz(quizId, durationMinutes, opensAt, closesAt)
    if (error == null) showMessage(t("Quiz rescheduled. Students have been notified.", "Jaribio limepangwa upya. Wanafunzi wamearifiwa."))
    return error?.let(::quizErrorText)
  }

  fun deleteQuiz(quizId: String) {
    val error = repository.deleteQuiz(quizId)
    showMessage(error?.let(::quizErrorText) ?: t("Quiz deleted.", "Jaribio limefutwa."))
  }

  fun startQuiz(quizId: String): StartResult {
    val result = repository.startAttempt(quizId)
    result.error?.let { showMessage(quizErrorText(it)) }
    return result
  }

  fun saveQuizAnswer(attemptId: String, questionId: String, answer: String): QuizError? =
    repository.saveAnswer(attemptId, questionId, answer)

  fun submitQuiz(attemptId: String, finalAnswers: Map<String, String>, timedOut: Boolean) {
    repository.submitAttempt(attemptId, finalAnswers, timedOut)
  }

  fun gradeQuiz(attemptId: String, awarded: Map<String, Int>): String? {
    val error = repository.gradeAttempt(attemptId, awarded)
    if (error == null) showMessage(t("Marks saved. The student has been told.", "Alama zimehifadhiwa. Mwanafunzi ameambiwa."))
    return error?.let(::quizErrorText)
  }

  fun quizScore(attemptId: String): QuizScore? = repository.scoreOf(attemptId)

  fun finalizeExpiredQuizzes() = repository.finalizeExpiredAttempts()

  // ---------- Campus setup (campus administrator) ----------

  fun addProgramme(name: String): Boolean {
    val ok = repository.addProgramme(name)
    showMessage(if (ok) t("Programme added.", "Programu imeongezwa.") else t("That programme already exists or the name is too short.", "Programu hiyo ipo tayari au jina ni fupi mno."))
    return ok
  }

  fun addCourse(code: String, title: String, programme: String?): Boolean {
    val ok = repository.addCourse(code, title, programme)
    showMessage(if (ok) t("Course saved.", "Kozi imehifadhiwa.") else t("Enter a course code and a name.", "Andika msimbo na jina la kozi."))
    return ok
  }

  fun importCourses(text: String): Boolean {
    val result = repository.importCourses(text)
    showMessage(
      if (result.added > 0) t("${result.added} courses added, ${result.skipped} skipped.", "Kozi ${result.added} zimeongezwa, ${result.skipped} zimerukwa.")
      else t("No course could be read. Use: CODE, Course name, Programme.", "Hakuna kozi iliyosomeka. Tumia: MSIMBO, Jina la kozi, Programu.")
    )
    return result.added > 0
  }

  fun addRoom(name: String): Boolean {
    val ok = repository.addRoom(name)
    showMessage(if (ok) t("Room added.", "Chumba kimeongezwa.") else t("That room already exists or the name is too short.", "Chumba hicho kipo tayari au jina ni fupi mno."))
    return ok
  }

  // ---------- Scheduling ----------

  fun schedulableCourses(): List<Pair<String, String>> = repository.schedulableCourses()
  fun lecturersFor(courseCode: String): List<UserProfile> = repository.lecturersFor(courseCode)
  fun campusLecturers(): List<UserProfile> = repository.campusLecturers()

  /** Saves who teaches the course. Returns the reason in words when it could not be saved, or null when it was. */
  fun setCourseLecturers(courseCode: String, lecturerIds: Set<String>): String? {
    val result = repository.setCourseLecturers(courseCode, lecturerIds)
    val reason = when (result.error) {
      null -> null
      AssignLecturersError.NOT_ALLOWED -> t("Only the campus administrator can assign lecturers.", "Msimamizi wa chuo pekee ndiye anaweza kuweka wahadhiri.")
      AssignLecturersError.UNKNOWN_COURSE -> t("That course is not on this campus.", "Kozi hiyo haipo kwenye chuo hiki.")
      AssignLecturersError.NOT_A_LECTURER -> t("Only approved lecturers of this campus can be assigned.", "Wahadhiri walioidhinishwa wa chuo hiki pekee ndio wanaweza kuwekwa.")
      AssignLecturersError.HAS_CLASSES -> t(
        "${result.names} still has classes in this course. Move or cancel them first.",
        "${result.names} bado ana vipindi katika kozi hii. Vihamishe au vifute kwanza."
      )
    }
    showMessage(reason ?: t("Lecturers saved.", "Wahadhiri wamehifadhiwa."))
    return reason
  }
  fun roomsWithAvailability(day: Int, start: String, end: String): List<RoomOption> =
    repository.roomsWithAvailability(day, start, end)

  /** Saves the class. Returns the reason in words when it could not be saved, or null when it was. */
  fun tryAddSession(courseCode: String, lecturerId: String, day: Int, start: String, end: String, venue: String): String? {
    val error = repository.addSession(courseCode, lecturerId, day, start, end, venue)
    if (error == null) {
      showMessage(t("Class added to the timetable.", "Kipindi kimeongezwa kwenye ratiba."))
      return null
    }
    return when (error) {
      SessionError.NOT_ALLOWED -> t("You can only schedule your own courses.", "Unaweza kupanga kozi zako tu.")
      SessionError.UNKNOWN_COURSE -> t("Choose a subject.", "Chagua somo.")
      SessionError.LECTURER_NOT_FOR_COURSE -> t("Choose a lecturer who teaches this subject.", "Chagua mhadhiri anayefundisha somo hili.")
      SessionError.TIME_INVALID -> t("The end time must be after the start time.", "Muda wa kumaliza lazima uwe baada ya wa kuanza.")
      SessionError.ROOM_REQUIRED -> t("Choose a room.", "Chagua chumba.")
      SessionError.ROOM_BUSY -> t("That room is already used at this time.", "Chumba hicho kinatumika wakati huu.")
      SessionError.LECTURER_BUSY -> t("That lecturer has another class at this time.", "Mhadhiri huyo ana kipindi kingine wakati huu.")
    }
  }

  // ---------- App manager ----------

  /** Creates the campus and its first administrator. Returns the reason in words when it failed, or null. */
  suspend fun createCampus(
    code: String, name: String, adminName: String, adminEmail: String, adminNumber: String, adminPassword: String,
    location: String = "", contactEmail: String = "", contactPhone: String = ""
  ): String? {
    val result = repository.createCampus(code, name, adminName, adminEmail, adminNumber, adminPassword, location, contactEmail, contactPhone)
    if (result.ok) {
      showMessage(t("Campus created.", "Chuo kimeundwa."))
      return null
    }
    return platformErrorText(result)
  }

  private fun platformErrorText(result: PlatformResult): String = when {
    result.authError != null -> authErrorText(result.authError)
    result.platformError == PlatformError.CAMPUS_EXISTS -> t("A campus with that short name already exists.", "Chuo chenye jina hilo fupi kipo tayari.")
    result.platformError == PlatformError.DETAILS_MISSING -> t("Enter a short name (letters and numbers, 2-20) and the full name.", "Andika jina fupi (herufi na namba, 2-20) na jina kamili la chuo.")
    result.platformError == PlatformError.FAILED -> t("The server could not finish this. Check your connection, then try again.", "Seva imeshindwa kukamilisha. Angalia mtandao kisha ujaribu tena.")
    else -> t("Only the app manager can do this.", "Ni msimamizi wa programu pekee anayeweza kufanya hili.")
  }

  fun wipeCampus(universityId: String, scope: WipeScope) {
    viewModelScope.launch {
      val ok = repository.wipeCampus(universityId, scope)
      showMessage(if (ok) t("Deleted.", "Imefutwa.") else t("Could not delete.", "Imeshindikana kufuta."))
    }
  }

  /** Offline (maintenance) mode of the person's own campus: when true the whole campus is paused. */
  val campusPaused = repository.campusPaused
  val myCampus = repository.myCampus

  fun setCampusMaintenance(universityId: String, on: Boolean, note: String) {
    viewModelScope.launch {
      val ok = repository.setCampusMaintenance(universityId, on, note)
      showMessage(
        when {
          !ok -> t("Could not change the campus mode.", "Imeshindikana kubadilisha hali ya chuo.")
          on -> t("Campus is now offline for maintenance.", "Chuo kimewekwa nje ya mtandao kwa matengenezo.")
          else -> t("Campus is back online.", "Chuo kimerudi mtandaoni.")
        }
      )
    }
  }

  /** Returns an error message, or null when the details were saved. */
  suspend fun updateCampusDetails(universityId: String, name: String?, location: String, email: String, phone: String): String? {
    val ok = repository.updateCampusDetails(universityId, name, location, email, phone)
    return if (ok) {
      showMessage(t("Campus details saved.", "Taarifa za chuo zimehifadhiwa."))
      null
    } else t("Could not save the details.", "Imeshindikana kuhifadhi taarifa.")
  }

  /** Returns an error message, or null when the person was added. */
  suspend fun addCampusUser(universityId: String, role: Role, name: String, email: String, number: String, programme: String?, password: String): String? {
    val result = repository.addCampusUser(universityId, role, name, email, number, programme, password)
    if (result.ok) {
      showMessage(t("Person added. They can sign in now.", "Mtu ameongezwa. Anaweza kuingia sasa."))
      return null
    }
    return platformErrorText(result)
  }

  fun deleteCampus(universityId: String) {
    viewModelScope.launch {
      val ok = repository.deleteCampus(universityId)
      showMessage(if (ok) t("Campus deleted.", "Chuo kimefutwa.") else t("Could not delete.", "Imeshindikana kufuta."))
    }
  }

  // ---------- Registry ----------

  /** True/false against the university list, or null when that university has not uploaded one. */

  fun approveUser(userId: String, role: Role, programme: String?, teaches: Set<String>) {
    viewModelScope.launch {
      val ok = repository.approveUser(userId, role, programme, teaches)
      showMessage(if (ok) t("Account approved.", "Akaunti imeidhinishwa.") else t("Could not approve. Check your connection and that your email is verified.", "Imeshindikana kuidhinisha. Angalia mtandao na kuwa barua pepe imethibitishwa."))
    }
  }

  fun rejectUser(userId: String) {
    viewModelScope.launch {
      val ok = repository.rejectUser(userId)
      showMessage(if (ok) t("Account rejected.", "Akaunti imekataliwa.") else t("Could not reject.", "Imeshindikana kukataa."))
    }
  }

  /** Reloads the people waiting for approval (administrators) or the campus totals (app manager). */
  fun refreshAdminData() {
    viewModelScope.launch {
      val u = repository.currentUser.value
      if (u.status != com.willykez.campconnect.data.model.AccountStatus.APPROVED) return@launch
      if (u.isPlatformAdmin) repository.refreshCampuses()
      else repository.syncNow()
    }
  }

  // ---------- My data ----------

  fun exportMyData(): String = repository.exportMyData()

  fun requestDataDeletion() {
    repository.requestDataDeletion()
    showMessage(t("Request sent to the Academic Registry.", "Ombi limetumwa kwa Msajili wa Taaluma."))
  }

  // ---------- People management (administrators) and AI settings (app manager) ----------

  private val people_ = repository.peopleAdmin
  val managedPeople = people_.people
  val managedProgrammes = people_.programmes
  val managedAdmins = people_.admins
  val aiStatus = people_.ai

  /** Reloads the campus people and programmes (campus administrator). */
  fun refreshPeople() {
    viewModelScope.launch {
      val me = repository.currentUser.value
      if (me.role != Role.ADMIN || me.isPlatformAdmin) return@launch
      people_.loadProgrammes(repository.auth.universityUuid(me.universityId))
      people_.loadPeople()
    }
  }

  /** Reloads the administrators of every campus (app manager). */
  fun refreshPlatformAdmins() {
    viewModelScope.launch { people_.loadAdmins() }
  }

  fun refreshAiStatus() {
    viewModelScope.launch { people_.loadAiStatus() }
  }

  fun peopleErrorText(code: String): String = when (code) {
    "number_taken" -> t("That registration or staff number is already used on this campus.", "Namba hiyo tayari inatumika katika chuo hiki.")
    "email_taken" -> t("That email already belongs to another account.", "Barua pepe hiyo tayari ina akaunti nyingine.")
    "weak_password" -> t("Choose a password of at least 8 characters.", "Chagua nenosiri lenye angalau herufi 8.")
    "invalid_email" -> t("That email does not look right.", "Barua pepe hiyo haionekani sahihi.")
    "invalid_name" -> t("Enter the full name.", "Andika jina kamili.")
    "invalid_number" -> t("Enter the registration or staff number.", "Andika namba ya usajili au ya mfanyakazi.")
    "programme_required" -> t("Choose a programme for the student.", "Chagua programu ya mwanafunzi.")
    "unknown_programme" -> t("That programme is not on this campus.", "Programu hiyo haipo kwenye chuo hiki.")
    "invalid_message" -> t("Write a title and a message.", "Andika kichwa na ujumbe.")
    "role_not_allowed" -> t("That role change is not allowed.", "Mabadiliko hayo ya jukumu hayaruhusiwi.")
    "not_allowed" -> t("You are not allowed to do that.", "Huruhusiwi kufanya hivyo.")
    "network" -> t("No connection. Nothing was changed.", "Hakuna mtandao. Hakuna kilichobadilishwa.")
    else -> t("The server could not finish this. Try again.", "Seva imeshindwa kukamilisha. Jaribu tena.")
  }

  /** Runs one action. Returns the reason in words when it failed, or null when it worked. */
  private suspend fun act(okText: String, call: suspend () -> com.willykez.campconnect.data.repository.PeopleResult): String? {
    val r = call()
    return if (r.ok) { showMessage(okText); null } else peopleErrorText(r.code)
  }

  suspend fun editPerson(id: String, name: String, number: String, programmeId: String?, department: String?): String? =
    act(t("Details saved.", "Taarifa zimehifadhiwa.")) { people_.updatePerson(id, name, number, programmeId, department) }

  suspend fun changePersonRole(id: String, role: String, number: String?, programmeId: String?): String? =
    act(t("Role changed.", "Jukumu limebadilishwa.")) { people_.changeRole(id, role, number, programmeId) }

  suspend fun resetPersonPassword(id: String, password: String): String? =
    act(t("Password reset. Share it with the person privately.", "Nenosiri limewekwa upya. Mpe mhusika kwa siri.")) { people_.resetPassword(id, password) }

  suspend fun changePersonLogin(id: String, email: String): String? =
    act(t("Login email changed.", "Barua pepe ya kuingia imebadilishwa.")) { people_.updateLogin(id, email) }

  suspend fun pausePerson(id: String, pause: Boolean): String? =
    act(if (pause) t("Account paused. They cannot sign in.", "Akaunti imesimamishwa. Hawezi kuingia.") else t("Account resumed.", "Akaunti imerudishwa.")) {
      people_.setPaused(id, pause)
    }

  suspend fun deletePerson(id: String): String? =
    act(t("Account deleted.", "Akaunti imefutwa.")) { people_.delete(id) }

  suspend fun setPersonClassRep(id: String, on: Boolean): String? =
    act(if (on) t("Class representative added.", "Mwakilishi wa darasa ameongezwa.") else t("Class representative removed.", "Mwakilishi wa darasa ameondolewa.")) {
      people_.setClassRep(id, on)
    }

  /** Returns an error in words, or null when sent. */
  suspend fun sendToCampusAdmins(universityId: String, title: String, message: String, urgent: Boolean): String? {
    val uuid = repository.auth.universityUuid(universityId) ?: return peopleErrorText("failed")
    val (n, code) = people_.messageCampusAdmins(uuid, title, message, urgent)
    if (n < 0) return peopleErrorText(code)
    showMessage(if (n == 0) t("No administrators to notify on this campus.", "Hakuna wasimamizi wa kuarifu katika chuo hiki.") else t("Sent to $n administrator(s).", "Imetumwa kwa wasimamizi $n."))
    return null
  }

  suspend fun sendToPeople(audience: String, programmeId: String?, title: String, message: String, urgent: Boolean): String? {
    val (n, code) = people_.messagePeople(audience, programmeId, title, message, urgent)
    if (n < 0) return peopleErrorText(code)
    showMessage(if (n == 0) t("Nobody matched, nothing was sent.", "Hakuna aliyelingana, hakuna kilichotumwa.") else t("Sent to $n people.", "Imetumwa kwa watu $n."))
    return null
  }

  suspend fun saveAiSettings(provider: String, model: String, key: String, enabled: Boolean): String? =
    act(t("AI settings saved.", "Mipangilio ya AI imehifadhiwa.")) { people_.saveAi(provider, model, key, enabled) }

  suspend fun removeAiKey(): String? =
    act(t("AI key removed.", "Ufunguo wa AI umeondolewa.")) { people_.clearAiKey() }

  // ---------- Words used in several places ----------

  fun getString(key: String): String {
    val sw = isSwahili
    return when (key) {
      "tab_home" -> if (sw) "Nyumbani" else "Home"
      "tab_timetable" -> if (sw) "Ratiba" else "Timetable"
      "tab_announcements" -> if (sw) "Matangazo" else "Announcements"
      "tab_materials" -> if (sw) "Vifaa" else "Materials"
      "tab_calendar" -> if (sw) "Tarehe" else "Deadlines"
      "tab_submissions" -> if (sw) "Kazi Zilizotumwa" else "Submissions"
      "tab_profile" -> if (sw) "Wasifu" else "Profile"
      "tab_admin" -> if (sw) "Utawala" else "Admin"
      "notifications" -> if (sw) "Arifa" else "Notifications"
      "room_changed" -> if (sw) "Chumba kimebadilishwa" else "Room changed"
      "cancelled" -> if (sw) "Imeghairiwa" else "Cancelled"
      "scheduled" -> if (sw) "Imepangwa" else "Scheduled"
      "greeting" -> if (sw) "Habari" else "Hello"
      "today_classes" -> if (sw) "Masomo ya Leo" else "Today's Classes"
      "latest_announcement" -> if (sw) "Tangazo Lililo Angaziwa" else "Highlighted Announcement"
      "next_deadline" -> if (sw) "Tarehe Inayofuata" else "Next Upcoming Deadline"
      "quick_actions" -> if (sw) "Vitendo vya Haraka" else "Quick Actions"
      "my_assigned_courses" -> if (sw) "Masomo Yangu Niliyopangiwa" else "My Assigned Courses"
      "offline_notice" -> if (sw) "Nje ya mtandao" else "Offline"
      else -> key
    }
  }
}
