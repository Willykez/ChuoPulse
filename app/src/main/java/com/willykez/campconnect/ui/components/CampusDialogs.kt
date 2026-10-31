package com.willykez.campconnect.ui.components

import androidx.compose.ui.text.style.TextOverflow

import com.willykez.campconnect.util.DeadlineTime

import com.willykez.campconnect.data.docs.DocumentKind

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.willykez.campconnect.data.model.Announcement
import com.willykez.campconnect.data.model.AnnouncementPriority
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.data.model.AudienceType
import com.willykez.campconnect.data.model.ClassSession
import com.willykez.campconnect.data.model.ContentApprovalStatus
import com.willykez.campconnect.data.model.DeadlineItem
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.StudyMaterial
import com.willykez.campconnect.data.model.UserProfile
import com.willykez.campconnect.data.repository.AudienceOption
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DangerRedLight
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.WarmAmberLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementDetailDialog(
  announcement: Announcement,
  currentUserRole: Role,
  canModerate: Boolean,
  onDismiss: () -> Unit,
  onPreviewAttachment: (AttachmentFile) -> Unit,
  onEditClick: () -> Unit,
  onDeleteClick: () -> Unit,
  onToggleApproval: (ContentApprovalStatus) -> Unit,
  showReadStats: Boolean = false,
  reminderSent: Boolean = false,
  onRemindClick: () -> Unit = {},
  onRetractClick: () -> Unit = {}
) {
  AppBottomSheet(
    onDismissRequest = onDismiss,
    tag = "announcement_detail_modal"
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        StatusBadge(
          text = announcement.audienceName,
          backgroundColor = DeepBlue.copy(alpha = 0.12f),
          contentColor = DeepBlue
        )

        if (announcement.isRetracted) {
          Spacer(modifier = Modifier.width(6.dp))
          StatusBadge(
            text = "Withdrawn",
            backgroundColor = DangerRedLight,
            contentColor = DangerRed
          )
        }

        if (announcement.approvalStatus == ContentApprovalStatus.PENDING_REVIEW) {
          Spacer(modifier = Modifier.width(6.dp))
          StatusBadge(
            text = "Pending Approval",
            backgroundColor = WarmAmberLight,
            contentColor = Color(0xFFB45309)
          )
        } else if (announcement.approvalStatus == ContentApprovalStatus.DISAPPROVED) {
          Spacer(modifier = Modifier.width(6.dp))
          StatusBadge(
            text = "Disapproved",
            backgroundColor = DangerRedLight,
            contentColor = DangerRed
          )
        }
      }

      IconButton(onClick = onDismiss) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Text(
      text = announcement.title,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Published by ${announcement.authorName} (${announcement.authorRole})" + (announcement.responsibleLecturerName?.let { ", for $it" } ?: "") + " \u2022 ${announcement.publishedAt}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(14.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Spacer(modifier = Modifier.height(14.dp))

    Text(
      text = announcement.body,
      style = MaterialTheme.typography.bodyLarge,
      lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
    )

    // Attachment Card with Built-in Document Preview
    if (announcement.attachment != null) {
      Spacer(modifier = Modifier.height(16.dp))
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Attachment,
              contentDescription = "Attachment",
              tint = DeepBlue
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = announcement.attachment.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "${announcement.attachment.fileSize} • Tap to view document",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Button(
            onClick = { onPreviewAttachment(announcement.attachment) },
            colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
            modifier = Modifier.testTag("btn_view_attached_doc")
          ) {
            Icon(imageVector = Icons.Default.Visibility, contentDescription = "Preview", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("View Doc")
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Delivery and open counts: only for the sender and the registry, never for students
    if (showReadStats) {
      val opened = announcement.readCount.coerceAtMost(announcement.totalStudents)
      val notOpened = (announcement.totalStudents - opened).coerceAtLeast(0)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFFDCFCE7))
          .padding(10.dp)
      ) {
        Text(
          text = "Delivered to ${announcement.totalStudents} students \u00B7 Opened by $opened \u00B7 $notOpened not yet opened",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          color = SuccessGreen,
          modifier = Modifier.testTag("read_stats_text")
        )
        if (notOpened > 0 && !announcement.isRetracted) {
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedButton(
            onClick = onRemindClick,
            enabled = !reminderSent,
            modifier = Modifier.testTag("btn_remind_unread")
          ) {
            Text(if (reminderSent) "Reminder sent" else "Remind the $notOpened")
          }
        }
      }
    }

    // Admin & Uploader Management Row
    if (canModerate) {
      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Admin Approval Toggles
        if (currentUserRole == Role.ADMIN) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (announcement.approvalStatus != ContentApprovalStatus.APPROVED) {
              Button(
                onClick = { onToggleApproval(ContentApprovalStatus.APPROVED) },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                modifier = Modifier.testTag("btn_approve_announcement")
              ) {
                Icon(Icons.Default.ThumbUp, contentDescription = "Approve", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Approve", style = MaterialTheme.typography.labelSmall)
              }
            }

            if (announcement.approvalStatus != ContentApprovalStatus.DISAPPROVED) {
              OutlinedButton(
                onClick = { onToggleApproval(ContentApprovalStatus.DISAPPROVED) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                modifier = Modifier.testTag("btn_disapprove_announcement")
              ) {
                Icon(Icons.Default.ThumbDown, contentDescription = "Disapprove", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Disapprove", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        } else {
          Spacer(modifier = Modifier.width(1.dp))
        }

        // Retract, edit & delete actions for Author or Admin
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!announcement.isRetracted) {
            OutlinedButton(
              onClick = onRetractClick,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
              modifier = Modifier.testTag("btn_retract_announcement")
            ) {
              Text("Retract", style = MaterialTheme.typography.labelSmall)
            }
          }
          IconButton(onClick = onEditClick, modifier = Modifier.testTag("edit_announcement_btn")) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DeepBlue)
          }
          IconButton(onClick = onDeleteClick, modifier = Modifier.testTag("delete_announcement_btn")) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishAnnouncementDialog(
  options: List<AudienceOption>,
  urgentLeft: Int,
  canUseUrgentFor: (AudienceType) -> Boolean,
  onDismiss: () -> Unit,
  lecturersFor: (String) -> List<UserProfile>,
  onPublish: (title: String, body: String, audienceType: AudienceType, audienceName: String, audienceCode: String, priority: AnnouncementPriority, attachment: AttachmentFile?, responsibleLecturerId: String?) -> Boolean,
  onPreviewAttachment: (AttachmentFile) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var responsibleId by remember { mutableStateOf<String?>(null) }
  var pickLecturer by remember { mutableStateOf(false) }
  var selected by remember { mutableStateOf(options.firstOrNull()) }
  var priority by remember { mutableStateOf(AnnouncementPriority.NORMAL) }
  var selectedAttachment by remember { mutableStateOf<AttachmentFile?>(null) }

  val chosen = selected
  val urgentAllowed = chosen != null && canUseUrgentFor(chosen.type) && urgentLeft > 0

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = "Publish Official Notice",
    tag = "publish_announcement_modal",
    footer = {
      SheetButtons(
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        confirmLabel = "Publish Now",
        onConfirm = {
          val target = selected
          if (target != null && title.isNotBlank() && body.isNotBlank()) {
            val sent = onPublish(title, body, target.type, target.name, target.code, priority, selectedAttachment, responsibleId)
            if (sent) onDismiss()
          }
        },
        enabled = chosen != null && title.isNotBlank() && body.isNotBlank(),
        confirmIcon = Icons.Default.Send,
        confirmTag = "submit_publish_btn"
      )
    }
  ) {
    Spacer(modifier = Modifier.height(4.dp))

    if (options.isEmpty()) {
      Text(
        text = "You do not have any class or programme to post to yet. Ask the Academic Registry to assign your courses.",
        style = MaterialTheme.typography.bodyMedium
      )
    } else {
      Text(
        text = "Send to (only these people will see it):",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
      ) {
        options.forEach { option ->
          AudienceChip(
            label = option.label,
            isSelected = chosen == option,
            onClick = {
              selected = option
              responsibleId = null
              if (priority == AnnouncementPriority.URGENT && !canUseUrgentFor(option.type)) {
                priority = AnnouncementPriority.NORMAL
              }
            }
          )
        }
      }

      // A notice for a course can be on behalf of the lecturer responsible for it.
      val forLecturers = if (chosen?.type == AudienceType.COURSE) lecturersFor(chosen.code) else emptyList()
      if (forLecturers.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        PickerField(
          label = "On behalf of",
          value = forLecturers.firstOrNull { it.id == responsibleId }?.fullName,
          placeholder = "Me",
          onClick = { pickLecturer = true },
          tag = "announcement_responsible"
        )
      }
      if (pickLecturer) {
        PickerSheet(
          title = "On behalf of",
          options = listOf(PickerItem("", "Me")) + forLecturers.map { PickerItem(it.id, it.fullName, it.cohort) },
          selectedId = responsibleId ?: "",
          onSelect = { responsibleId = it.ifBlank { null } },
          onDismiss = { pickLecturer = false }
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Announcement Title") },
        placeholder = { Text("e.g. Midterm Test Rescheduled") },
        modifier = Modifier.fillMaxWidth().testTag("announcement_title_input"),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
        value = body,
        onValueChange = { body = it },
        label = { Text("Message Body") },
        placeholder = { Text("Type official announcement message...") },
        modifier = Modifier
          .fillMaxWidth()
          .height(100.dp)
          .testTag("announcement_body_input")
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Integrated File Picker for Announcement Attachment
      Text(
        text = "Attach Official PDF Document / Notice Photo:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(4.dp))
      CampusFilePicker(
        selectedFile = selectedAttachment,
        onFileSelected = { selectedAttachment = it },
        onClearFile = { selectedAttachment = null },
        onPreviewFile = onPreviewAttachment,
        title = "Select Circular or Circular Scan",
        allowedTypesDescription = "PDF, JPG, PNG attachments supported"
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Priority:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AudienceChip(
          label = "Normal",
          isSelected = priority == AnnouncementPriority.NORMAL,
          onClick = { priority = AnnouncementPriority.NORMAL }
        )
        AudienceChip(
          label = "Academic",
          isSelected = priority == AnnouncementPriority.ACADEMIC,
          onClick = { priority = AnnouncementPriority.ACADEMIC }
        )
        if (urgentAllowed) {
          AudienceChip(
            label = "Urgent",
            isSelected = priority == AnnouncementPriority.URGENT,
            onClick = { priority = AnnouncementPriority.URGENT }
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = when {
          chosen != null && !canUseUrgentFor(chosen.type) ->
            "Urgent is only for the registry, or for your own course."
          urgentLeft <= 0 -> "You have used today's Urgent notices."
          urgentLeft < 100 -> "Urgent notices left today: $urgentLeft"
          else -> "Urgent: no daily limit for the registry."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAnnouncementDialog(
  announcement: Announcement,
  onDismiss: () -> Unit,
  onSave: (newTitle: String, newBody: String, newPriority: AnnouncementPriority) -> Unit
) {
  var title by remember { mutableStateOf(announcement.title) }
  var body by remember { mutableStateOf(announcement.body) }
  var isUrgent by remember { mutableStateOf(announcement.priority == AnnouncementPriority.URGENT) }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = "Edit Announcement",
    tag = "edit_announcement_modal",
    footer = {
      SheetButtons(
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        confirmLabel = "Save Changes",
        onConfirm = {
          if (title.isNotBlank() && body.isNotBlank()) {
            val priority = if (isUrgent) AnnouncementPriority.URGENT else AnnouncementPriority.NORMAL
            onSave(title, body, priority)
            onDismiss()
          }
        },
        enabled = title.isNotBlank() && body.isNotBlank(),
        confirmTag = "save_announcement_btn"
      )
    }
  ) {
    Spacer(modifier = Modifier.height(4.dp))

    OutlinedTextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Title") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = body,
      onValueChange = { body = it },
      label = { Text("Message Body") },
      modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
    )
  }
}

@Composable
fun AudienceChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeVenueDialog(
  session: ClassSession,
  onDismiss: () -> Unit,
  onConfirm: (newVenue: String, reason: String) -> Unit
) {
  var newVenue by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = "Relocate Lecture Venue",
    tag = "change_venue_modal",
    footer = {
      SheetButtons(
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        confirmLabel = "Update & Notify Class",
        onConfirm = {
          if (newVenue.isNotBlank()) {
            onConfirm(newVenue, reason.ifBlank { "Relocated by lecturer" })
            onDismiss()
          }
        },
        enabled = newVenue.isNotBlank(),
        confirmColor = WarmAmber,
        confirmIcon = Icons.Default.EditLocation,
        confirmTag = "confirm_venue_change_btn"
      )
    }
  ) {
    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = "${session.courseCode}: ${session.courseTitle}",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = DeepBlue
    )
    Text(
      text = "Current Venue: ${session.venue}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(14.dp))

    OutlinedTextField(
      value = newVenue,
      onValueChange = { newVenue = it },
      label = { Text("New Venue / Hall") },
      placeholder = { Text("e.g. Computer Lab 4") },
      modifier = Modifier.fillMaxWidth().testTag("new_venue_input"),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = reason,
      onValueChange = { reason = it },
      label = { Text("Reason for Change") },
      placeholder = { Text("e.g. Projector maintenance in LTB") },
      modifier = Modifier.fillMaxWidth().testTag("venue_reason_input")
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancelSessionDialog(
  session: ClassSession,
  onDismiss: () -> Unit,
  onConfirm: (reason: String) -> Unit
) {
  var reason by remember { mutableStateOf("") }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = "Cancel Lecture Session",
    titleColor = DangerRed,
    tag = "cancel_session_modal",
    footer = {
      SheetButtons(
        dismissLabel = "Keep Scheduled",
        onDismiss = onDismiss,
        confirmLabel = "Confirm Cancellation",
        onConfirm = {
          onConfirm(reason.ifBlank { "Class cancelled by lecturer" })
          onDismiss()
        },
        confirmColor = DangerRed,
        confirmIcon = Icons.Default.EventBusy,
        confirmTag = "confirm_cancel_session_btn"
      )
    }
  ) {
    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = "Are you sure you want to cancel ${session.courseCode}?",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = "Enrolled students will receive a push notification immediately.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(14.dp))

    OutlinedTextField(
      value = reason,
      onValueChange = { reason = it },
      label = { Text("Reason for Cancellation") },
      placeholder = { Text("e.g. Faculty meeting, makeup on Saturday") },
      modifier = Modifier.fillMaxWidth().testTag("cancel_reason_input")
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadMaterialDialog(
  onDismiss: () -> Unit,
  onUpload: (title: String, courseCode: String, courseTitle: String, description: String, attachment: AttachmentFile) -> Boolean,
  onPreviewAttachment: (AttachmentFile) -> Unit,
  defaultCourseCode: String = "CS210"
) {
  var title by remember { mutableStateOf("") }
  var courseCode by remember { mutableStateOf(defaultCourseCode) }
  var description by remember { mutableStateOf("") }
  var selectedFile by remember { mutableStateOf<AttachmentFile?>(null) }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = "Upload Course Material",
    tag = "upload_material_modal",
    footer = {
      SheetButtons(
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        confirmLabel = "Save & Publish",
        onConfirm = {
          val file = selectedFile
          if (file != null && title.isNotBlank() && courseCode.isNotBlank()) {
            val courseTitle = if (courseCode.contains("CS210")) "Data Structures & Algorithms" else "Computer Science"
            val ok = onUpload(title, courseCode.trim().uppercase(), courseTitle, description, file)
            if (ok) onDismiss()
          }
        },
        enabled = selectedFile != null && title.isNotBlank() && courseCode.isNotBlank(),
        confirmIcon = Icons.Default.Upload,
        confirmTag = "submit_upload_btn"
      )
    }
  ) {
    Spacer(modifier = Modifier.height(4.dp))

    OutlinedTextField(
      value = courseCode,
      onValueChange = { courseCode = it },
      label = { Text("Course Code") },
      modifier = Modifier.fillMaxWidth().testTag("material_course_input"),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Document / Handout Title") },
      placeholder = { Text("e.g. Chapter 4: Dynamic Programming Notes") },
      modifier = Modifier.fillMaxWidth().testTag("material_title_input"),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = description,
      onValueChange = { description = it },
      label = { Text("Short Description") },
      placeholder = { Text("Includes pseudocode and practice examples") },
      modifier = Modifier.fillMaxWidth().testTag("material_desc_input")
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Integrated File Picker
    Text(
      text = "Select Document File (PDF or Image):",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    CampusFilePicker(
      selectedFile = selectedFile,
      onFileSelected = { selectedFile = it },
      onClearFile = { selectedFile = null },
      onPreviewFile = onPreviewAttachment,
      title = "Select Lecture Slide or Notes",
      allowedTypesDescription = "PDF, JPG, PNG files (Max 25 MB)"
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeadlineDialog(
  onDismiss: () -> Unit,
  onAdd: (courseCode: String, courseTitle: String, title: String, instructions: String, dueDateTime: String, acceptedFormats: String, attachment: AttachmentFile?) -> Boolean,
  courses: List<Pair<String, String>> = emptyList(),
  defaultCourseCode: String = "",
  onPreviewAttachment: (AttachmentFile) -> Unit = {}
) {
  var courseCode by remember { mutableStateOf(defaultCourseCode.takeIf { c -> courses.any { it.first == c } } ?: courses.firstOrNull()?.first ?: defaultCourseCode) }
  var pickingCourse by remember { mutableStateOf(false) }
  var title by remember { mutableStateOf("") }
  var instructions by remember { mutableStateOf("") }
  var dueDateTime by remember { mutableStateOf(DeadlineTime.format(DeadlineTime.tomorrowEvening())) }
  var acceptedFormats by remember { mutableStateOf("PDF or Images (Max 15 MB)") }
  var attachment by remember { mutableStateOf<AttachmentFile?>(null) }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = "Create Academic Deadline",
    tag = "add_deadline_modal",
    footer = {
      SheetButtons(
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        confirmLabel = "Create Deadline",
        onConfirm = {
          if (title.isNotBlank() && courseCode.isNotBlank()) {
            val courseTitle = courses.firstOrNull { it.first == courseCode }?.second ?: courseCode
            val ok = onAdd(courseCode.trim().uppercase(), courseTitle, title, instructions, dueDateTime, acceptedFormats, attachment)
            if (ok) onDismiss()
          }
        },
        enabled = title.isNotBlank() && courseCode.isNotBlank(),
        confirmTag = "submit_deadline_btn"
      )
    }
  ) {
    Spacer(modifier = Modifier.height(4.dp))

    if (courses.isNotEmpty()) {
      PickerField(
        label = "Course",
        value = courses.firstOrNull { it.first == courseCode }?.let { "${it.first}  ${it.second}" },
        placeholder = "Choose a course",
        onClick = { pickingCourse = true },
        tag = "deadline_course_input"
      )
    } else {
      OutlinedTextField(
        value = courseCode,
        onValueChange = { courseCode = it },
        label = { Text("Course Code") },
        modifier = Modifier.fillMaxWidth().testTag("deadline_course_input"),
        singleLine = true
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Task / Assignment Title") },
      placeholder = { Text("e.g. Programming Assignment 3") },
      modifier = Modifier.fillMaxWidth().testTag("deadline_title_input"),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(10.dp))

    DueDateTimeField(
      valueText = dueDateTime,
      onValueChange = { dueDateTime = it },
      label = "Due date & time (EAT)"
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = acceptedFormats,
      onValueChange = { acceptedFormats = it },
      label = { Text("Accepted submission formats") },
      placeholder = { Text("e.g. PDF, Word or images (Max 15 MB)") },
      modifier = Modifier.fillMaxWidth().testTag("deadline_formats_input"),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
      value = instructions,
      onValueChange = { instructions = it },
      label = { Text("Submission Instructions") },
      placeholder = { Text("Submit zip or PDF report with test cases") },
      modifier = Modifier.fillMaxWidth().testTag("deadline_instructions_input")
    )

    Spacer(modifier = Modifier.height(12.dp))

    CampusFilePicker(
      selectedFile = attachment,
      onFileSelected = { attachment = it },
      onClearFile = { attachment = null },
      onPreviewFile = onPreviewAttachment,
      title = "Attach the assignment (optional)",
      allowedTypesDescription = "Question paper or brief: PDF, Word, PowerPoint, photo"
    )
    Spacer(modifier = Modifier.height(8.dp))
  }

  if (pickingCourse) {
    PickerSheet(
      title = "Course",
      options = courses.map { PickerItem(it.first, "${it.first}  ${it.second}") },
      selectedId = courseCode,
      onSelect = { courseCode = it },
      onDismiss = { pickingCourse = false }
    )
  }
}

/** Edit or reschedule a deadline. Students are told when the date changes. */
@Composable
fun EditDeadlineSheet(
  deadline: DeadlineItem,
  onDismiss: () -> Unit,
  onSave: (title: String, instructions: String, dueDateTime: String, acceptedFormats: String, attachment: AttachmentFile?, clearAttachment: Boolean) -> Unit,
  onPreviewAttachment: (AttachmentFile) -> Unit = {}
) {
  var title by remember(deadline.id) { mutableStateOf(deadline.title) }
  var instructions by remember(deadline.id) { mutableStateOf(deadline.instructions) }
  var dueDateTime by remember(deadline.id) {
    mutableStateOf(DeadlineTime.parse(deadline.dueDateTime)?.let { DeadlineTime.format(it) } ?: DeadlineTime.format(DeadlineTime.tomorrowEvening()))
  }
  var acceptedFormats by remember(deadline.id) { mutableStateOf(deadline.acceptedFormats) }
  var newAttachment by remember(deadline.id) { mutableStateOf<AttachmentFile?>(null) }
  var removeExisting by remember(deadline.id) { mutableStateOf(false) }
  val existing = deadline.attachment.takeIf { !removeExisting && newAttachment == null }
  val dateChanged = DeadlineTime.parse(deadline.dueDateTime) != DeadlineTime.parse(dueDateTime)

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = "Edit deadline",
    subtitle = "${deadline.courseCode} \u2022 ${deadline.courseTitle}",
    tag = "edit_deadline_modal",
    footer = {
      SheetButtons(
        dismissLabel = "Cancel",
        onDismiss = onDismiss,
        confirmLabel = if (dateChanged) "Save & notify students" else "Save changes",
        onConfirm = {
          if (title.isNotBlank()) {
            onSave(title.trim(), instructions, dueDateTime, acceptedFormats, newAttachment, removeExisting && newAttachment == null)
            onDismiss()
          }
        },
        enabled = title.isNotBlank(),
        confirmTag = "save_deadline_btn"
      )
    }
  ) {
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedTextField(
      value = title,
      onValueChange = { title = it },
      label = { Text("Task / Assignment Title") },
      modifier = Modifier.fillMaxWidth().testTag("edit_deadline_title_input"),
      singleLine = true
    )
    Spacer(modifier = Modifier.height(10.dp))
    DueDateTimeField(
      valueText = dueDateTime,
      onValueChange = { dueDateTime = it },
      label = "Due date & time (EAT)",
      tag = "edit_deadline_due_input"
    )
    if (dateChanged) {
      Text(
        "Students in ${deadline.courseCode} will get a notification about the new time.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
      )
    }
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
      value = acceptedFormats,
      onValueChange = { acceptedFormats = it },
      label = { Text("Accepted submission formats") },
      modifier = Modifier.fillMaxWidth().testTag("edit_deadline_formats_input"),
      singleLine = true
    )
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
      value = instructions,
      onValueChange = { instructions = it },
      label = { Text("Submission Instructions") },
      modifier = Modifier.fillMaxWidth().testTag("edit_deadline_instructions_input")
    )
    Spacer(modifier = Modifier.height(12.dp))
    if (existing != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
          .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        FileKindBadge(DocumentKind.of(existing.fileName, existing.mimeType), size = 36.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
          Text(existing.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("Current assignment file", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ActionButton(label = "View", icon = Icons.Default.Visibility, onClick = { onPreviewAttachment(existing) })
        Spacer(Modifier.width(8.dp))
        ActionButton(label = "Remove", icon = Icons.Default.Delete, onClick = { removeExisting = true }, color = DangerRed)
      }
    } else {
      CampusFilePicker(
        selectedFile = newAttachment,
        onFileSelected = { newAttachment = it },
        onClearFile = { newAttachment = null },
        onPreviewFile = onPreviewAttachment,
        title = if (deadline.attachment != null) "Replace the assignment file" else "Attach the assignment (optional)",
        allowedTypesDescription = "Question paper or brief: PDF, Word, PowerPoint, photo"
      )
    }
    Spacer(modifier = Modifier.height(8.dp))
  }
}
