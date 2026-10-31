@file:OptIn(ExperimentalMaterial3Api::class)

package com.willykez.campconnect.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api        // ← ADD THIS
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.UserProfile
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue

/**
 * Lets a campus administrator choose which lecturers teach a course.
 * ...
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignLecturersSheet(
  courses: List<Pair<String, String>>,
  lecturers: List<UserProfile>,
  assignedFor: (String) -> Set<String>,
  initialCourse: String?,
  onSave: (courseCode: String, lecturerIds: Set<String>) -> String?,
  onDismiss: () -> Unit,
  t: (en: String, sw: String) -> String
) {
  var course by remember { mutableStateOf(initialCourse ?: courses.singleOrNull()?.first) }
  var pickingCourse by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  val original = remember(course) { course?.let(assignedFor).orEmpty() }
  var selected by remember(course) { mutableStateOf(original) }

  val shown = remember(lecturers, query) {
    if (query.isBlank()) lecturers
    else lecturers.filter { it.fullName.contains(query, ignoreCase = true) }
  }
  val changed = selected != original

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = t("Assign lecturers", "Weka wahadhiri"),
    subtitle = t(
      "Choose who teaches a course. They can then post notices and upload materials for it.",
      "Chagua anayefundisha kozi. Wataweza kutuma matangazo na kupakia vifaa vya kozi hiyo."
    ),
    tag = "assign_lecturers_sheet",
    footer = {
      SheetButtons(
        dismissLabel = t("Cancel", "Ghairi"),
        onDismiss = onDismiss,
        confirmLabel = t("Save", "Hifadhi"),
        onConfirm = {
          val code = course
          if (code != null) {
            val reason = onSave(code, selected)
            if (reason == null) onDismiss() else error = reason
          }
        },
        enabled = course != null && changed,
        confirmTag = "assign_lecturers_save_btn"
      )
    }
  ) {
    Spacer(modifier = Modifier.height(6.dp))
    PickerField(
      label = t("Course", "Kozi"),
      value = course?.let { code -> courses.firstOrNull { it.first == code }?.let { "${it.first}  ${it.second}" } ?: code },
      placeholder = t("Choose a course", "Chagua kozi"),
      onClick = { pickingCourse = true },
      tag = "assign_course_field",
      leadingIcon = Icons.Default.School
    )
    Spacer(modifier = Modifier.height(14.dp))

    when {
      course == null -> Unit
      lecturers.isEmpty() -> Text(
        t(
          "No approved lecturers on this campus yet. Approve a lecturer account first, then assign them here.",
          "Hakuna wahadhiri walioidhinishwa bado. Idhinisha akaunti ya mhadhiri kwanza."
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      else -> {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(t("Lecturers", "Wahadhiri"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(
            t("${selected.size} selected", "Waliochaguliwa: ${selected.size}"),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected.isEmpty()) DangerRed else DeepBlue
          )
        }
        if (selected.isEmpty()) {
          Text(
            t("No lecturer would teach this course. Timetable and notices for it will have no responsible lecturer.", "Hakuna mhadhiri atakayefundisha kozi hii."),
            style = MaterialTheme.typography.bodySmall,
            color = DangerRed
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (lecturers.size > 8) {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(t("Search lecturers", "Tafuta wahadhiri")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("assign_lecturer_search")
          )
          Spacer(modifier = Modifier.height(6.dp))
        }
        shown.forEach { lecturer ->
          val on = lecturer.id in selected
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { selected = if (on) selected - lecturer.id else selected + lecturer.id; error = null }
              .padding(horizontal = 4.dp, vertical = 6.dp)
              .testTag("assign_lecturer_${lecturer.id}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(checked = on, onCheckedChange = null)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                lecturer.fullName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
              )
              // ✅ FIX: cohort is String? — null-check before using it
              lecturer.cohort?.let { cohort ->
                if (cohort.isNotBlank()) {
                  Text(
                    cohort,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
        if (shown.isEmpty()) {
          Text(
            t("No lecturer matches.", "Hakuna mhadhiri anayelingana."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    if (error != null) {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        error ?: "",
        color = DangerRed,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("assign_lecturers_error")
      )
    }
  }

  if (pickingCourse) {
    PickerSheet(
      title = t("Course", "Kozi"),
      options = courses.map { (code, title) ->
        val n = assignedFor(code).size
        PickerItem(
          code,
          "$code  $title",
          note = if (n == 0) t("No lecturer yet", "Hakuna mhadhiri bado")
                 else t("$n lecturer(s)", "Wahadhiri $n")
        )
      },
      selectedId = course,
      onSelect = { course = it; error = null; query = "" },
      onDismiss = { pickingCourse = false },
      emptyText = t("No courses yet. Add courses first.", "Hakuna kozi bado. Ongeza kozi kwanza.")
    )
  }
}