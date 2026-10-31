package com.willykez.campconnect.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.University
import com.willykez.campconnect.ui.components.AppBottomSheet
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.viewmodel.CampusViewModel

/** Edit a campus's details. [canRename] is true only for the app manager. */
@Composable
fun EditCampusDetailsSheet(viewModel: CampusViewModel, campus: University, canRename: Boolean, onDismiss: () -> Unit) {
  var name by remember { mutableStateOf(campus.name) }
  var location by remember { mutableStateOf(campus.location) }
  var email by remember { mutableStateOf(campus.contactEmail) }
  var phone by remember { mutableStateOf(campus.contactPhone) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("Campus details", "Taarifa za chuo"),
    subtitle = campus.shortName,
    tag = "edit_campus_sheet",
    footer = {
      Button(
        onClick = {
          scope.launch {
            busy = true
            error = viewModel.updateCampusDetails(campus.id, if (canRename) name else null, location, email, phone)
            busy = false
            if (error == null) onDismiss()
          }
        },
        enabled = !busy,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.weight(1f).height(56.dp).testTag("campus_details_save")
      ) { Text(viewModel.t("Save", "Hifadhi"), fontWeight = FontWeight.SemiBold) }
    }
  ) {
    Spacer(Modifier.height(8.dp))
    if (canRename) {
      SheetField(name, { name = it; error = null }, viewModel.t("University name", "Jina la chuo"), "details_name")
      Spacer(Modifier.height(10.dp))
    }
    SheetField(location, { location = it }, viewModel.t("Location", "Mahali"), "details_location")
    Spacer(Modifier.height(10.dp))
    SheetField(email, { email = it }, viewModel.t("Contact email", "Barua pepe ya mawasiliano"), "details_email")
    Spacer(Modifier.height(10.dp))
    SheetField(phone, { phone = it }, viewModel.t("Contact phone", "Simu ya mawasiliano"), "details_phone")
    error?.let {
      Spacer(Modifier.height(10.dp))
      Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
    }
  }
}

/** Add an approved student, lecturer or administrator straight into a campus. */
@Composable
fun AddCampusUserSheet(viewModel: CampusViewModel, universityId: String, allowAdmin: Boolean, onDismiss: () -> Unit) {
  var role by remember { mutableStateOf(Role.LECTURER) }
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var number by remember { mutableStateOf("") }
  var programme by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val roles = if (allowAdmin) listOf(Role.STUDENT, Role.LECTURER, Role.ADMIN) else listOf(Role.STUDENT, Role.LECTURER)
  val programmes = remember(universityId) { viewModel.programmesFor(universityId) }
  val ready = name.isNotBlank() && email.isNotBlank() && number.isNotBlank() && password.isNotBlank() &&
    (role != Role.STUDENT || programme.isNotBlank())

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("Add a person", "Ongeza mtu"),
    subtitle = viewModel.t("They are approved at once and sign in with this email and password.", "Anaidhinishwa mara moja na kuingia kwa barua pepe na nenosiri hili."),
    tag = "add_user_sheet",
    footer = {
      Button(
        onClick = {
          scope.launch {
            busy = true
            error = viewModel.addCampusUser(universityId, role, name, email, number, programme.ifBlank { null }, password)
            busy = false
            if (error == null) onDismiss()
          }
        },
        enabled = ready && !busy,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.weight(1f).height(56.dp).testTag("add_user_btn")
      ) { Text(viewModel.t("Add person", "Ongeza"), fontWeight = FontWeight.SemiBold) }
    }
  ) {
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      roles.forEach { r ->
        FilterChip(
          selected = role == r,
          onClick = { role = r; error = null },
          label = {
            Text(
              when (r) {
                Role.STUDENT -> viewModel.t("Student", "Mwanafunzi")
                Role.LECTURER -> viewModel.t("Lecturer", "Mhadhiri")
                Role.ADMIN -> viewModel.t("Admin", "Msimamizi")
              }
            )
          },
          modifier = Modifier.testTag("add_user_role_${r.name}")
        )
      }
    }
    Spacer(Modifier.height(12.dp))
    SheetField(name, { name = it; error = null }, viewModel.t("Full name", "Jina kamili"), "add_user_name")
    Spacer(Modifier.height(10.dp))
    SheetField(email, { email = it; error = null }, viewModel.t("Email", "Barua pepe"), "add_user_email")
    Spacer(Modifier.height(10.dp))
    SheetField(
      number, { number = it; error = null },
      if (role == Role.STUDENT) viewModel.t("Registration number", "Namba ya usajili") else viewModel.t("Staff number", "Namba ya mfanyakazi"),
      "add_user_number"
    )
    if (role == Role.STUDENT) {
      Spacer(Modifier.height(10.dp))
      Text(viewModel.t("Programme", "Programu"), style = MaterialTheme.typography.labelLarge)
      if (programmes.isEmpty()) {
        Text(
          viewModel.t("Add programmes in the Campus tab first.", "Ongeza programu kwenye kichupo cha Chuo kwanza."),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Column(Modifier.fillMaxWidth()) {
        programmes.forEach { p ->
          FilterChip(
            selected = programme == p,
            onClick = { programme = p },
            label = { Text(p) },
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    SheetField(password, { password = it; error = null }, viewModel.t("Starting password", "Nenosiri la kuanzia"), "add_user_password", password = true)
    error?.let {
      Spacer(Modifier.height(10.dp))
      Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
    }
  }
}

/** The app manager's offline-mode switch for one campus. */
@Composable
fun MaintenanceSheet(viewModel: CampusViewModel, campus: University, onDismiss: () -> Unit) {
  var on by remember { mutableStateOf(campus.underMaintenance) }
  var note by remember { mutableStateOf(campus.maintenanceNote) }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("Campus offline mode", "Hali ya chuo nje ya mtandao"),
    subtitle = campus.name,
    tag = "maintenance_sheet",
    footer = {
      Button(
        onClick = { viewModel.setCampusMaintenance(campus.id, on, note); onDismiss() },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (on) DangerRed else DeepBlue),
        modifier = Modifier.weight(1f).height(56.dp).testTag("maintenance_apply")
      ) {
        Text(
          if (on) viewModel.t("Pause this campus", "Simamisha chuo hiki") else viewModel.t("Keep campus online", "Acha chuo mtandaoni"),
          fontWeight = FontWeight.SemiBold
        )
      }
    }
  ) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(viewModel.t("Under maintenance", "Matengenezo"), fontWeight = FontWeight.SemiBold)
        Text(
          viewModel.t(
            "Every student, lecturer and administrator of this campus sees a maintenance screen and cannot use the app until you switch this off.",
            "Kila mwanafunzi, mhadhiri na msimamizi wa chuo hiki ataona skrini ya matengenezo na hawezi kutumia programu hadi uizime."
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Switch(checked = on, onCheckedChange = { on = it }, modifier = Modifier.testTag("maintenance_switch"))
    }
    if (on) {
      Spacer(Modifier.height(12.dp))
      SheetField(note, { note = it }, viewModel.t("Message for the campus (optional)", "Ujumbe kwa chuo (hiari)"), "maintenance_note")
    }
  }
}
