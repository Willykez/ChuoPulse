package com.willykez.campconnect.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.repository.ManagedPerson
import com.willykez.campconnect.ui.components.AppBottomSheet
import com.willykez.campconnect.ui.components.PickerItem
import com.willykez.campconnect.ui.components.PickerSheet
import com.willykez.campconnect.ui.components.SheetButtons
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.viewmodel.CampusViewModel
import kotlinx.coroutines.launch
import java.io.File

/** Tab ids used by AdminScreen. */
internal object AdminTabs { const val REQUESTS = 0; const val LOG = 1; const val CAMPUS = 2; const val PEOPLE = 3; const val OVERVIEW = 4; const val MESSAGES = 5 }

// ======================================================================================
// The campus administrator's home: what needs attention, the numbers, and every action in one place.
// ======================================================================================

@Composable
fun AdminOverviewTab(viewModel: CampusViewModel, onGo: (Int) -> Unit, modifier: Modifier = Modifier) {
  val people by viewModel.managedPeople.collectAsState()
  val programmes by viewModel.managedProgrammes.collectAsState()
  val pending by viewModel.pendingUsers.collectAsState()
  val me by viewModel.currentUser.collectAsState()
  val myCampus by viewModel.myCampus.collectAsState()
  val context = LocalContext.current
  LaunchedEffect(Unit) { viewModel.refreshPeople() }
  var adding by remember { mutableStateOf<Role?>(null) }
  var bulk by remember { mutableStateOf<Role?>(null) }

  val students = people.count { it.role == "student" }
  val lecturers = people.count { it.role == "lecturer" }
  val reps = people.count { it.isClassRep }
  val admins = people.count { it.role == "admin" }
  val paused = people.count { it.paused }

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    myCampus?.let { c ->
      if (c.underMaintenance) Banner(WarmAmber, viewModel.t("This campus is offline for maintenance.", "Chuo hiki kiko nje ya mtandao kwa matengenezo."))
    }
    if (pending.isNotEmpty()) {
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WarmAmber.copy(alpha = 0.14f))
          .border(1.dp, WarmAmber.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
          .clickable { onGo(AdminTabs.REQUESTS) }.padding(14.dp).testTag("overview_pending"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.HourglassTop, null, tint = WarmAmber)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(viewModel.t("${pending.size} waiting for approval", "${pending.size} wanasubiri idhini"), fontWeight = FontWeight.Bold)
          Text(viewModel.t("Tap to review them", "Gusa kuwakagua"), style = MaterialTheme.typography.bodySmall)
        }
      }
    }

    Text(viewModel.t("Your campus", "Chuo chako"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    StatRows(
      listOf(
        OverviewStat(Icons.Default.Groups, viewModel.t("Students", "Wanafunzi"), students),
        OverviewStat(Icons.Default.School, viewModel.t("Lecturers", "Wahadhiri"), lecturers),
        OverviewStat(Icons.Default.Star, viewModel.t("Class reps", "Wawakilishi"), reps),
        OverviewStat(Icons.Default.AdminPanelSettings, viewModel.t("Admins", "Wasimamizi"), admins),
        OverviewStat(Icons.Default.PauseCircle, viewModel.t("Paused", "Wamesimamishwa"), paused),
        OverviewStat(Icons.Default.VerifiedUser, viewModel.t("Programmes", "Programu"), programmes.size)
      )
    )

    Text(viewModel.t("Do something", "Fanya kitu"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    ActionTile(Icons.Default.PersonAdd, viewModel.t("Add a student", "Ongeza mwanafunzi"), viewModel.t("Create the account and give them a password", "Tengeneza akaunti na mpe nenosiri"), "ov_add_student") { adding = Role.STUDENT }
    ActionTile(Icons.Default.GroupAdd, viewModel.t("Add many students at once", "Ongeza wanafunzi wengi kwa mara moja"), viewModel.t("Paste a list: name, number, email", "Bandika orodha: jina, namba, barua pepe"), "ov_bulk_students") { bulk = Role.STUDENT }
    ActionTile(Icons.Default.PersonAdd, viewModel.t("Add a lecturer", "Ongeza mhadhiri"), viewModel.t("Staff account for teaching", "Akaunti ya mfanyakazi kwa kufundisha"), "ov_add_lecturer") { adding = Role.LECTURER }
    ActionTile(Icons.Default.Campaign, viewModel.t("Message people", "Tuma ujumbe kwa watu"), viewModel.t("Everyone, students, lecturers, class reps or one programme", "Wote, wanafunzi, wahadhiri, wawakilishi au programu moja"), "ov_message") { onGo(AdminTabs.MESSAGES) }
    ActionTile(Icons.Default.Groups, viewModel.t("Manage people", "Simamia watu"), viewModel.t("Edit, change role, reset password, pause, delete", "Hariri, badilisha jukumu, weka upya nenosiri, simamisha, futa"), "ov_people") { onGo(AdminTabs.PEOPLE) }
    ActionTile(Icons.Default.Settings, viewModel.t("Campus setup", "Mpangilio wa chuo"), viewModel.t("Programmes, courses, rooms, details", "Programu, kozi, vyumba, taarifa"), "ov_setup") { onGo(AdminTabs.CAMPUS) }
    ActionTile(Icons.Default.FileDownload, viewModel.t("Export people (CSV)", "Hamisha orodha ya watu (CSV)"), viewModel.t("Open or share in a spreadsheet app", "Fungua au shiriki kwenye programu ya lahajedwali"), "ov_export") {
      exportPeopleCsv(context, myCampus?.shortName ?: "campus", people)
    }

    if (programmes.isNotEmpty()) {
      Text(viewModel.t("Programmes", "Programu"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      programmes.forEach { p ->
        val studentsHere = people.filter { it.role == "student" && it.programmeId == p.id }
        val repNames = studentsHere.filter { it.isClassRep }.joinToString(", ") { it.name }
        Column(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).padding(12.dp)
        ) {
          Text(p.name, fontWeight = FontWeight.SemiBold)
          Text(
            viewModel.t("${studentsHere.size} students", "Wanafunzi ${studentsHere.size}") + "  ·  " +
              (if (repNames.isBlank()) viewModel.t("no class representative", "hakuna mwakilishi") else viewModel.t("rep: $repNames", "mwakilishi: $repNames")),
            style = MaterialTheme.typography.bodySmall, color = if (repNames.isBlank()) WarmAmber else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }

  adding?.let { r ->
    AddCampusUserSheet(viewModel, me.universityId, allowAdmin = false, initialRole = r, onlyRole = true) { adding = null }
  }
  bulk?.let { r -> BulkAddSheet(viewModel, me.universityId, r) { bulk = null } }
}

private data class OverviewStat(val icon: ImageVector, val label: String, val value: Int)

@Composable
private fun StatRows(stats: List<OverviewStat>) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    stats.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { s ->
          Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
              .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).padding(12.dp)
          ) {
            Icon(s.icon, null, tint = DeepBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text("${s.value}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(s.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun Banner(color: androidx.compose.ui.graphics.Color, text: String) {
  Text(
    text, color = color, fontWeight = FontWeight.SemiBold,
    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f)).padding(12.dp)
  )
}

// ======================================================================================
// Messages: a notification to a group of people on the campus.
// ======================================================================================

@Composable
fun AdminMessagesTab(viewModel: CampusViewModel, modifier: Modifier = Modifier) {
  val programmes by viewModel.managedProgrammes.collectAsState()
  LaunchedEffect(Unit) { viewModel.refreshPeople() }
  val scope = rememberCoroutineScope()
  var audience by remember { mutableStateOf("all") }
  var programmeId by remember { mutableStateOf<String?>(null) }
  var picking by remember { mutableStateOf(false) }
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var urgent by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  val audiences = listOf(
    "all" to viewModel.t("Everyone", "Wote"), "students" to viewModel.t("Students", "Wanafunzi"),
    "lecturers" to viewModel.t("Lecturers", "Wahadhiri"), "reps" to viewModel.t("Class reps", "Wawakilishi"),
    "programme" to viewModel.t("One programme", "Programu moja")
  )

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(viewModel.t("Who should get it?", "Nani apate?"), fontWeight = FontWeight.Bold)
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(audiences.size) { i ->
        val (id, label) = audiences[i]
        FilterChip(selected = audience == id, onClick = { audience = id }, label = { Text(label) }, modifier = Modifier.testTag("msg_audience_$id"))
      }
    }
    if (audience == "programme") {
      OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("msg_programme")) {
        Text(programmes.firstOrNull { it.id == programmeId }?.name ?: viewModel.t("Choose programme", "Chagua programu"))
      }
    }
    OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(viewModel.t("Title", "Kichwa")) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("msg_title"))
    OutlinedTextField(body, { body = it }, minLines = 4, label = { Text(viewModel.t("Message", "Ujumbe")) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("msg_body"))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(viewModel.t("Mark as urgent", "Weka kuwa ya dharura"), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
      Switch(urgent, { urgent = it })
    }
    Text(viewModel.t("It appears in each person's notifications.", "Itaonekana kwenye arifa za kila mtu."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    error?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
    Button(
      onClick = {
        scope.launch {
          busy = true
          error = viewModel.sendToPeople(audience, if (audience == "programme") programmeId else null, title, body, urgent)
          busy = false
          if (error == null) { title = ""; body = ""; urgent = false }
        }
      },
      enabled = !busy && title.isNotBlank() && body.isNotBlank() && (audience != "programme" || programmeId != null),
      shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("msg_send")
    ) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Send", "Tuma"), fontWeight = FontWeight.SemiBold) }
    Spacer(Modifier.height(16.dp))
  }
  if (picking) PickerSheet(
    title = viewModel.t("Programme", "Programu"), options = programmes.map { PickerItem(it.id, it.name) },
    selectedId = programmeId, onSelect = { programmeId = it }, onDismiss = { picking = false }
  )
}

// ======================================================================================
// Add many people at once.
// ======================================================================================

private data class BulkRow(val name: String, val email: String, val number: String, val password: String, var error: String? = null, var done: Boolean = false)

@Composable
fun BulkAddSheet(viewModel: CampusViewModel, universityId: String, role: Role, onDismiss: () -> Unit) {
  val programmes by viewModel.managedProgrammes.collectAsState()
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  var programmeName by remember { mutableStateOf<String?>(null) }
  var picking by remember { mutableStateOf(false) }
  var text by remember { mutableStateOf("") }
  var results by remember { mutableStateOf<List<BulkRow>>(emptyList()) }
  var running by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf(0) }
  val needsProgramme = role == Role.STUDENT

  fun parse(): List<BulkRow> = text.lines().map { it.trim() }.filter { it.isNotBlank() }.map { line ->
    val parts = line.split(',', ';', '\t').map { it.trim() }
    BulkRow(parts.getOrElse(0) { "" }, parts.getOrElse(2) { "" }.lowercase(), parts.getOrElse(1) { "" }, parts.getOrNull(3)?.takeIf { it.length >= 8 } ?: generatePassword())
  }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = if (role == Role.STUDENT) viewModel.t("Add many students", "Ongeza wanafunzi wengi") else viewModel.t("Add many lecturers", "Ongeza wahadhiri wengi"),
    subtitle = viewModel.t("One person per line: name, number, email", "Mtu mmoja kwa mstari: jina, namba, barua pepe"),
    tag = "bulk_add_sheet",
    footer = {
      if (results.isEmpty()) SheetButtons(
        viewModel.t("Cancel", "Ghairi"), onDismiss, viewModel.t("Create accounts", "Tengeneza akaunti"),
        {
          val rows = parse()
          results = rows
          scope.launch {
            running = true; progress = 0
            rows.forEach { r ->
              val err = viewModel.addCampusUser(universityId, role, r.name, r.email, r.number, if (needsProgramme) programmeName else null, r.password)
              r.error = err; r.done = err == null
              progress += 1
              results = results.toList()
            }
            running = false
            viewModel.refreshPeople()
          }
        },
        enabled = !running && text.isNotBlank() && (!needsProgramme || programmeName != null)
      ) else SheetButtons(
        viewModel.t("Copy passwords", "Nakili nenosiri"),
        {
          clipboard.setText(AnnotatedString(results.filter { it.done }.joinToString("\n") { "${it.name} | ${it.email} | ${it.password}" }))
        },
        viewModel.t("Done", "Imekamilika"), onDismiss, enabled = !running
      )
    }
  ) {
    Spacer(Modifier.height(8.dp))
    if (results.isEmpty()) {
      if (needsProgramme) OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("bulk_programme")) {
        Text(programmeName ?: viewModel.t("Choose the programme for all of them", "Chagua programu ya wote"))
      }
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        text, { text = it }, minLines = 6, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("bulk_text"),
        placeholder = { Text("Amina Juma, 2024/BIT/0123, amina@mail.com\nJohn Mushi, 2024/BIT/0124, john@mail.com") }
      )
      Text(
        viewModel.t("A password is made for each person. Add a 4th column to set your own (8+ characters). You can copy them all afterwards.", "Nenosiri hutengenezwa kwa kila mtu. Ongeza safu ya 4 kuweka lako (herufi 8+). Unaweza kuzinakili zote baadaye."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
      )
    } else {
      Text(viewModel.t("Done $progress of ${results.size}", "Imekamilika $progress kati ya ${results.size}"), fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      results.forEach { r ->
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
          Row {
            Text(r.name.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (r.done) "✓" else if (r.error != null) "✗" else "…", color = if (r.done) SuccessGreen else if (r.error != null) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Text(if (r.done) "${r.email}  ·  ${r.password}" else r.error ?: r.email, style = MaterialTheme.typography.bodySmall, color = if (r.error != null) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }
  if (picking) PickerSheet(
    title = viewModel.t("Programme", "Programu"), options = programmes.map { PickerItem(it.name, it.name) },
    selectedId = programmeName, onSelect = { programmeName = it }, onDismiss = { picking = false }
  )
}

// ======================================================================================
// Export
// ======================================================================================

internal fun exportPeopleCsv(context: Context, campusCode: String, people: List<ManagedPerson>) {
  fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
  val csv = buildString {
    append("Name,Role,Number,Programme,Department,Email,Status,Class rep\n")
    people.forEach { p ->
      append(listOf(p.name, p.role, p.number, p.programmeName, p.department, p.email, p.status, if (p.isClassRep) "yes" else "").joinToString(",") { q(it) }).append('\n')
    }
  }
  val dir = File(context.cacheDir, "exports").apply { mkdirs() }
  val file = File(dir, "people-${campusCode.lowercase().replace(Regex("[^a-z0-9]+"), "-")}.csv")
  file.writeText(csv)
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
  val send = Intent(Intent.ACTION_SEND).apply {
    type = "text/csv"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
