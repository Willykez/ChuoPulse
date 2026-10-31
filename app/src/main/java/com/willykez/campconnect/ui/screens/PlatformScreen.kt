package com.willykez.campconnect.ui.screens

import kotlinx.coroutines.launch
import com.willykez.campconnect.ui.components.SoftIconButton
import com.willykez.campconnect.ui.components.ActionButton
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import com.willykez.campconnect.ui.theme.DangerRedLight
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.SuccessGreenLight
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.DeepBlueDark
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.willykez.campconnect.data.repository.CampusSummary
import com.willykez.campconnect.data.repository.WipeScope
import com.willykez.campconnect.ui.components.AppBottomSheet
import com.willykez.campconnect.ui.components.ConfirmSheet
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.viewmodel.CampusViewModel

/**
 * The app manager's (owner's) console. It lists every campus with its status and totals, creates new
 * campuses with their first administrator, switches a campus offline for maintenance, edits campus
 * details, adds people and can delete data from any campus. It never shows what a campus wrote:
 * campus administrators run their own campuses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformScreen(viewModel: CampusViewModel) {
  val campuses by viewModel.campuses.collectAsState()
  LaunchedEffect(Unit) { viewModel.refreshAdminData() }
  val logs by viewModel.auditLogs.collectAsState()
  val message by viewModel.message.collectAsState()
  val user by viewModel.currentUser.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  var creating by remember { mutableStateOf(false) }
  var managing by remember { mutableStateOf<String?>(null) }
  var quick by remember { mutableStateOf<Pair<String, Int>?>(null) } // campus id to action (1 offline, 2 edit, 3 add person)
  var tab by remember { mutableStateOf(0) }

  LaunchedEffect(message) {
    message?.let {
      snackbar.showSnackbar(it)
      viewModel.messageShown()
    }
  }

  val offline = campuses.count { it.university.underMaintenance }
  val people = campuses.sumOf { it.people }
  val waiting = campuses.sumOf { it.pending }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    containerColor = MaterialTheme.colorScheme.background,
    modifier = Modifier.systemBarsPadding().testTag("platform_screen")
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      // Header
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
          .background(Brush.verticalGradient(listOf(DeepBlueDark, DeepBlue)))
          .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 20.dp)
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Dns, contentDescription = null, tint = Color.White) }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                viewModel.t("App manager", "Msimamizi wa programu"),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White
              )
              Text(
                viewModel.t("Control center for every campus", "Kituo cha kudhibiti kila chuo"),
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f)
              )
            }
            IconButton(onClick = { viewModel.signOut() }, modifier = Modifier.testTag("platform_sign_out")) {
              Icon(Icons.Filled.Logout, contentDescription = viewModel.t("Sign out", "Toka"), tint = Color.White)
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(user.fullName, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
          Spacer(modifier = Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(viewModel.t("Campuses", "Vyuo"), "${campuses.size}", Modifier.weight(1f))
            StatTile(viewModel.t("Online", "Mtandaoni"), "${campuses.size - offline}", Modifier.weight(1f))
            StatTile(viewModel.t("Offline", "Nje"), "$offline", Modifier.weight(1f), highlight = offline > 0)
            StatTile(viewModel.t("People", "Watu"), "$people", Modifier.weight(1f))
          }
        }
      }

      TabRow(
        selectedTabIndex = tab,
        containerColor = Color.Transparent,
        contentColor = DeepBlue,
        divider = {}
      ) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.testTag("platform_tab_campuses"), text = {
          Text(viewModel.t("Campuses (${campuses.size})", "Vyuo (${campuses.size})"), fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Normal)
        })
        Tab(selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.testTag("platform_tab_log"), text = {
          Text(viewModel.t("Activity log", "Kumbukumbu"), fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Normal)
        })
      }

      if (tab == 0) {
        if (waiting > 0) {
          Text(
            viewModel.t("$waiting people are waiting for approval across campuses.", "Watu $waiting wanasubiri idhini katika vyuo."),
            style = MaterialTheme.typography.labelMedium,
            color = WarmAmber,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
          )
        }
        LazyColumn(
          modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp)
        ) {
          items(campuses, key = { it.university.id }) { campus ->
            Box(modifier = Modifier.animateItem()) {
              CampusCard(
                campus = campus,
                viewModel = viewModel,
                onMaintenance = { quick = campus.university.id to 1 },
                onEdit = { quick = campus.university.id to 2 },
                onAddPerson = { quick = campus.university.id to 3 },
                onManage = { managing = campus.university.id }
              )
            }
          }
          item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        Button(
          onClick = { creating = true },
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp).testTag("platform_new_campus_btn")
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text(viewModel.t("New campus", "Chuo kipya"), fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(12.dp))
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(vertical = 12.dp)
        ) {
          if (logs.isEmpty()) {
            item {
              Text(
                viewModel.t("Nothing has happened yet.", "Hakuna kilichofanyika bado."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          items(logs, key = { it.id }) { log ->
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(log.action.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = DeepBlue)
                  Text(log.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(log.details, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }
    }
  }

  if (creating) CreateCampusSheet(viewModel = viewModel, onDismiss = { creating = false })
  managing?.let { id ->
    campuses.firstOrNull { it.university.id == id }?.let { campus ->
      ManageCampusSheet(campus = campus, viewModel = viewModel, onDismiss = { managing = null })
    }
  }
  quick?.let { (id, which) ->
    campuses.firstOrNull { it.university.id == id }?.let { campus ->
      when (which) {
        1 -> MaintenanceSheet(viewModel, campus.university) { quick = null }
        2 -> EditCampusDetailsSheet(viewModel, campus.university, canRename = true) { quick = null }
        3 -> AddCampusUserSheet(viewModel, campus.university.id, allowAdmin = true) { quick = null }
      }
    }
  }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(if (highlight) WarmAmber.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.16f))
      .padding(vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
  }
}

@Composable
private fun CampusCard(
  campus: CampusSummary,
  viewModel: CampusViewModel,
  onMaintenance: () -> Unit,
  onEdit: () -> Unit,
  onAddPerson: () -> Unit,
  onManage: () -> Unit
) {
  val u = campus.university
  val offline = u.underMaintenance
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
      .testTag("campus_${u.id}")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(DeepBlue.copy(alpha = 0.10f)),
          contentAlignment = Alignment.Center
        ) { Text(u.shortName.take(4), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = DeepBlue) }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(u.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(
            u.location.ifBlank { u.contactEmail }.ifBlank { viewModel.t("No details yet", "Bado hakuna taarifa") },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Surface(shape = RoundedCornerShape(8.dp), color = if (offline) DangerRedLight else SuccessGreenLight) {
          Text(
            if (offline) viewModel.t("OFFLINE", "NJE") else viewModel.t("ONLINE", "MTANDAONI"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (offline) DangerRed else SuccessGreen,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
      if (offline && u.maintenanceNote.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(u.maintenanceNote, style = MaterialTheme.typography.bodySmall, color = DangerRed)
      }
      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
      Spacer(modifier = Modifier.height(10.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MiniStat("${campus.people}", viewModel.t("people", "watu"))
        MiniStat("${campus.pending}", viewModel.t("waiting", "wanasubiri"))
        MiniStat("${campus.courses}", viewModel.t("courses", "kozi"))
        MiniStat("${campus.sessions}", viewModel.t("classes", "vipindi"))
        MiniStat("${campus.materials}", viewModel.t("files", "mafaili"))
      }
      Spacer(modifier = Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionButton(
          label = if (offline) viewModel.t("Bring online", "Rudisha") else viewModel.t("Maintenance", "Matengenezo"),
          icon = Icons.Default.PowerSettingsNew,
          onClick = onMaintenance,
          color = if (offline) SuccessGreen else WarmAmber,
          modifier = Modifier.weight(1f),
          tag = "toggle_status_${u.id}"
        )
        SoftIconButton(Icons.Default.PersonAdd, viewModel.t("Add person", "Ongeza mtu"), onAddPerson, tag = "add_person_${u.id}")
        SoftIconButton(Icons.Default.Edit, viewModel.t("Edit details", "Hariri"), onEdit, tag = "edit_campus_${u.id}")
        SoftIconButton(Icons.Default.DeleteSweep, viewModel.t("Delete data", "Futa data"), onManage, color = DangerRed, tag = "manage_data_${u.id}")
      }
    }
  }
}

@Composable
private fun MiniStat(value: String, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCampusSheet(viewModel: CampusViewModel, onDismiss: () -> Unit) {
  var code by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var adminName by remember { mutableStateOf("") }
  var adminEmail by remember { mutableStateOf("") }
  var adminNumber by remember { mutableStateOf("") }
  var adminPassword by remember { mutableStateOf("") }
  var location by remember { mutableStateOf("") }
  var contactEmail by remember { mutableStateOf("") }
  var contactPhone by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("New campus", "Chuo kipya"),
    subtitle = viewModel.t(
      "Creates the campus and its first administrator. The administrator then adds the programmes, courses and rooms and approves their people.",
      "Huunda chuo na msimamizi wake wa kwanza. Msimamizi huyo huongeza programu, kozi na vyumba na kuidhinisha watu wake."
    ),
    footer = {
      Button(
        onClick = {
          scope.launch {
            busy = true
            error = viewModel.createCampus(code, name, adminName, adminEmail, adminNumber, adminPassword, location, contactEmail, contactPhone)
            busy = false
            if (error == null) onDismiss()
          }
        },
        enabled = !busy && listOf(code, name, adminName, adminEmail, adminNumber, adminPassword).all { it.isNotBlank() },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.weight(1f).height(52.dp).testTag("campus_create_btn")
      ) { Text(if (busy) viewModel.t("Creating...", "Inaunda...") else viewModel.t("Create campus", "Unda chuo"), fontWeight = FontWeight.SemiBold) }
    }
  ) {
    Spacer(modifier = Modifier.height(8.dp))
    SheetField(code, { code = it; error = null }, viewModel.t("Short name (for example UDOM)", "Jina fupi (mfano UDOM)"), "campus_code")
    Spacer(modifier = Modifier.height(10.dp))
    SheetField(name, { name = it; error = null }, viewModel.t("Full name of the university", "Jina kamili la chuo"), "campus_name")
    Spacer(modifier = Modifier.height(10.dp))
    SheetField(location, { location = it }, viewModel.t("Location (optional)", "Mahali (si lazima)"), "campus_location")
    Spacer(modifier = Modifier.height(10.dp))
    SheetField(contactEmail, { contactEmail = it }, viewModel.t("Contact email (optional)", "Barua pepe ya mawasiliano (si lazima)"), "campus_contact_email")
    Spacer(modifier = Modifier.height(10.dp))
    SheetField(contactPhone, { contactPhone = it }, viewModel.t("Contact phone (optional)", "Simu ya mawasiliano (si lazima)"), "campus_contact_phone")
    Spacer(modifier = Modifier.height(18.dp))
    Text(viewModel.t("First administrator", "Msimamizi wa kwanza"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    SheetField(adminName, { adminName = it; error = null }, viewModel.t("Full name", "Jina kamili"), "campus_admin_name")
    Spacer(modifier = Modifier.height(10.dp))
    SheetField(adminEmail, { adminEmail = it; error = null }, viewModel.t("Email", "Barua pepe"), "campus_admin_email")
    Spacer(modifier = Modifier.height(10.dp))
    SheetField(adminNumber, { adminNumber = it; error = null }, viewModel.t("Staff number", "Namba ya mfanyakazi"), "campus_admin_number")
    Spacer(modifier = Modifier.height(10.dp))
    SheetField(adminPassword, { adminPassword = it; error = null }, viewModel.t("Starting password", "Nenosiri la kuanzia"), "campus_admin_password", password = true)

    if (error != null) {
      Spacer(modifier = Modifier.height(10.dp))
      Text(error ?: "", color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("campus_error"))
    }
  }
}

private data class DeleteChoice(val label: String, val detail: String, val scope: WipeScope?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageCampusSheet(campus: CampusSummary, viewModel: CampusViewModel, onDismiss: () -> Unit) {
  var confirm by remember { mutableStateOf<DeleteChoice?>(null) }
  var typed by remember { mutableStateOf("") }
  var action by remember { mutableStateOf(0) }

  when (action) {
    1 -> MaintenanceSheet(viewModel, campus.university) { action = 0 }
    2 -> EditCampusDetailsSheet(viewModel, campus.university, canRename = true) { action = 0 }
    3 -> AddCampusUserSheet(viewModel, campus.university.id, allowAdmin = true) { action = 0 }
  }

  val choices = listOf(
    DeleteChoice(viewModel.t("Notices", "Matangazo"), "${campus.announcements}", WipeScope.ANNOUNCEMENTS),
    DeleteChoice(viewModel.t("Timetable", "Ratiba"), "${campus.sessions}", WipeScope.TIMETABLE),
    DeleteChoice(viewModel.t("Study materials", "Vifaa vya masomo"), "${campus.materials}", WipeScope.MATERIALS),
    DeleteChoice(viewModel.t("Deadlines and hand-ins", "Tarehe za mwisho na kazi"), "${campus.works}", WipeScope.WORKS),
    DeleteChoice(viewModel.t("Notifications", "Arifa"), "", WipeScope.NOTIFICATIONS),
    DeleteChoice(viewModel.t("Programmes, courses and rooms", "Programu, kozi na vyumba"), "${campus.programmes + campus.courses + campus.rooms}", WipeScope.CATALOG),
    DeleteChoice(viewModel.t("Students and lecturers", "Wanafunzi na wahadhiri"), "${campus.people - campus.admins}", WipeScope.PEOPLE),
    DeleteChoice(viewModel.t("Everything, including administrators", "Kila kitu, pamoja na wasimamizi"), "", WipeScope.EVERYTHING),
    DeleteChoice(viewModel.t("Delete this campus completely", "Futa chuo hiki kabisa"), "", null)
  )

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = campus.university.name,
    subtitle = viewModel.t(
      "${campus.people} people \u2022 ${campus.pending} waiting for approval",
      "Watu ${campus.people} \u2022 ${campus.pending} wanasubiri idhini"
    ),
    tag = "manage_campus_sheet"
  ) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(viewModel.t("Manage", "Simamia"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    listOf(
      Triple(if (campus.university.underMaintenance) viewModel.t("Offline mode: ON (tap to change)", "Nje ya mtandao: IMEWASHWA (gusa kubadilisha)") else viewModel.t("Put campus offline for maintenance", "Weka chuo nje ya mtandao kwa matengenezo"), "manage_maintenance", 1),
      Triple(viewModel.t("Edit campus details", "Hariri taarifa za chuo"), "manage_edit_details", 2),
      Triple(viewModel.t("Add a person (admin, lecturer, student)", "Ongeza mtu (msimamizi, mhadhiri, mwanafunzi)"), "manage_add_user", 3)
    ).forEach { (label, tag, which) ->
      Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { action = which }.padding(horizontal = 8.dp, vertical = 14.dp).testTag(tag)
      )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(viewModel.t("Delete data", "Futa data"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
      viewModel.t("Deleting cannot be undone.", "Kufuta hakurudishwi."),
      style = MaterialTheme.typography.bodySmall,
      color = DangerRed
    )
    Spacer(modifier = Modifier.height(6.dp))
    choices.forEach { choice ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable { typed = ""; confirm = choice }
          .padding(horizontal = 8.dp, vertical = 14.dp)
          .testTag("wipe_${choice.scope?.name ?: "CAMPUS"}"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          choice.label,
          style = MaterialTheme.typography.bodyLarge,
          color = if (choice.scope == null || choice.scope == WipeScope.EVERYTHING) DangerRed else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f)
        )
        if (choice.detail.isNotBlank()) {
          Text(choice.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }

  confirm?.let { choice ->
    val matches = typed.trim().equals(campus.university.shortName, ignoreCase = true)
    ConfirmSheet(
      title = viewModel.t("Delete ${choice.label.lowercase()}?", "Futa ${choice.label.lowercase()}?"),
      message = viewModel.t(
        "This removes it from ${campus.university.name} for everyone and cannot be undone. Type ${campus.university.shortName} to confirm.",
        "Hii huiondoa kwa kila mtu wa ${campus.university.name} na haiwezi kurudishwa. Andika ${campus.university.shortName} kuthibitisha."
      ),
      confirmLabel = viewModel.t("Delete", "Futa"),
      dismissLabel = viewModel.t("Cancel", "Ghairi"),
      confirmEnabled = matches,
      onDismiss = { confirm = null },
      onConfirm = {
        val scope = choice.scope
        if (scope == null) viewModel.deleteCampus(campus.university.id) else viewModel.wipeCampus(campus.university.id, scope)
        confirm = null
        onDismiss()
      },
      confirmTag = "wipe_confirm_btn",
      extra = {
        OutlinedTextField(
          value = typed,
          onValueChange = { typed = it },
          singleLine = true,
          placeholder = { Text(campus.university.shortName) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("wipe_confirm_text")
        )
      }
    )
  }
}
