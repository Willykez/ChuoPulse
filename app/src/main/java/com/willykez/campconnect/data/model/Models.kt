package com.willykez.campconnect.data.model

enum class Role {
  STUDENT,
  LECTURER,
  ADMIN
}

enum class AccountStatus {
  APPROVED,
  PENDING,
  REJECTED
}

enum class ContentApprovalStatus {
  APPROVED,
  PENDING_REVIEW,
  DISAPPROVED
}

enum class SessionStatus {
  SCHEDULED,
  CHANGED,
  CANCELLED
}

enum class AudienceType {
  CAMPUS,
  PROGRAMME,
  COURSE
}

enum class AnnouncementPriority {
  NORMAL,
  URGENT,
  ACADEMIC
}

enum class NotificationType {
  TIMETABLE_CHANGE,
  ANNOUNCEMENT,
  DEADLINE_ALERT,
  APPROVAL,
  SUBMISSION_RECEIVED,
  GRADE_POSTED
}

enum class AppLanguage {
  ENGLISH,
  SWAHILI
}

data class AttachmentFile(
  val uriString: String? = null,
  val fileName: String,
  val fileSize: String,
  val mimeType: String = "application/pdf",
  val isImage: Boolean = false,
  val previewText: String? = null
)

/** A campus that uses the app. Each university has its own people, data and administrators. */
data class University(
  val id: String,
  val name: String,
  val shortName: String,
  // Details the app manager or the campus administrator can edit.
  val location: String = "",
  val contactEmail: String = "",
  val contactPhone: String = "",
  // Campus offline mode: while true, everyone at this campus is paused. Only the app manager lifts it.
  val underMaintenance: Boolean = false,
  val maintenanceNote: String = ""
)

data class UserProfile(
  val id: String,
  val fullName: String,
  val email: String,
  val role: Role,
  val status: AccountStatus,
  // Which university this person belongs to. Everything they see and do stays inside it.
  val universityId: String = "cuom",
  val studentNumber: String? = null,
  val staffNumber: String? = null,
  val programme: String? = null,
  val cohort: String? = null,
  // Role the person asked for at sign-up. This is only a hint for the registry:
  // `role` is changed solely by an administrator approving the account.
  val requestedRole: Role = Role.STUDENT,
  // The app manager: runs the whole app and every campus, but sees none of a campus's own data.
  val isPlatformAdmin: Boolean = false,
  // A class representative is an approved student who may post for their own programme's courses:
  // notices, materials, assignments and timetable changes (never quizzes or other students' work).
  val isClassRep: Boolean = false,
  // Courses a student is enrolled in (base codes such as "CS210").
  val enrolledCourses: Set<String> = emptySet(),
  // Courses a lecturer teaches (base codes such as "CS210").
  val teachesCourses: Set<String> = emptySet(),
  val avatarInitials: String = fullName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
)

data class ClassSession(
  val id: String,
  val courseCode: String,
  val courseTitle: String,
  val lecturerId: String,
  val lecturerName: String,
  val venue: String,
  val originalVenue: String? = null,
  val dayOfWeek: Int, // 1 = Mon, ..., 6 = Sat
  val startTime: String,
  val endTime: String,
  val status: SessionStatus = SessionStatus.SCHEDULED,
  val changeReason: String? = null,
  val programme: String = "BSc Computer Science",
  val cohort: String = "Year 2",
  val approvalStatus: ContentApprovalStatus = ContentApprovalStatus.APPROVED
)

data class Announcement(
  val id: String,
  val authorId: String,
  val authorName: String,
  val authorRole: String,
  val title: String,
  val body: String,
  val audienceType: AudienceType,
  val audienceName: String,
  // COURSE -> base course code, PROGRAMME -> programme name, CAMPUS -> ""
  val audienceCode: String = "",
  // The lecturer the notice is on behalf of, when someone else posts it (an administrator, a class representative).
  val responsibleLecturerId: String? = null,
  val responsibleLecturerName: String? = null,
  val publishedAt: String,
  val priority: AnnouncementPriority = AnnouncementPriority.NORMAL,
  val readCount: Int = 0,
  val totalStudents: Int = 54,
  val isRead: Boolean = false,
  val attachment: AttachmentFile? = null,
  val isRetracted: Boolean = false,
  val approvalStatus: ContentApprovalStatus = ContentApprovalStatus.APPROVED
)

data class StudyMaterial(
  val id: String,
  val courseCode: String,
  val courseTitle: String,
  val title: String,
  val uploaderId: String,
  val uploadedBy: String,
  val uploadedAt: String,
  val attachment: AttachmentFile,
  val isDownloaded: Boolean = false,
  val description: String = "",
  val approvalStatus: ContentApprovalStatus = ContentApprovalStatus.APPROVED
)

data class DeadlineItem(
  val id: String,
  val courseCode: String,
  val courseTitle: String,
  val lecturerId: String,
  val lecturerName: String,
  val title: String,
  val instructions: String,
  val dueDateTime: String,
  val urgencyText: String,
  val acceptedFormats: String = "PDF or Images (Max 15 MB)",
  val isCompleted: Boolean = false,
  val submittedCount: Int = 0,
  val totalStudents: Int = 54,
  val approvalStatus: ContentApprovalStatus = ContentApprovalStatus.APPROVED,
  /** The assignment brief or question paper the lecturer attached, if any. */
  val attachment: AttachmentFile? = null
)

data class StudentSubmission(
  val id: String,
  val deadlineId: String,
  val courseCode: String,
  val assignmentTitle: String,
  val studentId: String,
  val studentName: String,
  val studentNumber: String,
  val notes: String = "",
  val attachment: AttachmentFile,
  val submittedAt: String,
  val grade: String? = null,
  val feedback: String? = null,
  val approvalStatus: ContentApprovalStatus = ContentApprovalStatus.APPROVED
)

data class NotificationItem(
  val id: String,
  // Owner. Notifications are personal: only this user ever sees them.
  val userId: String,
  val title: String,
  val message: String,
  val type: NotificationType,
  val timestamp: String,
  val isRead: Boolean = false
)

data class AuditLog(
  val id: String,
  val universityId: String = "cuom",
  val actor: String,
  val action: String,
  val details: String,
  val timestamp: String
)
