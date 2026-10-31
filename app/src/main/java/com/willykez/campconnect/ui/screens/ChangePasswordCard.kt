package com.willykez.campconnect.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.ui.components.AppBottomSheet
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.viewmodel.CampusViewModel
import kotlinx.coroutines.launch

/** Lets any signed-in person (student, lecturer, administrator) replace the password they were given. */
@Composable
fun ChangePasswordCard(viewModel: CampusViewModel) {
  var open by remember { mutableStateOf(false) }
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(viewModel.t("Password", "Nenosiri"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(
        viewModel.t("Change the password you were given to one only you know.", "Badilisha nenosiri ulilopewa liwe unalolijua wewe tu."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(10.dp))
      OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("change_password_open")) {
        Text(viewModel.t("Change password", "Badilisha nenosiri"))
      }
    }
  }
  if (open) ChangePasswordSheet(viewModel) { open = false }
}

@Composable
private fun ChangePasswordSheet(viewModel: CampusViewModel, onDismiss: () -> Unit) {
  var current by remember { mutableStateOf("") }
  var next by remember { mutableStateOf("") }
  var repeat by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("Change password", "Badilisha nenosiri"),
    subtitle = viewModel.t("At least 8 characters. Avoid common passwords.", "Angalau herufi 8. Epuka manenosiri yanayozoeleka."),
    tag = "change_password_sheet",
    footer = {
      Button(
        onClick = {
          if (next != repeat) {
            error = viewModel.t("The two new passwords are not the same.", "Manenosiri mapya mawili hayafanani.")
          } else scope.launch {
            busy = true
            error = viewModel.changePassword(current, next)
            busy = false
            if (error == null) onDismiss()
          }
        },
        enabled = !busy && current.isNotBlank() && next.isNotBlank() && repeat.isNotBlank(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.weight(1f).height(56.dp).testTag("change_password_save")
      ) { Text(viewModel.t("Save password", "Hifadhi nenosiri"), fontWeight = FontWeight.SemiBold) }
    }
  ) {
    Spacer(Modifier.height(8.dp))
    SheetField(current, { current = it; error = null }, viewModel.t("Current password", "Nenosiri la sasa"), "pw_current", password = true)
    Spacer(Modifier.height(10.dp))
    SheetField(next, { next = it; error = null }, viewModel.t("New password", "Nenosiri jipya"), "pw_new", password = true)
    Spacer(Modifier.height(10.dp))
    SheetField(repeat, { repeat = it; error = null }, viewModel.t("Repeat new password", "Rudia nenosiri jipya"), "pw_repeat", password = true)
    error?.let {
      Spacer(Modifier.height(10.dp))
      Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
    }
  }
}
