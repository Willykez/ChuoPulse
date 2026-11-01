package com.willykez.campconnect.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.repository.ManagedAdmin
import com.willykez.campconnect.data.repository.ManagedPerson
import com.willykez.campconnect.data.repository.ProgrammeRef
import com.willykez.campconnect.ui.components.AppBottomSheet
import com.willykez.campconnect.ui.components.ConfirmSheet
import com.willykez.campconnect.ui.components.PickerItem
import com.willykez.campconnect.ui.components.PickerSheet
import com.willykez.campconnect.ui.components.SheetButtons
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.viewmodel.CampusViewModel
import kotlinx.coroutines.launch

// ======================================================================================
// Campus administrator: every person on the campus, by role, by programme.
// ======================================================================================

private enum class PeopleTab { STUDENTS, REPS, LECTURERS, ADMINS }

@Composable
fun PeopleManagementTab(viewModel: CampusViewModel, modifier: Modifier = Modifier) {
  val people by viewModel.managedPeople.collectAsState()
  val programmes by viewModel.managedProgrammes.collectAsState()
  val me by viewModel.currentUser.collectAsState()
  LaunchedEffect(Unit) { viewModel.refreshPeople() }

  var tab by remember { mutableStateOf(PeopleTab.STUDENTS) }
  var programmeId by remember { mutableStateOf<String?>(null) }
  var query by remember { mutableStateOf("") }
  var adding by remember { mutableStateOf<Role?>(null) }
  var choosingReps by remember { mutableStateOf(false) }
  var openPerson by remember { mutableStateOf<String?>(null) }

  // Always one programme selected, so students are never shown "all in one place".
  val selected = programmeId?.takeIf { id -> programmes.any { it.id == id } } ?: programmes.firstOrNull()?.id
  val selectedName = programmes.firstOrNull { it.id == selected }?.name.orEmpty()

  fun matches(p: ManagedPerson) = query.isBlank() || p.name.contains(query, true) || p.number.contains(query, true) || p.email.contains(query, true)

  val students = people.filter { it.role == "student" && it.programmeId == selected && matches(it) }
  val reps = people.filter { it.role == "student" && it.isClassRep && it.programmeId == selected && matches(it) }
  val lecturers = people.filter { it.role == "lecturer" && matches(it) }
  val admins = people.filter { it.role == "admin" && matches(it) }
  val shown = when (tab) { PeopleTab.STUDENTS -> students; PeopleTab.REPS -> reps; PeopleTab.LECTURERS -> lecturers; PeopleTab.ADMINS -> admins }

  Column(modifier = modifier.fillMaxSize()) {
    ScrollableTabRow(
      selectedTabIndex = tab.ordinal, edgePadding = 0.dp,
      containerColor = MaterialTheme.colorScheme.surface, contentColor = DeepBlue
    ) {
      PeopleTab.values().forEach { t ->
        val count = when (t) {
          PeopleTab.STUDENTS -> people.count { it.role == "student" }
          PeopleTab.REPS -> people.count { it.isClassRep }
          PeopleTab.LECTURERS -> people.count { it.role == "lecturer" }
          PeopleTab.ADMINS -> people.count { it.role == "admin" }
        }
        val label = when (t) {
          PeopleTab.STUDENTS -> viewModel.t("Students", "Wanafunzi")
          PeopleTab.REPS -> viewModel.t("Class reps", "Wawakilishi")
          PeopleTab.LECTURERS -> viewModel.t("Lecturers", "Wahadhiri")
          PeopleTab.ADMINS -> viewModel.t("Admins", "Wasimamizi")
        }
        Tab(
          selected = tab == t, onClick = { tab = t }, modifier = Modifier.testTag("people_tab_${t.name}"),
          text = { Text("$label ($count)", fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
        )
      }
    }

    Spacer(Modifier.height(10.dp))

    // Programme chooser: students and class representatives are always seen one programme at a time.
    if (tab == PeopleTab.STUDENTS || tab == PeopleTab.REPS) {
      if (programmes.isEmpty()) {
        Text(
          viewModel.t("Add programmes in the Campus tab first. Students are grouped by programme.", "Ongeza programu kwenye kichupo cha Chuo kwanza. Wanafunzi huwekwa kwa programu."),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(programmes, key = { it.id }) { p ->
            FilterChip(
              selected = p.id == selected, onClick = { programmeId = p.id },
              label = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
              modifier = Modifier.testTag("people_programme_${p.id}")
            )
          }
        }
        Spacer(Modifier.height(8.dp))
      }
    }

    SheetField(query, { query = it }, viewModel.t("Search name, number or email", "Tafuta jina, namba au barua pepe"), "people_search")
    Spacer(Modifier.height(10.dp))

    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (shown.isEmpty()) {
        item {
          Text(
            when (tab) {
              PeopleTab.STUDENTS -> viewModel.t("No students in this programme yet.", "Hakuna wanafunzi katika programu hii bado.")
              PeopleTab.REPS -> viewModel.t("No class representative for this programme yet.", "Hakuna mwakilishi wa darasa kwa programu hii bado.")
              PeopleTab.LECTURERS -> viewModel.t("No lecturers yet.", "Hakuna wahadhiri bado.")
              PeopleTab.ADMINS -> viewModel.t("No other administrators.", "Hakuna wasimamizi wengine.")
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
          )
        }
      }
      items(shown, key = { it.id }) { p -> PersonRow(p) { openPerson = p.id } }
      item { Spacer(Modifier.height(8.dp)) }
    }

    when (tab) {
      PeopleTab.STUDENTS -> Button(
        onClick = { adding = Role.STUDENT }, enabled = programmes.isNotEmpty(),
        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("people_add_student")
      ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Add student", "Ongeza mwanafunzi"), fontWeight = FontWeight.SemiBold) }
      PeopleTab.REPS -> Button(
        onClick = { choosingReps = true }, enabled = selected != null,
        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("people_choose_reps")
      ) { Icon(Icons.Default.Star, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Choose from $selectedName", "Chagua kutoka $selectedName"), fontWeight = FontWeight.SemiBold) }
      PeopleTab.LECTURERS -> Button(
        onClick = { adding = Role.LECTURER },
        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("people_add_lecturer")
      ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Add lecturer", "Ongeza mhadhiri"), fontWeight = FontWeight.SemiBold) }
      PeopleTab.ADMINS -> Text(
        viewModel.t("Administrators are managed by the app manager.", "Wasimamizi husimamiwa na msimamizi wa programu."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Spacer(Modifier.height(10.dp))
  }

  adding?.let { r ->
    AddCampusUserSheet(
      viewModel, me.universityId, allowAdmin = false, onDismiss = { adding = null },
      initialRole = r, initialProgramme = if (r == Role.STUDENT) selectedName else null, onlyRole = true
    )
  }
  if (choosingReps) {
    ClassRepChooserSheet(viewModel, selectedName, people.filter { it.role == "student" && it.approved && it.programmeId == selected }) { choosingReps = false }
  }
  openPerson?.let { id ->
    people.firstOrNull { it.id == id }?.let { p ->
      PersonManageSheet(
        viewModel = viewModel, id = p.id, name = p.name, email = p.email, role = p.role, status = p.status,
        number = p.number, programmeId = p.programmeId, programmeName = p.programmeName, department = p.department,
        isClassRep = p.isClassRep, programmes = programmes, allowRoleChange = p.role != "admin", allowClassRep = true,
        onDismiss = { openPerson = null }
      )
    } ?: run { openPerson = null }
  }
}

@Composable
private fun PersonRow(p: ManagedPerson, onClick: () -> Unit) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
      .clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).testTag("person_row_${p.id}")
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Avatar(p.name)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          listOf(p.number, p.programmeName.ifBlank { p.department }).filter { it.isNotBlank() }.joinToString("  ·  "),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1, overflow = TextOverflow.Ellipsis
        )
      }
      if (p.isClassRep) Pill("REP", DeepBlue)
      StatusPill(p.status)
      Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
internal fun Avatar(name: String) {
  Box(Modifier.size(40.dp).clip(CircleShape).background(DeepBlue.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
    Text(name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }, color = DeepBlue, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun Pill(text: String, color: Color) {
  Text(
    text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp)
  )
}

@Composable
private fun StatusPill(status: String) {
  when (status) {
    "suspended" -> Pill("PAUSED", WarmAmber)
    "pending" -> Pill("PENDING", DeepBlue)
    "rejected" -> Pill("OFF", DangerRed)
  }
}

@Composable
private fun ClassRepChooserSheet(viewModel: CampusViewModel, programmeName: String, students: List<ManagedPerson>, onDismiss: () -> Unit) {
  val scope = rememberCoroutineScope()
  var query by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf<String?>(null) }
  val shown = students.filter { query.isBlank() || it.name.contains(query, true) || it.number.contains(query, true) }
    .sortedWith(compareByDescending<ManagedPerson> { it.isClassRep }.thenBy { it.name })
  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("Class representatives", "Wawakilishi wa darasa"),
    subtitle = programmeName, tag = "rep_chooser_sheet"
  ) {
    Spacer(Modifier.height(8.dp))
    SheetField(query, { query = it }, viewModel.t("Search", "Tafuta"), "rep_search")
    Spacer(Modifier.height(10.dp))
    if (shown.isEmpty()) Text(viewModel.t("No approved students in this programme.", "Hakuna wanafunzi walioidhinishwa katika programu hii."), color = MaterialTheme.colorScheme.onSurfaceVariant)
    shown.forEach { m ->
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(m.name, fontWeight = FontWeight.SemiBold)
          Text(m.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (m.isClassRep) OutlinedButton(
          onClick = { scope.launch { busy = m.id; viewModel.setPersonClassRep(m.id, false); busy = null } },
          enabled = busy == null, shape = RoundedCornerShape(10.dp)
        ) { Text(viewModel.t("Remove", "Ondoa")) }
        else Button(
          onClick = { scope.launch { busy = m.id; viewModel.setPersonClassRep(m.id, true); busy = null } },
          enabled = busy == null, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepBlue)
        ) { Text(viewModel.t("Make rep", "Mteue")) }
      }
    }
  }
}

// ======================================================================================
// One person: details and every action. Used by campus administrators (students, lecturers)
// and by the app manager (campus administrators).
// ======================================================================================

@Composable
fun PersonManageSheet(
  viewModel: CampusViewModel,
  id: String, name: String, email: String, role: String, status: String, number: String,
  programmeId: String?, programmeName: String, department: String, isClassRep: Boolean,
  programmes: List<ProgrammeRef>, allowRoleChange: Boolean, allowClassRep: Boolean,
  onDismiss: () -> Unit,
  roleChangeTargets: List<String> = if (role == "student") listOf("lecturer") else listOf("student"),
  allowDelete: Boolean = true
) {
  val scope = rememberCoroutineScope()
  var mode by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val paused = status == "suspended"
  val roleLabel = when (role) {
    "student" -> viewModel.t("Student", "Mwanafunzi"); "lecturer" -> viewModel.t("Lecturer", "Mhadhiri"); else -> viewModel.t("Administrator", "Msimamizi")
  }

  fun run(block: suspend () -> String?) {
    scope.launch { busy = true; error = block(); busy = false; if (error == null) mode = "" }
  }

  AppBottomSheet(onDismissRequest = onDismiss, title = name, subtitle = "$roleLabel  ·  ${if (paused) viewModel.t("Paused", "Imesimamishwa") else status}", tag = "person_sheet") {
    Spacer(Modifier.height(6.dp))
    Detail(viewModel.t("Email (login)", "Barua pepe (kuingia)"), email)
    Detail(if (role == "student") viewModel.t("Registration number", "Namba ya usajili") else viewModel.t("Staff number", "Namba ya mfanyakazi"), number)
    if (role == "student") Detail(viewModel.t("Programme", "Programu"), programmeName.ifBlank { "—" })
    if (department.isNotBlank()) Detail(viewModel.t("Department", "Idara"), department)
    if (isClassRep) Detail(viewModel.t("Class representative", "Mwakilishi wa darasa"), viewModel.t("Yes", "Ndiyo"))
    error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(8.dp))

    ActionRow(Icons.Default.Edit, viewModel.t("Edit details", "Hariri taarifa"), "person_edit") { error = null; mode = "edit" }
    ActionRow(Icons.Default.LockReset, viewModel.t("Reset password", "Weka upya nenosiri"), "person_reset") { error = null; mode = "password" }
    ActionRow(Icons.Default.Email, viewModel.t("Change login email", "Badilisha barua pepe ya kuingia"), "person_login") { error = null; mode = "login" }
    if (allowRoleChange) ActionRow(Icons.Default.SwapHoriz, viewModel.t("Change role", "Badilisha jukumu"), "person_role") { error = null; mode = "role" }
    if (allowClassRep && role == "student" && status == "approved") {
      ActionRow(Icons.Default.Star, if (isClassRep) viewModel.t("Remove as class representative", "Ondoa uwakilishi wa darasa") else viewModel.t("Make class representative", "Mteue mwakilishi wa darasa"), "person_rep") {
        run { viewModel.setPersonClassRep(id, !isClassRep) }
      }
    }
    ActionRow(
      if (paused) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
      if (paused) viewModel.t("Resume account", "Rudisha akaunti") else viewModel.t("Pause account", "Simamisha akaunti"), "person_pause"
    ) { error = null; if (paused) run { viewModel.pausePerson(id, false) } else mode = "pause" }
    if (allowDelete) ActionRow(Icons.Default.Delete, viewModel.t("Delete account", "Futa akaunti"), "person_delete", DangerRed) { error = null; mode = "delete" }
    Spacer(Modifier.height(12.dp))
  }

  when (mode) {
    "edit" -> EditPersonSheet(viewModel, role, name, number, programmeId, department, programmes, error, busy, { mode = "" }) { n, num, prog, dep ->
      run { viewModel.editPerson(id, n, num, prog, dep) }
    }
    "password" -> ResetPasswordSheet(viewModel, name, error, busy, { mode = "" }) { pw -> run { viewModel.resetPersonPassword(id, pw) } }
    "login" -> LoginEmailSheet(viewModel, email, error, busy, { mode = "" }) { e -> run { viewModel.changePersonLogin(id, e) } }
    "role" -> ChangeRoleSheet(viewModel, roleChangeTargets, programmes, error, busy, { mode = "" }) { r, num, prog -> run { viewModel.changePersonRole(id, r, num, prog) } }
    "pause" -> ConfirmSheet(
      title = viewModel.t("Pause $name?", "Simamisha $name?"),
      message = viewModel.t("They are signed out and cannot sign in or see anything until you resume the account. Nothing is deleted.", "Atatolewa na hataweza kuingia wala kuona chochote hadi urudishe akaunti. Hakuna kinachofutwa."),
      confirmLabel = viewModel.t("Pause", "Simamisha"), dismissLabel = viewModel.t("Cancel", "Ghairi"), destructive = false,
      onConfirm = { run { viewModel.pausePerson(id, true) } }, onDismiss = { mode = "" }
    )
    "delete" -> ConfirmSheet(
      title = viewModel.t("Delete $name?", "Futa $name?"),
      message = viewModel.t("The account and sign-in are removed for good. This cannot be undone. To keep the record, pause the account instead.", "Akaunti na kuingia vinaondolewa kabisa. Haiwezi kurudishwa. Ili kuhifadhi kumbukumbu, simamisha akaunti badala yake."),
      confirmLabel = viewModel.t("Delete", "Futa"), dismissLabel = viewModel.t("Cancel", "Ghairi"),
      onConfirm = { scope.launch { busy = true; val e = viewModel.deletePerson(id); busy = false; if (e == null) onDismiss() else { error = e; mode = "" } } },
      onDismiss = { mode = "" }
    )
  }
}

@Composable
private fun Detail(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
    Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
  }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tag: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
  val c = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp).testTag(tag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, null, tint = c)
    Spacer(Modifier.width(14.dp))
    Text(label, color = c, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun EditPersonSheet(
  viewModel: CampusViewModel, role: String, name: String, number: String, programmeId: String?, department: String,
  programmes: List<ProgrammeRef>, error: String?, busy: Boolean, onDismiss: () -> Unit,
  onSave: (String, String, String?, String?) -> Unit
) {
  var n by remember { mutableStateOf(name) }
  var num by remember { mutableStateOf(number) }
  var prog by remember { mutableStateOf(programmeId) }
  var dep by remember { mutableStateOf(department) }
  var picking by remember { mutableStateOf(false) }
  AppBottomSheet(
    onDismissRequest = onDismiss, title = viewModel.t("Edit details", "Hariri taarifa"), tag = "edit_person_sheet",
    footer = { SheetButtons(viewModel.t("Cancel", "Ghairi"), onDismiss, viewModel.t("Save", "Hifadhi"), { onSave(n, num, if (role == "student") prog else null, if (role == "student") null else dep) }, enabled = !busy && n.isNotBlank() && num.isNotBlank()) }
  ) {
    Spacer(Modifier.height(8.dp))
    SheetField(n, { n = it }, viewModel.t("Full name", "Jina kamili"), "edit_name")
    Spacer(Modifier.height(10.dp))
    SheetField(num, { num = it }, if (role == "student") viewModel.t("Registration number", "Namba ya usajili") else viewModel.t("Staff number", "Namba ya mfanyakazi"), "edit_number")
    Spacer(Modifier.height(10.dp))
    if (role == "student") {
      OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("edit_programme")) {
        Text(programmes.firstOrNull { it.id == prog }?.name ?: viewModel.t("Choose programme", "Chagua programu"))
      }
      Text(viewModel.t("Moving a student to another programme changes their courses and removes class representative status.", "Kumhamisha mwanafunzi programu hubadilisha kozi zake na huondoa uwakilishi wa darasa."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    } else {
      SheetField(dep, { dep = it }, viewModel.t("Department", "Idara"), "edit_department")
    }
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
  }
  if (picking) PickerSheet(
    title = viewModel.t("Programme", "Programu"), options = programmes.map { PickerItem(it.id, it.name) },
    selectedId = prog, onSelect = { prog = it }, onDismiss = { picking = false }
  )
}

private fun generatePassword(): String {
  val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
  val r = java.security.SecureRandom()
  return (1..9).map { chars[r.nextInt(chars.length)] }.joinToString("") + "#" + (10 + r.nextInt(89))
}

@Composable
private fun ResetPasswordSheet(viewModel: CampusViewModel, name: String, error: String?, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  var pw by remember { mutableStateOf("") }
  AppBottomSheet(
    onDismissRequest = onDismiss, title = viewModel.t("Reset password", "Weka upya nenosiri"), subtitle = name, tag = "reset_password_sheet",
    footer = { SheetButtons(viewModel.t("Cancel", "Ghairi"), onDismiss, viewModel.t("Set password", "Weka nenosiri"), { onSave(pw) }, enabled = !busy && pw.length >= 8) }
  ) {
    Spacer(Modifier.height(8.dp))
    SheetField(pw, { pw = it }, viewModel.t("New password (8+ characters)", "Nenosiri jipya (herufi 8+)"), "reset_pw_field")
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { pw = generatePassword() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("reset_pw_generate")) {
      Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Generate one", "Tengeneza"))
    }
    Text(viewModel.t("Give it to the person privately. They can change it in their profile.", "Mpe mhusika kwa siri. Anaweza kuibadilisha kwenye wasifu wake."),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
  }
}

@Composable
private fun LoginEmailSheet(viewModel: CampusViewModel, email: String, error: String?, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  var e by remember { mutableStateOf(email) }
  AppBottomSheet(
    onDismissRequest = onDismiss, title = viewModel.t("Change login email", "Badilisha barua pepe ya kuingia"), tag = "login_email_sheet",
    footer = { SheetButtons(viewModel.t("Cancel", "Ghairi"), onDismiss, viewModel.t("Save", "Hifadhi"), { onSave(e) }, enabled = !busy && e.contains("@") && e.trim().lowercase() != email.lowercase()) }
  ) {
    Spacer(Modifier.height(8.dp))
    SheetField(e, { e = it }, viewModel.t("New email", "Barua pepe mpya"), "login_email_field")
    Text(viewModel.t("They will sign in with this email from now on. No confirmation email is sent.", "Wataingia kwa barua pepe hii kuanzia sasa. Hakuna barua ya uthibitisho inayotumwa."),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
  }
}

@Composable
private fun ChangeRoleSheet(
  viewModel: CampusViewModel, targets: List<String>, programmes: List<ProgrammeRef>, error: String?, busy: Boolean,
  onDismiss: () -> Unit, onSave: (String, String?, String?) -> Unit
) {
  var role by remember { mutableStateOf(targets.first()) }
  var num by remember { mutableStateOf("") }
  var prog by remember { mutableStateOf<String?>(null) }
  var picking by remember { mutableStateOf(false) }
  AppBottomSheet(
    onDismissRequest = onDismiss, title = viewModel.t("Change role", "Badilisha jukumu"), tag = "change_role_sheet",
    footer = { SheetButtons(viewModel.t("Cancel", "Ghairi"), onDismiss, viewModel.t("Change", "Badilisha"), { onSave(role, num.ifBlank { null }, prog) }, enabled = !busy && (role != "student" || prog != null)) }
  ) {
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      targets.forEach { r ->
        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(if (r == "student") viewModel.t("Student", "Mwanafunzi") else viewModel.t("Lecturer", "Mhadhiri")) })
      }
    }
    Spacer(Modifier.height(10.dp))
    if (role == "student") {
      OutlinedButton(onClick = { picking = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("role_programme")) {
        Text(programmes.firstOrNull { it.id == prog }?.name ?: viewModel.t("Choose programme", "Chagua programu"))
      }
      Spacer(Modifier.height(10.dp))
    }
    SheetField(num, { num = it }, viewModel.t("New number (optional)", "Namba mpya (si lazima)"), "role_number")
    Text(viewModel.t("Courses, class representative status and teaching assignments of the old role are removed.", "Kozi, uwakilishi na ufundishaji wa jukumu la zamani huondolewa."),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
  }
  if (picking) PickerSheet(title = viewModel.t("Programme", "Programu"), options = programmes.map { PickerItem(it.id, it.name) }, selectedId = prog, onSelect = { prog = it }, onDismiss = { picking = false })
}

// ======================================================================================
// App manager: the administrators of one campus.
// ======================================================================================

@Composable
fun CampusAdminsSheet(viewModel: CampusViewModel, campusId: String, campusCode: String, campusName: String, onDismiss: () -> Unit) {
  val admins by viewModel.managedAdmins.collectAsState()
  LaunchedEffect(Unit) { viewModel.refreshPlatformAdmins() }
  val mine = admins.filter { it.campusCode.equals(campusCode, true) }
  var open by remember { mutableStateOf<String?>(null) }
  var adding by remember { mutableStateOf(false) }

  AppBottomSheet(
    onDismissRequest = onDismiss, title = viewModel.t("Administrators", "Wasimamizi"), subtitle = campusName, tag = "campus_admins_sheet",
    footer = {
      Button(
        onClick = { adding = true }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.weight(1f).height(56.dp).testTag("add_admin_btn")
      ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Add administrator", "Ongeza msimamizi"), fontWeight = FontWeight.SemiBold) }
    }
  ) {
    Spacer(Modifier.height(6.dp))
    Text(viewModel.t("A campus can have more than one administrator.", "Chuo kinaweza kuwa na wasimamizi zaidi ya mmoja."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (mine.isEmpty()) Text(viewModel.t("No administrators yet.", "Hakuna wasimamizi bado."), color = MaterialTheme.colorScheme.onSurfaceVariant)
    mine.forEach { a ->
      Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = a.id }.padding(vertical = 10.dp).testTag("admin_row_${a.id}"), verticalAlignment = Alignment.CenterVertically) {
        Avatar(a.name); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(a.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(a.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusPill(a.status)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Spacer(Modifier.height(8.dp))
  }

  if (adding) AddCampusUserSheet(viewModel, campusId, allowAdmin = true, onDismiss = { adding = false }, initialRole = Role.ADMIN, onlyRole = true)
  open?.let { id ->
    mine.firstOrNull { it.id == id }?.let { a: ManagedAdmin ->
      PersonManageSheet(
        viewModel = viewModel, id = a.id, name = a.name, email = a.email, role = "admin", status = a.status, number = a.number,
        programmeId = null, programmeName = "", department = "", isClassRep = false, programmes = emptyList(),
        allowRoleChange = true, allowClassRep = false, roleChangeTargets = listOf("lecturer"), onDismiss = { open = null }
      )
    } ?: run { open = null }
  }
}

// ======================================================================================
// App manager: AI settings. The key is saved on the server and used for everyone; it is never shown again.
// ======================================================================================

@Composable
fun AiSettingsSheet(viewModel: CampusViewModel, onDismiss: () -> Unit) {
  val status by viewModel.aiStatus.collectAsState()
  LaunchedEffect(Unit) { viewModel.refreshAiStatus() }
  val scope = rememberCoroutineScope()
  var provider by remember(status.provider) { mutableStateOf(status.provider) }
  var model by remember(status.model) { mutableStateOf(status.model) }
  var enabled by remember(status.enabled) { mutableStateOf(status.enabled) }
  var key by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  AppBottomSheet(
    onDismissRequest = onDismiss, title = viewModel.t("AI settings", "Mipangilio ya AI"),
    subtitle = viewModel.t("Used by everyone for document analysis", "Hutumiwa na kila mtu kuchambua nyaraka"), tag = "ai_settings_sheet",
    footer = {
      SheetButtons(viewModel.t("Close", "Funga"), onDismiss, viewModel.t("Save", "Hifadhi"), {
        scope.launch { busy = true; error = viewModel.saveAiSettings(provider, model, key, enabled); busy = false; if (error == null) key = "" }
      }, enabled = !busy)
    }
  ) {
    Spacer(Modifier.height(8.dp))
    Text(
      if (status.hasKey) viewModel.t("Key saved on the server: ${status.keyHint}", "Ufunguo umehifadhiwa kwenye seva: ${status.keyHint}")
      else viewModel.t("No key saved here. If a repository secret (AI_API_KEY) is deployed, that one is used. Otherwise the on-device analysis is used.", "Hakuna ufunguo uliohifadhiwa hapa. Ikiwa siri ya hazina (AI_API_KEY) imewekwa, hiyo hutumika. Vinginevyo uchambuzi wa kwenye simu hutumika."),
      style = MaterialTheme.typography.bodySmall, color = if (status.hasKey) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("gemini" to "Gemini", "anthropic" to "Claude").forEach { (id, label) ->
        FilterChip(selected = provider == id, onClick = { provider = id }, label = { Text(label) }, modifier = Modifier.testTag("ai_provider_$id"))
      }
    }
    Spacer(Modifier.height(10.dp))
    SheetField(model, { model = it }, viewModel.t("Model (e.g. gemini-2.5-flash)", "Modeli (mf. gemini-2.5-flash)"), "ai_model")
    Spacer(Modifier.height(10.dp))
    SheetField(key, { key = it }, viewModel.t("API key (leave empty to keep the saved one)", "Ufunguo wa API (acha tupu kubaki na uliopo)"), "ai_key", password = true)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(viewModel.t("AI analysis on", "Uchambuzi wa AI umewashwa"), modifier = Modifier.weight(1f))
      Switch(checked = enabled, onCheckedChange = { enabled = it }, modifier = Modifier.testTag("ai_enabled"))
    }
    if (status.hasKey) {
      OutlinedButton(
        onClick = { scope.launch { busy = true; error = viewModel.removeAiKey(); busy = false } }, enabled = !busy,
        shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp).testTag("ai_remove_key")
      ) { Text(viewModel.t("Remove saved key", "Ondoa ufunguo uliohifadhiwa"), color = DangerRed) }
    }
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
  }
}
