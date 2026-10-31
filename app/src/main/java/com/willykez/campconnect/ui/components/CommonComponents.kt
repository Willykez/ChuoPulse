package com.willykez.campconnect.ui.components

import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Visibility

import com.willykez.campconnect.data.docs.DocumentKind

import com.willykez.campconnect.util.DeadlineTime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.campconnect.data.model.Announcement
import com.willykez.campconnect.data.model.AnnouncementPriority
import com.willykez.campconnect.data.model.AudienceType
import com.willykez.campconnect.data.model.ClassSession
import com.willykez.campconnect.data.model.ContentApprovalStatus
import com.willykez.campconnect.data.model.DeadlineItem
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.SessionStatus
import com.willykez.campconnect.ui.util.formatClock
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DangerRedLight
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.SuccessGreenLight
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.WarmAmberLight

@Composable
fun StatusBadge(
  text: String,
  backgroundColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(backgroundColor)
      .padding(horizontal = 8.dp, vertical = 3.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = contentColor,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
fun OfflineBanner(
  isOffline: Boolean,
  statusText: String,
  onSyncClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (!isOffline) {
    // Online and fresh: a single quiet line instead of a card.
    Text(
      text = statusText,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp).testTag("sync_status_text")
    )
    return
  }
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isOffline) WarmAmberLight else MaterialTheme.colorScheme.surfaceVariant
    ),
    modifier = modifier
      .fillMaxWidth()
      .testTag("offline_banner_card")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.Sync,
          contentDescription = "Sync Status",
          tint = if (isOffline) WarmAmber else DeepBlue,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isOffline) Color(0xFF78350F) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("sync_status_text")
          )
        }
      }

      ActionButton(
        label = if (isOffline) "Go online" else "Sync",
        icon = Icons.Default.Sync,
        onClick = onSyncClick,
        color = if (isOffline) WarmAmber else DeepBlue,
        filled = true,
        tag = "sync_button"
      )
    }
  }
}

@Composable
fun SessionCard(
  session: ClassSession,
  currentUserRole: Role,
  is24Hour: Boolean = true,
  canModerate: Boolean = false,
  onEditClick: (() -> Unit)? = null,
  onCancelClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val isChanged = session.status == SessionStatus.CHANGED
  val isCancelled = session.status == SessionStatus.CANCELLED

  val borderColor = when (session.status) {
    SessionStatus.CHANGED -> WarmAmber
    SessionStatus.CANCELLED -> DangerRed
    SessionStatus.SCHEDULED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
  }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = if (isChanged || isCancelled) 1.5.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(18.dp)
      )
      .testTag("session_card_${session.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(DeepBlue)
              .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = session.courseCode,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.labelSmall
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = "Time",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "${formatClock(session.startTime, is24Hour)} - ${formatClock(session.endTime, is24Hour)}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Medium
            )
          }
        }

        // Status Badge
        when (session.status) {
          SessionStatus.CHANGED -> {
            StatusBadge(
              text = "Room changed",
              backgroundColor = WarmAmberLight,
              contentColor = Color(0xFFB45309)
            )
          }
          SessionStatus.CANCELLED -> {
            StatusBadge(
              text = "Cancelled",
              backgroundColor = DangerRedLight,
              contentColor = DangerRed
            )
          }
          SessionStatus.SCHEDULED -> {
            StatusBadge(
              text = "Confirmed",
              backgroundColor = SuccessGreenLight,
              contentColor = SuccessGreen
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = session.courseTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textDecoration = if (isCancelled) TextDecoration.LineThrough else TextDecoration.None,
        color = if (isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Venue Row
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.LocationOn,
          contentDescription = "Venue",
          modifier = Modifier.size(16.dp),
          tint = if (isChanged) WarmAmber else DeepBlue
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = session.venue,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = if (isChanged) WarmAmber else MaterialTheme.colorScheme.onSurface
        )
      }

      // Change or cancellation note
      if (!session.changeReason.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCancelled) DangerRedLight.copy(alpha = 0.5f) else WarmAmberLight.copy(alpha = 0.5f))
            .padding(8.dp)
        ) {
          Row(verticalAlignment = Alignment.Top) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Notice",
              tint = if (isCancelled) DangerRed else WarmAmber,
              modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = session.changeReason,
              style = MaterialTheme.typography.bodySmall,
              color = if (isCancelled) Color(0xFF991B1B) else Color(0xFF78350F)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Lecturer
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Person,
          contentDescription = "Lecturer",
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = session.lecturerName,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Controls for Admin or Assigned Lecturer: full-size buttons on their own row
      if (currentUserRole == Role.ADMIN || canModerate) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (onEditClick != null) {
            ActionButton(
              label = "Relocate",
              icon = Icons.Default.EditLocationAlt,
              onClick = onEditClick,
              modifier = Modifier.weight(1f),
              tag = "change_room_${session.id}"
            )
          }
          if (!isCancelled && onCancelClick != null) {
            ActionButton(
              label = "Cancel",
              icon = Icons.Default.EventBusy,
              onClick = onCancelClick,
              color = DangerRed,
              modifier = Modifier.weight(1f),
              tag = "cancel_session_${session.id}"
            )
          }
          if (onDeleteClick != null && currentUserRole == Role.ADMIN) {
            SoftIconButton(
              icon = Icons.Default.Delete,
              contentDescription = "Delete",
              onClick = onDeleteClick,
              color = DangerRed,
              tag = "delete_session_${session.id}"
            )
          }
        }
      }
    }
  }
}

@Composable
fun AnnouncementCard(
  announcement: Announcement,
  onClick: () -> Unit,
  showReadCount: Boolean = false,
  modifier: Modifier = Modifier
) {
  val isUrgent = announcement.priority == AnnouncementPriority.URGENT

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .border(
        width = 1.dp,
        color = if (isUrgent) DangerRed.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        shape = RoundedCornerShape(18.dp)
      )
      .testTag("announcement_card_${announcement.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Audience badge
          val audienceBg = when (announcement.audienceType) {
            AudienceType.CAMPUS -> DeepBlue.copy(alpha = 0.15f)
            AudienceType.PROGRAMME -> WarmAmber.copy(alpha = 0.15f)
            AudienceType.COURSE -> SuccessGreen.copy(alpha = 0.15f)
          }
          val audienceText = when (announcement.audienceType) {
            AudienceType.CAMPUS -> DeepBlue
            AudienceType.PROGRAMME -> Color(0xFFB45309)
            AudienceType.COURSE -> SuccessGreen
          }

          StatusBadge(
            text = announcement.audienceName,
            backgroundColor = audienceBg,
            contentColor = audienceText
          )

          Spacer(modifier = Modifier.width(6.dp))
          when (announcement.priority) {
            AnnouncementPriority.URGENT -> StatusBadge(
              text = "URGENT",
              backgroundColor = DangerRedLight,
              contentColor = DangerRed
            )
            AnnouncementPriority.ACADEMIC -> StatusBadge(
              text = "ACADEMIC",
              backgroundColor = DeepBlue.copy(alpha = 0.12f),
              contentColor = DeepBlue
            )
            AnnouncementPriority.NORMAL -> Unit // ordinary notices carry no badge: only Urgent and Academic stand out
          }

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
              text = "Pending Review",
              backgroundColor = WarmAmberLight,
              contentColor = Color(0xFFB45309)
            )
          }
        }

        // Unread badge or read count
        if (!announcement.isRead) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(DeepBlue)
          )
        } else if (showReadCount) {
          // Counts are for the sender and the registry. A student never sees who read what.
          Text(
            text = "${announcement.readCount} read",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = announcement.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = announcement.body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )

      // Attachment preview
      if (announcement.attachment != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Attachment,
            contentDescription = "Attachment",
            tint = DeepBlue,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${announcement.attachment.fileName} (${announcement.attachment.fileSize})",
            style = MaterialTheme.typography.labelSmall,
            color = DeepBlue,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = announcement.authorName + (announcement.responsibleLecturerName?.let { " \u2022 for $it" } ?: ""),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Medium
        )

        Text(
          text = announcement.publishedAt,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun DeadlineCard(
  deadline: DeadlineItem,
  onToggleComplete: () -> Unit,
  onSubmitClick: (() -> Unit)? = null,
  onEditClick: (() -> Unit)? = null,
  onDeleteClick: (() -> Unit)? = null,
  canModerate: Boolean = false,
  onAttachmentClick: (() -> Unit)? = null,
  onViewMySubmission: (() -> Unit)? = null,
  swahili: Boolean = false,
  modifier: Modifier = Modifier
) {
  val isCompleted = deadline.isCompleted
  // How long is left is worked out from the due date, so it never goes stale.
  val urgency = DeadlineTime.urgency(deadline.dueDateTime, deadline.urgencyText, swahili)
  val overdue = !isCompleted && DeadlineTime.isOverdue(deadline.dueDateTime)

  // Ticking fades the colours instead of snapping them.
  val tickFill by animateColorAsState(
    if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.surfaceVariant, tween(220), label = "tick_fill"
  )
  val tickEdge by animateColorAsState(
    if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.outline, tween(220), label = "tick_edge"
  )
  val cardEdge by animateColorAsState(
    if (isCompleted) SuccessGreen.copy(alpha = 0.3f) else WarmAmber.copy(alpha = 0.4f), tween(220), label = "card_edge"
  )

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = cardEdge,
        shape = RoundedCornerShape(18.dp)
      )
      .testTag("deadline_card_${deadline.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
      ) {
        // Checkbox
        Box(
          modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tickFill)
            .border(
              width = 1.5.dp,
              color = tickEdge,
              shape = RoundedCornerShape(8.dp)
            )
            .clickable { onToggleComplete() }
            .testTag("toggle_complete_${deadline.id}"),
          contentAlignment = Alignment.Center
        ) {
          if (isCompleted) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Completed",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = deadline.courseCode,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = DeepBlue
            )

            StatusBadge(
              text = if (isCompleted) (if (swahili) "Imewasilishwa" else "Submitted/Done") else urgency,
              backgroundColor = when {
                isCompleted -> SuccessGreenLight
                overdue -> DangerRedLight
                else -> WarmAmberLight
              },
              contentColor = when {
                isCompleted -> SuccessGreen
                overdue -> DangerRed
                else -> Color(0xFFB45309)
              }
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = deadline.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = deadline.instructions,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )

          val brief = deadline.attachment
          if (brief != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .then(if (onAttachmentClick != null) Modifier.clickable { onAttachmentClick() } else Modifier)
                .padding(8.dp)
                .testTag("deadline_brief_${deadline.id}"),
              verticalAlignment = Alignment.CenterVertically
            ) {
              FileKindBadge(DocumentKind.of(brief.fileName, brief.mimeType), size = 30.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = brief.fileName,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = if (swahili) "Kazi ya kufanya \u2022 gusa kufungua" else "Assignment \u2022 tap to open",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Lecturer Preferences format note
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Preferences: ${deadline.acceptedFormats}",
              style = MaterialTheme.typography.labelSmall,
              color = DeepBlue,
              fontWeight = FontWeight.Medium
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Due",
                tint = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else if (overdue) DangerRed else WarmAmber,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = deadline.dueDateTime,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else if (overdue) DangerRed else WarmAmber
              )
            }

            // Submit Coursework button
            if (onSubmitClick != null) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = DeepBlue,
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { onSubmitClick() }
                  .padding(horizontal = 10.dp, vertical = 6.dp)
                  .testTag("submit_work_btn_${deadline.id}")
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.UploadFile, contentDescription = "Submit", tint = Color.White, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isCompleted) "Resubmit" else "Submit Work",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }

      // A student can look back at what they handed in.
      if (onViewMySubmission != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = SuccessGreenLight,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onViewMySubmission() }
            .testTag("view_my_submission_${deadline.id}")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Visibility, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (swahili) "Angalia kazi yangu iliyowasilishwa" else "View my submitted work",
              color = SuccessGreen,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // Moderation controls for Admin/Lecturer
      if (canModerate) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
          if (onEditClick != null) {
            ActionButton(label = "Edit", icon = Icons.Default.Edit, onClick = onEditClick, tag = "edit_dl_${deadline.id}")
          }
          if (onDeleteClick != null) {
            SoftIconButton(Icons.Default.Delete, "Delete", onDeleteClick, color = DangerRed, tag = "delete_dl_${deadline.id}")
          }
        }
      }
    }
  }
}
