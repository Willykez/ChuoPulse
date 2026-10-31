package com.willykez.campconnect.ui.screens

import com.willykez.campconnect.ui.components.ActionButton
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.FloatingActionButton
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
import com.willykez.campconnect.data.model.DeadlineItem
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.ui.components.DeadlineCard
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.viewmodel.CampusViewModel
import com.willykez.campconnect.viewmodel.ScreenTab

@Composable
fun DeadlinesScreen(
  viewModel: CampusViewModel,
  onOpenAddDeadlineDialog: () -> Unit,
  onSubmitWorkClick: (DeadlineItem) -> Unit,
  onEditDeadline: (DeadlineItem) -> Unit = {},
  onOpenAttachment: (AttachmentFile, String?, String?) -> Unit = { _, _, _ -> },
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val deadlines by viewModel.deadlines.collectAsState()
  val submissions by viewModel.submissions.collectAsState()
  val swahili = viewModel.t("a", "b") == "b"
  // A student's own handed-in work, newest first.
  val mySubmissions = if (user.role == Role.STUDENT) submissions.filter { it.studentId == user.id } else emptyList()

  val pendingDeadlines = deadlines.filter { !it.isCompleted }
  val completedDeadlines = deadlines.filter { it.isCompleted }

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = viewModel.getString("tab_calendar"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = viewModel.t("Assignments, CATs & submission dropboxes", "Kazi, majaribio na sehemu za kuwasilisha"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      // Shortcuts to the timed quizzes and the submissions, full-size so they are easy to tap
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        ActionButton(
          label = viewModel.t("Quizzes", "Majaribio"),
          icon = Icons.Default.Quiz,
          onClick = { viewModel.setTab(ScreenTab.QUIZZES) },
          modifier = Modifier.weight(1f),
          tag = "nav_to_quizzes_btn"
        )
        ActionButton(
          label = if (user.role == Role.STUDENT) viewModel.t("My works", "Kazi zangu") else viewModel.t("Submissions", "Kazi"),
          icon = Icons.Default.AssignmentTurnedIn,
          onClick = { viewModel.setTab(ScreenTab.SUBMISSIONS) },
          filled = true,
          modifier = Modifier.weight(1f),
          tag = "nav_to_submissions_btn"
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (pendingDeadlines.isNotEmpty()) {
          item {
            Text(
              text = "Upcoming Deadlines (${pendingDeadlines.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          items(pendingDeadlines, key = { it.id }) { dl ->
          Box(modifier = Modifier.animateItem()) {
            val canModerate = viewModel.canEditOrDelete(dl.lecturerId)

            DeadlineCard(
              deadline = dl,
              onToggleComplete = { viewModel.toggleDeadlineCompleted(dl.id) },
              onSubmitClick = if (user.role == Role.STUDENT) { { onSubmitWorkClick(dl) } } else null,
              onEditClick = if (canModerate) { { onEditDeadline(dl) } } else null,
              onDeleteClick = if (canModerate) { { viewModel.deleteDeadline(dl.id) } } else null,
              canModerate = canModerate,
              onAttachmentClick = dl.attachment?.let { att -> { onOpenAttachment(att, dl.lecturerName, dl.courseCode) } },
              onViewMySubmission = mySubmissions.firstOrNull { it.deadlineId == dl.id }
                ?.let { sub -> { onOpenAttachment(sub.attachment, sub.studentName, sub.courseCode) } },
              swahili = swahili
            )
          
          }
        }
        }

        if (completedDeadlines.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Submitted / Completed (${completedDeadlines.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          items(completedDeadlines, key = { it.id }) { dl ->
          Box(modifier = Modifier.animateItem()) {
            val canModerate = viewModel.canEditOrDelete(dl.lecturerId)

            DeadlineCard(
              deadline = dl,
              onToggleComplete = { viewModel.toggleDeadlineCompleted(dl.id) },
              onSubmitClick = if (user.role == Role.STUDENT) { { onSubmitWorkClick(dl) } } else null,
              onEditClick = if (canModerate) { { onEditDeadline(dl) } } else null,
              onDeleteClick = if (canModerate) { { viewModel.deleteDeadline(dl.id) } } else null,
              canModerate = canModerate,
              onAttachmentClick = dl.attachment?.let { att -> { onOpenAttachment(att, dl.lecturerName, dl.courseCode) } },
              onViewMySubmission = mySubmissions.firstOrNull { it.deadlineId == dl.id }
                ?.let { sub -> { onOpenAttachment(sub.attachment, sub.studentName, sub.courseCode) } },
              swahili = swahili
            )
          
          }
        }
        }

        item {
          Spacer(modifier = Modifier.height(80.dp))
        }
      }
    }

    if (user.role == Role.LECTURER || user.role == Role.ADMIN || (user.role == Role.STUDENT && user.isClassRep)) {
      FloatingActionButton(
        onClick = onOpenAddDeadlineDialog,
        containerColor = DeepBlue,
        contentColor = Color.White,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(20.dp)
          .size(66.dp)
          .testTag("fab_add_deadline")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Create Deadline"
        )
      }
    }
  }
}
