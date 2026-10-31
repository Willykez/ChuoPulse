package com.willykez.campconnect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.util.formatClock
import com.willykez.campconnect.viewmodel.CampusViewModel

private enum class SessionPicker { SUBJECT, LECTURER, START, END, ROOM }

/**
 * Add a class to the timetable. Pick the subject, the lecturer responsible, the day and time, then a
 * room: rooms already in use at that time are shown but cannot be chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionSheet(viewModel: CampusViewModel, initialDay: Int, onDismiss: () -> Unit) {
  val user by viewModel.currentUser.collectAsState()
  val is24Hour by viewModel.is24HourFormat.collectAsState()

  val courses = remember { viewModel.schedulableCourses() }
  var course by remember { mutableStateOf(courses.singleOrNull()?.first) }
  var lecturerId by remember { mutableStateOf<String?>(null) }
  var day by remember { mutableStateOf(initialDay.coerceIn(1, 6)) }
  var start by remember { mutableStateOf("08:00") }
  var end by remember { mutableStateOf("10:00") }
  var room by remember { mutableStateOf("") }
  var picker by remember { mutableStateOf<SessionPicker?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  val lecturers = course?.let { viewModel.lecturersFor(it) }.orEmpty()
  val rooms = viewModel.roomsWithAvailability(day, start, end)
  val times = remember { (6..20).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) } }

  // The person who is responsible is usually the lecturer adding the class, or the only one who teaches it.
  LaunchedEffect(course) {
    lecturerId = lecturers.firstOrNull { it.id == user.id }?.id ?: lecturers.singleOrNull()?.id
  }
  // A room that becomes busy after the time changes is un-chosen, so a clash cannot be saved by accident.
  LaunchedEffect(day, start, end) {
    if (rooms.any { it.name.equals(room, ignoreCase = true) && it.busyWith != null }) room = ""
  }

  val dayNames = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat")

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("Add a class", "Ongeza kipindi"),
    tag = "add_session_sheet",
    footer = {
      Button(
        onClick = {
          val c = course
          val l = lecturerId
          if (c != null && l != null) {
            error = viewModel.tryAddSession(c, l, day, start, end, room)
            if (error == null) onDismiss()
          }
        },
        enabled = course != null && lecturerId != null && room.isNotBlank(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.weight(1f).height(52.dp).testTag("session_save_btn")
      ) { Text(viewModel.t("Add to timetable", "Ongeza kwenye ratiba"), fontWeight = FontWeight.SemiBold) }
    }
  ) {
    Spacer(modifier = Modifier.height(6.dp))
    PickerField(
      label = viewModel.t("Subject", "Somo"),
      value = course?.let { code -> courses.firstOrNull { it.first == code }?.let { "${it.first}  ${it.second}" } ?: code },
      placeholder = viewModel.t("Choose a subject", "Chagua somo"),
      onClick = { picker = SessionPicker.SUBJECT },
      tag = "session_subject",
      leadingIcon = Icons.Default.School
    )
    Spacer(modifier = Modifier.height(10.dp))
    PickerField(
      label = viewModel.t("Lecturer responsible", "Mhadhiri anayehusika"),
      value = lecturers.firstOrNull { it.id == lecturerId }?.fullName,
      placeholder = if (course == null) viewModel.t("Choose a subject first", "Chagua somo kwanza") else viewModel.t("Choose a lecturer", "Chagua mhadhiri"),
      onClick = { picker = SessionPicker.LECTURER },
      tag = "session_lecturer",
      enabled = course != null,
      leadingIcon = Icons.Default.Person
    )
    if (course != null && lecturers.isEmpty()) {
      Text(
        viewModel.t(
          "No lecturer teaches this subject yet. Ask your campus administrator to assign one.",
          "Hakuna mhadhiri anayefundisha somo hili bado. Muombe msimamizi wa chuo amwongeze."
        ),
        style = MaterialTheme.typography.bodySmall,
        color = DangerRed,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
      )
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(viewModel.t("Day", "Siku"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
      dayNames.forEach { (n, label) ->
        val selected = day == n
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DeepBlue else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { day = n }
            .padding(vertical = 10.dp)
            .testTag("session_day_$label"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      PickerField(
        label = viewModel.t("Starts", "Inaanza"),
        value = formatClock(start, is24Hour),
        placeholder = "",
        onClick = { picker = SessionPicker.START },
        tag = "session_start",
        modifier = Modifier.weight(1f),
        leadingIcon = Icons.Default.AccessTime
      )
      PickerField(
        label = viewModel.t("Ends", "Inaisha"),
        value = formatClock(end, is24Hour),
        placeholder = "",
        onClick = { picker = SessionPicker.END },
        tag = "session_end",
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(10.dp))
    if (rooms.isEmpty()) {
      // The campus has not added its rooms yet: type one.
      OutlinedTextField(
        value = room,
        onValueChange = { room = it },
        label = { Text(viewModel.t("Room", "Chumba")) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("session_room_text")
      )
    } else {
      PickerField(
        label = viewModel.t("Room", "Chumba"),
        value = room.takeIf { it.isNotBlank() },
        placeholder = viewModel.t("Choose a free room", "Chagua chumba kisicho na mtu"),
        onClick = { picker = SessionPicker.ROOM },
        tag = "session_room",
        leadingIcon = Icons.Default.MeetingRoom
      )
    }

    if (error != null) {
      Spacer(modifier = Modifier.height(10.dp))
      Text(error ?: "", color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("session_error"))
    }
  }

  when (picker) {
    SessionPicker.SUBJECT -> PickerSheet(
      title = viewModel.t("Subject", "Somo"),
      options = courses.map { PickerItem(it.first, "${it.first}  ${it.second}") },
      selectedId = course,
      onSelect = { course = it; error = null },
      onDismiss = { picker = null },
      emptyText = viewModel.t("No subjects yet. Your campus administrator adds them under Admin > Campus.", "Hakuna masomo bado. Msimamizi wa chuo huyaongeza.")
    )
    SessionPicker.LECTURER -> PickerSheet(
      title = viewModel.t("Lecturer responsible", "Mhadhiri anayehusika"),
      options = lecturers.map { PickerItem(it.id, it.fullName, it.cohort) },
      selectedId = lecturerId,
      onSelect = { lecturerId = it; error = null },
      onDismiss = { picker = null },
      emptyText = viewModel.t("No lecturer teaches this subject yet.", "Hakuna mhadhiri anayefundisha somo hili bado.")
    )
    SessionPicker.START -> PickerSheet(
      title = viewModel.t("Starts", "Inaanza"),
      options = times.map { PickerItem(it, formatClock(it, is24Hour)) },
      selectedId = start,
      onSelect = { picked ->
        start = picked
        // Keep the class two hours long unless the end is still later.
        if (end <= picked) end = times.firstOrNull { it > picked && it >= shiftHours(picked, 2) } ?: times.last()
        error = null
      },
      onDismiss = { picker = null }
    )
    SessionPicker.END -> PickerSheet(
      title = viewModel.t("Ends", "Inaisha"),
      options = times.filter { it > start }.map { PickerItem(it, formatClock(it, is24Hour)) },
      selectedId = end,
      onSelect = { end = it; error = null },
      onDismiss = { picker = null }
    )
    SessionPicker.ROOM -> PickerSheet(
      title = viewModel.t("Room", "Chumba"),
      options = rooms.map {
        PickerItem(
          id = it.name,
          title = it.name,
          enabled = it.busyWith == null,
          note = it.busyWith?.let { c -> viewModel.t("In use: $c", "Inatumika: $c") }
        )
      },
      selectedId = room,
      onSelect = { room = it; error = null },
      onDismiss = { picker = null }
    )
    null -> Unit
  }
}

private fun shiftHours(time: String, hours: Int): String {
  val h = (time.substringBefore(':').toIntOrNull() ?: 0) + hours
  return "%02d:%s".format(h.coerceAtMost(23), time.substringAfter(':'))
}
