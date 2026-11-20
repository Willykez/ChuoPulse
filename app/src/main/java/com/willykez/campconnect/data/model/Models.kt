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

data class University(
  val id: String,
  val name: String,
  val shortName: String,
  val location: String = "",
  val contactEmail: String = "",
  val contactPhone: String = "",
  val underMaintenance: Boolean = false,
  val maintenanceNote: String = ""
)

data class UserProfile(
  val id: String,
  val fullName: String,
  val email: String,
  val role: Role,
  val status: AccountStatus,
  val universityId: String = "cuom",
  val studentNumber: String? = null,
  val staffNumber: String? = null,
  val programme: String? = null,
  val cohort: String? = null,
  val requestedRole: Role = Role.STUDENT,
  val isPlatformAdmin: Boolean = false,
  val isClassRep: Boolean = false,
  val enrolledCourses: Set<String> = emptySet(),
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
  val dayOfWeek: Int,
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
  val audienceCode: String = "",
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
  val approvalStatus: ContentApprovalStatus = ContentApprovalStatus.APPROVED,
  val isLate: Boolean = false
)

data class NotificationItem(
  val id: String,
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
