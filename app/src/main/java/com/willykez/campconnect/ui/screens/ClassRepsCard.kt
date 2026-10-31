package com.willykez.campconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.AccountStatus
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.UserProfile
import com.willykez.campconnect.ui.components.AppBottomSheet
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.viewmodel.CampusViewModel

/**
 * Class representatives. Administrators and lecturers choose them; a representative sees a badge here.
 * A representative may post notices, materials, assignments and timetable changes for their own class
 * (never quizzes, never other students' work).
 */
@Composable
fun ClassRepsCard(viewModel: CampusViewModel, user: UserProfile) {
  if (user.status != AccountStatus.APPROVED || user.isPlatformAdmin) return
  var open by remember { mutableStateOf(false) }

  when {
    user.role == Role.STUDENT && user.isClassRep -> Card(
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = DeepBlue.copy(alpha = 0.10f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(viewModel.t("You are a class representative", "Wewe ni mwakilishi wa darasa"), fontWeight = FontWeight.Bold, color = DeepBlue)
        Text(
          viewModel.t(
            "You can post notices, materials, assignments and timetable changes for your class.",
            "Unaweza kuweka matangazo, nyenzo, kazi na mabadiliko ya ratiba kwa darasa lako."
          ),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    user.role == Role.ADMIN || user.role == Role.LECTURER -> {
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(viewModel.t("Class representatives", "Wawakilishi wa darasa"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(
            viewModel.t(
              "Choose students who may post notices, materials, assignments and timetable changes for their class.",
              "Chagua wanafunzi wanaoweza kuweka matangazo, nyenzo, kazi na mabadiliko ya ratiba kwa darasa lao."
            ),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(Modifier.height(10.dp))
          OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("class_reps_open")) {
            Text(viewModel.t("Manage class representatives", "Simamia wawakilishi"))
          }
        }
      }
      if (open) ClassRepsSheet(viewModel) { open = false }
    }
  }
}

@Composable
private fun ClassRepsSheet(viewModel: CampusViewModel, onDismiss: () -> Unit) {
  val members by viewModel.classMembers.collectAsState()
  var query by remember { mutableStateOf("") }
  val shown = members.filter {
    query.isBlank() || it.name.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true) ||
      it.programme.contains(query, ignoreCase = true)
  }.sortedWith(compareByDescending<com.willykez.campconnect.data.repository.ClassMember> { it.isRep }.thenBy { it.name }).take(80)

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("Class representatives", "Wawakilishi wa darasa"),
    subtitle = viewModel.t("Only approved students with a programme can be chosen.", "Ni wanafunzi walioidhinishwa wenye programu tu wanaweza kuchaguliwa."),
    tag = "class_reps_sheet"
  ) {
    Spacer(Modifier.height(8.dp))
    SheetField(query, { query = it }, viewModel.t("Search name, number or programme", "Tafuta jina, namba au programu"), "class_rep_search")
    Spacer(Modifier.height(10.dp))
    if (shown.isEmpty()) {
      Text(
        viewModel.t("No students to show yet.", "Hakuna wanafunzi wa kuonyesha bado."),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    shown.forEach { m ->
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(m.name, fontWeight = FontWeight.SemiBold)
          Text(
            listOf(m.number, m.programme).filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (m.isRep) {
          OutlinedButton(onClick = { viewModel.setClassRep(m.id, false) }, shape = RoundedCornerShape(10.dp)) {
            Text(viewModel.t("Remove", "Ondoa"))
          }
        } else {
          Button(
            onClick = { viewModel.setClassRep(m.id, true) }, shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepBlue)
          ) { Text(viewModel.t("Make rep", "Mteue")) }
        }
      }
    }
  }
}
