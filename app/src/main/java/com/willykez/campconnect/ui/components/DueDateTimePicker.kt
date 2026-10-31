@file:OptIn(ExperimentalMaterial3Api::class)
package com.willykez.campconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.util.DeadlineTime
import java.util.Calendar
import java.util.TimeZone

/**
 * Pick a due date, then a due time, instead of typing a date as text.
 * [valueText] and [onValueChange] use the app's deadline text, e.g. "Friday, 24 Oct 2026 • 17:00 EAT".
 */
@Composable
fun DueDateTimeField(
  valueText: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  swahili: Boolean = false,
  tag: String = "deadline_due_input"
) {
  val current = DeadlineTime.parse(valueText) ?: DeadlineTime.tomorrowEvening()
  var showDate by remember { mutableStateOf(false) }
  var showTime by remember { mutableStateOf(false) }
  var pickedDay by remember { mutableStateOf(current) }

  Column(modifier = modifier.fillMaxWidth()) {
    PickerField(
      label = label,
      value = DeadlineTime.format(current),
      placeholder = label,
      onClick = { showDate = true },
      tag = tag,
      leadingIcon = Icons.Default.Event
    )
    val past = current < System.currentTimeMillis()
    Text(
      text = if (past) {
        if (swahili) "Wakati huu umeshapita" else "This time has already passed"
      } else DeadlineTime.urgency(DeadlineTime.format(current), "", swahili),
      style = MaterialTheme.typography.labelSmall,
      color = if (past) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
  }

  if (showDate) {
    // The calendar works in UTC midnight of the chosen day; turn the current due date into that.
    val local = Calendar.getInstance(DeadlineTime.zone).apply { timeInMillis = current }
    val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      clear()
      set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    val dateState = rememberDatePickerState(initialSelectedDateMillis = utcMidnight)
    DatePickerDialog(
      onDismissRequest = { showDate = false },
      confirmButton = {
        TextButton(onClick = {
          val chosen = dateState.selectedDateMillis
          if (chosen != null) {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = chosen }
            pickedDay = Calendar.getInstance(DeadlineTime.zone).apply {
              timeInMillis = current
              set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
            showDate = false
            showTime = true
          }
        }) { Text(if (swahili) "Endelea" else "Next") }
      },
      dismissButton = { TextButton(onClick = { showDate = false }) { Text(if (swahili) "Ghairi" else "Cancel") } }
    ) { DatePicker(state = dateState) }
  }

  if (showTime) {
    val cal = Calendar.getInstance(DeadlineTime.zone).apply { timeInMillis = pickedDay }
    val timeState = rememberTimePickerState(
      initialHour = cal.get(Calendar.HOUR_OF_DAY),
      initialMinute = cal.get(Calendar.MINUTE),
      is24Hour = true
    )
    Dialog(onDismissRequest = { showTime = false }) {
      Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(24.dp)) {
          Text(
            if (swahili) "Chagua saa ya mwisho (EAT)" else "Choose the due time (EAT)",
            style = MaterialTheme.typography.titleMedium
          )
          Spacer(Modifier.height(16.dp))
          TimePicker(state = timeState)
          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showTime = false }) { Text(if (swahili) "Ghairi" else "Cancel") }
            TextButton(onClick = {
              val result = Calendar.getInstance(DeadlineTime.zone).apply {
                timeInMillis = pickedDay
                set(Calendar.HOUR_OF_DAY, timeState.hour)
                set(Calendar.MINUTE, timeState.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
              }.timeInMillis
              onValueChange(DeadlineTime.format(result))
              showTime = false
            }) { Text(if (swahili) "Weka" else "Set") }
          }
        }
      }
    }
  }
}
