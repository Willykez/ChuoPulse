package com.willykez.campconnect.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.willykez.campconnect.data.docs.DocumentKind
import com.willykez.campconnect.data.docs.LecturerRef
import com.willykez.campconnect.data.docs.PSession
import com.willykez.campconnect.data.docs.TimetableProposal
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.viewmodel.AssistantPhase
import com.willykez.campconnect.viewmodel.CampusViewModel

private val DAY_NAMES = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
fun TimetableAssistantCard(viewModel: CampusViewModel, onOpen: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = DeepBlue.copy(alpha = 0.08f),
    border = BorderStroke(1.dp, DeepBlue.copy(alpha = 0.25f)),
    modifier = Modifier.fillMaxWidth().testTag("setup_ai_assistant")
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI", tint = DeepBlue)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = viewModel.t("AI timetable assistant", "Msaidizi wa ratiba (AI)"),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
      Text(
        text = viewModel.t(
          "Upload a timetable or course list (PDF, Word, Excel, CSV or a photo). The AI proposes the programmes, courses, lecturers and weekly sessions. You review and fix everything before it is added.",
          "Pakia ratiba au orodha ya kozi (PDF, Word, Excel, CSV au picha). AI inapendekeza programu, kozi, wahadhiri na vipindi. Unakagua na kurekebisha kabla ya kuongezwa."
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Button(onClick = onOpen, modifier = Modifier.testTag("ai_assistant_open")) {
        Text(viewModel.t("Open assistant", "Fungua msaidizi"))
      }
    }
  }
}

@Composable
fun TimetableAssistantScreen(viewModel: CampusViewModel, onClose: () -> Unit) {
  val model = viewModel.timetableAssistant
  val phase by model.phase.collectAsState()
  val context = LocalContext.current
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) model.start(context, uri, context.contentResolver.getType(uri))
  }
  val close = { model.reset(); onClose() }

  Dialog(
    onDismissRequest = { if (phase !is AssistantPhase.Working) close() },
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
  ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = viewModel.t("AI timetable assistant", "Msaidizi wa ratiba (AI)"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
          )
          IconButton(onClick = close, modifier = Modifier.testTag("ai_assistant_close")) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
          }
        }
        when (val p = phase) {
          is AssistantPhase.Idle -> IdleView(viewModel) { picker.launch(DocumentKind.pickerMimeTypes) }
          is AssistantPhase.Working -> WorkingView(p.label)
          is AssistantPhase.Review -> ReviewView(viewModel, p.proposal, p.fileName)
          is AssistantPhase.Done -> DoneView(viewModel, p, close)
          is AssistantPhase.Failed -> FailedView(viewModel, p.message, { model.reset() }, close)
        }
      }
    }
  }
}

@Composable
private fun IdleView(viewModel: CampusViewModel, onPick: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Text(
      text = viewModel.t("How it works", "Inavyofanya kazi"),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    listOf(
      viewModel.t("1. Choose a file with your timetable, course list or lecturer assignments.", "1. Chagua faili lenye ratiba, orodha ya kozi au wahadhiri."),
      viewModel.t("2. The AI reads it and proposes what to add. It never saves anything by itself.", "2. AI inaisoma na kupendekeza cha kuongeza. Haihifadhi chochote yenyewe."),
      viewModel.t("3. You tick, edit or remove each item. Clashes with the existing timetable are shown.", "3. Unachagua, kuhariri au kuondoa kila kipengele. Migongano inaonyeshwa."),
      viewModel.t("4. Tap Add. Only the items you approved are added.", "4. Gusa Ongeza. Vilivyoidhinishwa pekee ndivyo vinaongezwa.")
    ).forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
    Text(
      text = viewModel.t(
        "Works best with clear text. Photos and scans are read with text recognition, so check them carefully.",
        "Inafanya kazi vizuri na maandishi wazi. Picha husomwa kwa utambuzi wa maandishi, kwa hiyo zikague kwa makini."
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(onClick = onPick, modifier = Modifier.fillMaxWidth().testTag("ai_assistant_pick")) {
      Text(viewModel.t("Choose file", "Chagua faili"))
    }
  }
}

@Composable
private fun WorkingView(label: String) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    CircularProgressIndicator()
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ai_assistant_working"))
  }
}

@Composable
private fun FailedView(viewModel: CampusViewModel, message: String, onRetry: () -> Unit, onClose: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(text = message, style = MaterialTheme.typography.bodyLarge, color = DangerRed, modifier = Modifier.testTag("ai_assistant_error"))
    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Button(onClick = onRetry) { Text(viewModel.t("Try again", "Jaribu tena")) }
      OutlinedButton(onClick = onClose) { Text(viewModel.t("Close", "Funga")) }
    }
  }
}

@Composable
private fun DoneView(viewModel: CampusViewModel, done: AssistantPhase.Done, onClose: () -> Unit) {
  val r = done.report
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    item {
      Text(
        text = if (r.failures.isEmpty()) viewModel.t("Done. The timetable is updated.", "Imekamilika. Ratiba imesasishwa.")
        else viewModel.t("Finished with some problems.", "Imekamilika na matatizo kadhaa."),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (r.failures.isEmpty()) SuccessGreen else DangerRed,
        modifier = Modifier.testTag("ai_assistant_done")
      )
    }
    item {
      Text(
        text = "${r.programmes} programmes, ${r.courses} courses, ${r.rooms} rooms, ${r.assignments} lecturer assignments, ${r.sessions} sessions added.",
        style = MaterialTheme.typography.bodyMedium
      )
    }
    if (r.failures.isNotEmpty()) {
      item { Text(text = viewModel.t("Not added:", "Havikuongezwa:"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
      items(r.failures) { Text(text = "• $it", style = MaterialTheme.typography.bodySmall, color = DangerRed) }
    }
    item { Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(viewModel.t("Close", "Funga")) } }
  }
}

@Composable
private fun ReviewView(viewModel: CampusViewModel, p: TimetableProposal, fileName: String) {
  val model = viewModel.timetableAssistant
  var editing by remember { mutableStateOf<PSession?>(null) }
  var confirming by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.weight(1f).testTag("ai_assistant_review"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      item {
        Text(
          text = viewModel.t(
            "Review what the AI found in $fileName. Nothing is saved until you tap Add.",
            "Kagua alichopata AI katika $fileName. Hakuna kinachohifadhiwa hadi uguse Ongeza."
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      if (p.warnings.isNotEmpty()) {
        item {
          Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFEF3C7), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(text = viewModel.t("Notes from the AI", "Maelezo kutoka AI"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              p.warnings.take(8).forEach { Text(text = "• $it", style = MaterialTheme.typography.bodySmall) }
            }
          }
        }
      }
      if (p.programmes.isNotEmpty()) {
        item { SectionTitle(viewModel.t("New programmes", "Programu mpya"), p.programmes.size) }
        items(p.programmes) { g -> CheckRow(g.include, g.name, null, true) { model.toggleProgramme(g.name) } }
      }
      if (p.courses.isNotEmpty()) {
        item { SectionTitle(viewModel.t("New courses", "Kozi mpya"), p.courses.size) }
        items(p.courses) { c ->
          CheckRow(c.include, "${c.code} · ${c.title}", c.programme.takeIf { it.isNotBlank() }, true) { model.toggleCourse(c.code) }
        }
      }
      if (p.assignments.isNotEmpty()) {
        item { SectionTitle(viewModel.t("Lecturers for courses", "Wahadhiri wa kozi"), p.assignments.size) }
        itemsIndexed(p.assignments) { i, a ->
          val matched = a.lecturerId != null
          CheckRow(
            checked = a.include && matched,
            title = "${a.lecturerName} → ${a.course}",
            subtitle = if (matched) null else viewModel.t("No matching lecturer on your campus. Skipped.", "Hakuna mhadhiri anayelingana. Imerukwa."),
            enabled = matched
          ) { model.toggleAssignment(i) }
        }
      }
      if (p.sessions.isNotEmpty()) {
        item { SectionTitle(viewModel.t("Weekly sessions", "Vipindi vya wiki"), p.sessions.size) }
        items(p.sessions, key = { it.id }) { s ->
          SessionCard(s, onToggle = { model.toggleSession(s.id) }, onEdit = { editing = s })
        }
      }
    }
    Surface(shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(onClick = { model.reset() }) { Text(viewModel.t("Start over", "Anza upya")) }
        Button(
          onClick = { confirming = true },
          enabled = p.itemsToApply > 0,
          modifier = Modifier.weight(1f).testTag("ai_assistant_apply")
        ) {
          Text(viewModel.t("Add ${p.itemsToApply} items", "Ongeza vipengele ${p.itemsToApply}"))
        }
      }
    }
  }

  editing?.let { s ->
    EditSessionDialog(
      session = s,
      lecturers = model.lecturers(),
      viewModel = viewModel,
      onSave = { updated -> model.editSession(s.id) { updated }; editing = null },
      onDismiss = { editing = null }
    )
  }

  if (confirming) {
    AlertDialog(
      onDismissRequest = { confirming = false },
      title = { Text(viewModel.t("Add to the timetable?", "Ongeza kwenye ratiba?")) },
      text = {
        Text(
          viewModel.t(
            "${p.itemsToApply} items will be added to your campus. Students and lecturers will see the sessions.",
            "Vipengele ${p.itemsToApply} vitaongezwa chuoni kwako. Wanafunzi na wahadhiri wataona vipindi."
          )
        )
      },
      confirmButton = { TextButton(onClick = { confirming = false; model.apply() }) { Text(viewModel.t("Add", "Ongeza")) } },
      dismissButton = { TextButton(onClick = { confirming = false }) { Text(viewModel.t("Cancel", "Ghairi")) } }
    )
  }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
  Text(
    text = "$title ($count)",
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.padding(top = 10.dp)
  )
}

@Composable
private fun CheckRow(checked: Boolean, title: String, subtitle: String?, enabled: Boolean, onToggle: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onToggle() },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.bodyMedium)
      if (subtitle != null) {
        Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun SessionCard(s: PSession, onToggle: () -> Unit, onEdit: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, if (s.blocked) DangerRed.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    modifier = Modifier.fillMaxWidth().testTag("ai_session_${s.id}")
  ) {
    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
      Checkbox(checked = s.willApply, onCheckedChange = { onToggle() }, enabled = !s.blocked || s.include)
      Column(modifier = Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "${s.course} · ${DAY_NAMES.getOrElse(s.day) { "?" }} ${s.start}–${s.end}",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "${s.venue.ifBlank { "No venue" }} · ${s.lecturerName.ifBlank { "No lecturer" }}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        s.issues.forEach { Text(text = it.text, style = MaterialTheme.typography.labelSmall, color = DangerRed) }
      }
      TextButton(onClick = onEdit, modifier = Modifier.testTag("ai_session_edit_${s.id}")) { Text("Edit") }
    }
  }
}

@Composable
private fun EditSessionDialog(
  session: PSession,
  lecturers: List<LecturerRef>,
  viewModel: CampusViewModel,
  onSave: (PSession) -> Unit,
  onDismiss: () -> Unit
) {
  var course by remember { mutableStateOf(session.course) }
  var venue by remember { mutableStateOf(session.venue) }
  var start by remember { mutableStateOf(session.start) }
  var end by remember { mutableStateOf(session.end) }
  var day by remember { mutableStateOf(session.day) }
  var lecturerId by remember { mutableStateOf(session.lecturerId) }
  var lecturerName by remember { mutableStateOf(session.lecturerName) }
  var menu by remember { mutableStateOf(false) }

  fun time(raw: String): String {
    val t = raw.trim()
    return if (Regex("^\\d:\\d{2}$").matches(t)) "0$t" else t
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(viewModel.t("Edit session", "Hariri kipindi")) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = course, onValueChange = { course = it.uppercase() }, label = { Text("Course code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          (1..6).forEach { d ->
            val on = d == day
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (on) DeepBlue else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { day = d }
            ) {
              Text(
                text = DAY_NAMES[d],
                style = MaterialTheme.typography.labelMedium,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
          OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("Venue") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Box {
          OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (lecturerId == null) viewModel.t("Choose lecturer", "Chagua mhadhiri") else lecturerName)
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            lecturers.forEach { l ->
              DropdownMenuItem(
                text = { Text(l.name) },
                onClick = { lecturerId = l.id; lecturerName = l.name; menu = false }
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onSave(session.copy(course = course.trim().uppercase(), venue = venue.trim(), start = time(start), end = time(end), day = day, lecturerId = lecturerId, lecturerName = lecturerName, include = true))
      }) { Text(viewModel.t("Save", "Hifadhi")) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(viewModel.t("Cancel", "Ghairi")) } }
  )
}
