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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.willykez.campconnect.data.model.ContentApprovalStatus
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.StudentSubmission
import com.willykez.campconnect.ui.components.StatusBadge
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DangerRedLight
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.SuccessGreenLight
import com.willykez.campconnect.ui.theme.WarmAmber
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

  val courses = listOf("ALL", "CS210", "IS202", "CS215")

  val filteredSubmissions = submissions.filter { sub ->
    when {
      activeCourseFilter != "ALL" && sub.courseCode != activeCourseFilter -> false
      user.role == Role.STUDENT && sub.studentId != user.id -> false
      else -> true
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    // Header
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

    // Filter Chips
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      items(courses) { course ->
        val isSelected = activeCourseFilter == course
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.surface,
          border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { viewModel.setSubmissionFilterCourse(course) }
            .testTag("submission_filter_$course")
        ) {
          Text(
            text = if (course == "ALL") "All Courses" else course,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

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

        // Grade Badge
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

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = submission.assignmentTitle,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
      )

      Text(
        text = "ID: ${submission.studentNumber} • Submitted: ${submission.submittedAt}",
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

      // File Attachment Row
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
            Icon(
              imageVector = if (submission.attachment.isImage) Icons.Default.Assignment else Icons.Default.PictureAsPdf,
              contentDescription = "File",
              tint = DeepBlue,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = submission.attachment.fileName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = submission.attachment.fileSize,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          // In-App Document Preview Button
          ActionButton(
            label = "View",
            icon = Icons.Default.Visibility,
            onClick = onPreviewFile,
            filled = true,
            tag = "preview_submission_doc_${submission.id}"
          )
        }
      }

      // Feedback display if available
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

      // Action row for Lecturers & Admins
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
