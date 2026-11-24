package com.willykez.campconnect.ui.screens

import com.willykez.campconnect.ui.components.SoftIconButton
import com.willykez.campconnect.ui.components.ActionButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.willykez.campconnect.ui.theme.DangerRedLight
import com.willykez.campconnect.data.docs.DocumentKind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.StudentSubmission
import com.willykez.campconnect.ui.components.StatusBadge
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.SuccessGreenLight
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.WarmAmberDark
import com.willykez.campconnect.ui.theme.WarmAmberLight
import com.willykez.campconnect.viewmodel.CampusViewModel

@Composable
fun SubmissionsScreen(
  viewModel: CampusViewModel,
  onPreviewDocument: (AttachmentFile) -> Unit,
  onGradeSubmission: (StudentSubmission) -> Unit,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val submissions by viewModel.submissions.collectAsState()
  val activeCourseFilter by viewModel.submissionFilterCourse.collectAsState()

  var statusFilter by remember { mutableStateOf("ALL") }
  val visible = submissions.filter { user.role != Role.STUDENT || it.studentId == user.id }
  val courses = listOf("ALL") + visible.map { it.courseCode }.distinct().sorted()
  val inCourse = visible.filter { activeCourseFilter == "ALL" || it.courseCode == activeCourseFilter }
  val filteredSubmissions = inCourse.filter { sub ->
    when (statusFilter) {
      "REVIEW" -> sub.grade.isNullOrBlank()
      "GRADED" -> !sub.grade.isNullOrBlank()
      "LATE" -> sub.isLate
      else -> true
    }
  }
  val waitingLabel = if (user.role == Role.STUDENT) "Awaiting grade" else "To review"

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = if (user.role == Role.STUDENT) "My Submissions" else "Student Coursework",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = if (user.role == Role.STUDENT) "Your uploaded assignments, grades & feedback" else "Review, grade and inspect uploaded student work",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatTile("Total", inCourse.size.toString(), DeepBlue, Modifier.weight(1f))
      StatTile(waitingLabel, inCourse.count { it.grade.isNullOrBlank() }.toString(), WarmAmberDark, Modifier.weight(1f))
      StatTile("Graded", inCourse.count { !it.grade.isNullOrBlank() }.toString(), SuccessGreen, Modifier.weight(1f))
      StatTile("Late", inCourse.count { it.isLate }.toString(), DangerRed, Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(10.dp))

    ChipRow(
      options = courses.map { it to (if (it == "ALL") "All Courses" else it) },
      selected = activeCourseFilter,
      tagPrefix = "submission_filter",
      onSelect = { viewModel.setSubmissionFilterCourse(it) }
    )

    Spacer(modifier = Modifier.height(8.dp))

    ChipRow(
      options = listOf("ALL" to "All", "REVIEW" to waitingLabel, "GRADED" to "Graded", "LATE" to "Late"),
      selected = statusFilter,
      tagPrefix = "submission_status",
      onSelect = { statusFilter = it }
    )

    Spacer(modifier = Modifier.height(14.dp))

    if (filteredSubmissions.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.UploadFile,
            contentDescription = "No submissions",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "No coursework submissions found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = if (user.role == Role.STUDENT) "You haven't submitted any assignment files yet" else "No student has submitted work for this selection",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(filteredSubmissions) { sub ->
          SubmissionCard(
            submission = sub,
            currentUserRole = user.role,
            canModerate = viewModel.canEditOrDelete(sub.studentId),
            onPreviewFile = { onPreviewDocument(sub.attachment) },
            onGradeClick = { onGradeSubmission(sub) },
            onDeleteClick = { viewModel.deleteStudentSubmission(sub.id) }
          )
        }

        item {
          Spacer(modifier = Modifier.height(30.dp))
        }
      }
    }
  }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
  Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.10f), modifier = modifier) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
      Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
  }
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, tagPrefix: String, onSelect: (String) -> Unit) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    items(options) { (value, label) ->
      val isSelected = selected == value
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
          width = 1.dp,
          color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier
          .clip(RoundedCornerShape(16.dp))
          .clickable { onSelect(value) }
          .testTag("${tagPrefix}_$value")
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
          color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
      }
    }
  }
}

private data class FileStyle(val icon: ImageVector, val color: Color, val label: String)

private fun fileStyle(file: AttachmentFile): FileStyle {
  val kind = if (file.isImage) DocumentKind.IMAGE else DocumentKind.of(file.fileName, file.mimeType)
  return when (kind) {
    DocumentKind.PDF -> FileStyle(Icons.Default.PictureAsPdf, Color(0xFFDC2626), kind.label)
    DocumentKind.WORD -> FileStyle(Icons.Default.Description, Color(0xFF2563EB), kind.label)
    DocumentKind.SHEET -> FileStyle(Icons.Default.TableChart, Color(0xFF16A34A), kind.label)
    DocumentKind.SLIDES -> FileStyle(Icons.Default.Slideshow, Color(0xFFEA580C), kind.label)
    DocumentKind.IMAGE -> FileStyle(Icons.Default.Photo, Color(0xFF7C3AED), kind.label)
    DocumentKind.ARCHIVE -> FileStyle(Icons.Default.FolderZip, Color(0xFF64748B), kind.label)
    else -> FileStyle(Icons.Default.InsertDriveFile, Color(0xFF64748B), kind.label)
  }
}

@Composable
fun SubmissionCard(
  submission: StudentSubmission,
  currentUserRole: Role,
  canModerate: Boolean,
  onPreviewFile: () -> Unit,
  onGradeClick: () -> Unit,
  onDeleteClick: () -> Unit
) {
  val isGraded = !submission.grade.isNullOrBlank()

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (isGraded) SuccessGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        shape = RoundedCornerShape(18.dp)
      )
      .testTag("submission_card_${submission.id}")
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
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = submission.courseCode,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.labelSmall
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Text(
            text = submission.studentName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (submission.isLate) {
            StatusBadge(text = "Late", backgroundColor = DangerRedLight, contentColor = DangerRed)
          }
          if (isGraded) {
            StatusBadge(
              text = "Grade: ${submission.grade}",
              backgroundColor = SuccessGreenLight,
              contentColor = SuccessGreen
            )
          } else {
            StatusBadge(
              text = "Pending Review",
              backgroundColor = WarmAmberLight,
              contentColor = Color(0xFFB45309)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = submission.assignmentTitle,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
      )

      Text(
        text = "ID: ${submission.studentNumber} • Submitted: ${submission.submittedAt}${if (submission.isLate) " • Late" else ""}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      if (submission.notes.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "\"${submission.notes}\"",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF334155)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val style = fileStyle(submission.attachment)
            Box(
              modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(style.color.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(imageVector = style.icon, contentDescription = style.label, tint = style.color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = submission.attachment.fileName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = listOf(style.label, submission.attachment.fileSize).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          ActionButton(
            label = "Open",
            icon = Icons.Default.Visibility,
            onClick = onPreviewFile,
            filled = true,
            tag = "preview_submission_doc_${submission.id}"
          )
        }
      }

      if (!submission.feedback.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SuccessGreenLight.copy(alpha = 0.5f))
            .padding(10.dp)
        ) {
          Column {
            Text(
              text = "Lecturer Evaluation & Feedback:",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF14532D)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = submission.feedback,
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFF166534)
            )
          }
        }
      }

      if (currentUserRole == Role.LECTURER || currentUserRole == Role.ADMIN) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (canModerate) {
            SoftIconButton(Icons.Default.Delete, "Delete", onDeleteClick, color = DangerRed, tag = "delete_sub_${submission.id}")
            Spacer(modifier = Modifier.width(8.dp))
          }
          ActionButton(
            label = if (isGraded) "Update grade" else "Grade submission",
            icon = Icons.Default.Grade,
            onClick = onGradeClick,
            color = if (isGraded) DeepBlue else WarmAmber,
            filled = !isGraded,
            tag = "grade_btn_${submission.id}"
          )
        }
      }
    }
  }
}
