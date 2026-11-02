package com.willykez.campconnect.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.repository.CampusSummary
import com.willykez.campconnect.data.repository.WipeScope
import com.willykez.campconnect.ui.components.ConfirmSheet
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.DeepBlueDark
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.TransparentDialogBars
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.viewmodel.CampusViewModel
import kotlinx.coroutines.launch

private enum class RoomTab { OVERVIEW, ADMINS, MESSAGE, DATA, DANGER }

private data class WipeItem(
  val icon: ImageVector, val title: String, val hint: String, val count: Int?, val scope: WipeScope?
)

/**
 * The app manager's full-screen room for ONE campus: status, details, administrators, messages,
 * cleaning up content, and the danger zone. It shows totals only, never what people wrote.
 */
@Composable
fun CampusControlRoom(campus: CampusSummary, viewModel: CampusViewModel, onDismiss: () -> Unit) {
  val uni = campus.university
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  val message by viewModel.message.collectAsState()
  var tab by remember { mutableStateOf(RoomTab.OVERVIEW) }
  var sheet by remember { mutableStateOf(0) } // 1 offline, 2 edit, 3 add person
  var confirm by remember { mutableStateOf<WipeItem?>(null) }
  var typed by remember { mutableStateOf("") }

  // While this room is open it shows the messages itself (the screen behind it is covered).
  LaunchedEffect(message) {
    message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    TransparentDialogBars(lightIcons = false)
    Surface(modifier = Modifier.fillMaxSize().testTag("campus_control_room"), color = MaterialTheme.colorScheme.background) {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
          // ---- Header: the colour runs behind the status bar, nothing is cut off ----
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
              .background(Brush.linearGradient(listOf(DeepBlueDark, DeepBlue)))
              .statusBarsPadding()
              .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.testTag("room_close")) {
                Icon(Icons.Default.Close, viewModel.t("Close", "Funga"), tint = Color.White)
              }
              Column(Modifier.weight(1f)) {
                Text(uni.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                  listOf(uni.shortName, uni.location).filter { it.isNotBlank() }.joinToString("  ·  "),
                  style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f)
                )
              }
              StatusChip(online = !uni.underMaintenance, viewModel = viewModel)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              HeaderStat("${campus.people}", viewModel.t("People", "Watu"), Modifier.weight(1f))
              HeaderStat("${campus.pending}", viewModel.t("Waiting", "Wanasubiri"), Modifier.weight(1f), highlight = campus.pending > 0)
              HeaderStat("${campus.admins}", viewModel.t("Admins", "Wasimamizi"), Modifier.weight(1f))
              HeaderStat("${campus.courses}", viewModel.t("Courses", "Kozi"), Modifier.weight(1f))
            }
          }

          ScrollableTabRow(
            selectedTabIndex = tab.ordinal, edgePadding = 12.dp, containerColor = Color.Transparent,
            contentColor = DeepBlue, divider = {}
          ) {
            RoomTab.values().forEach { t ->
              val label = when (t) {
                RoomTab.OVERVIEW -> viewModel.t("Overview", "Muhtasari")
                RoomTab.ADMINS -> viewModel.t("Administrators", "Wasimamizi")
                RoomTab.MESSAGE -> viewModel.t("Message", "Ujumbe")
                RoomTab.DATA -> viewModel.t("Clean up", "Safisha")
                RoomTab.DANGER -> viewModel.t("Danger zone", "Eneo la hatari")
              }
              Tab(
                selected = tab == t, onClick = { tab = t }, modifier = Modifier.testTag("room_tab_${t.name}"),
                text = {
                  Text(label, maxLines = 1, fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                    color = if (t == RoomTab.DANGER && tab != t) DangerRed.copy(alpha = 0.8f) else Color.Unspecified)
                }
              )
            }
          }

          Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
              RoomTab.OVERVIEW -> OverviewPanel(campus, viewModel, onOffline = { sheet = 1 }, onEdit = { sheet = 2 }, onAdd = { sheet = 3 }, onAdmins = { tab = RoomTab.ADMINS })
              RoomTab.ADMINS -> AdminsPanel(campus, viewModel, onAdd = { sheet = 3 })
              RoomTab.MESSAGE -> MessagePanel(campus, viewModel)
              RoomTab.DATA -> WipePanel(campus, viewModel, danger = false) { typed = ""; confirm = it }
              RoomTab.DANGER -> WipePanel(campus, viewModel, danger = true) { typed = ""; confirm = it }
            }
          }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
      }
    }
  }

  when (sheet) {
    1 -> MaintenanceSheet(viewModel, uni) { sheet = 0 }
    2 -> EditCampusDetailsSheet(viewModel, uni, canRename = true) { sheet = 0 }
    3 -> AddCampusUserSheet(viewModel, uni.id, allowAdmin = true) { sheet = 0 }
  }

  confirm?.let { item ->
    val matches = typed.trim().equals(uni.shortName, ignoreCase = true)
    val closesRoom = item.scope == null || item.scope == WipeScope.EVERYTHING
    ConfirmSheet(
      title = viewModel.t("${item.title}?", "${item.title}?"),
      message = viewModel.t(
        "This removes it from ${uni.name} for everyone and cannot be undone. Type ${uni.shortName} to confirm.",
        "Hii huiondoa kwa kila mtu wa ${uni.name} na haiwezi kurudishwa. Andika ${uni.shortName} kuthibitisha."
      ),
      confirmLabel = viewModel.t("Yes, delete", "Ndiyo, futa"), dismissLabel = viewModel.t("Cancel", "Ghairi"),
      confirmEnabled = matches, onDismiss = { confirm = null },
      onConfirm = {
        val s = item.scope
        if (s == null) viewModel.deleteCampus(uni.id) else viewModel.wipeCampus(uni.id, s)
        confirm = null
        if (closesRoom) onDismiss()
        else Toast.makeText(context, viewModel.t("Clearing… the numbers update in a moment.", "Inafuta… namba zitasasishwa muda mfupi."), Toast.LENGTH_SHORT).show()
      },
      confirmTag = "wipe_confirm_btn",
      extra = {
        OutlinedTextField(
          value = typed, onValueChange = { typed = it }, singleLine = true,
          placeholder = { Text(uni.shortName) }, shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("wipe_confirm_text")
        )
      }
    )
  }
}

@Composable
private fun StatusChip(online: Boolean, viewModel: CampusViewModel) {
  val c = if (online) SuccessGreen else WarmAmber
  Row(
    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(c))
    Spacer(Modifier.width(6.dp))
    Text(if (online) viewModel.t("Online", "Mtandaoni") else viewModel.t("Offline", "Nje ya mtandao"), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun HeaderStat(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
  Column(
    modifier.clip(RoundedCornerShape(12.dp)).background(if (highlight) WarmAmber.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.16f)).padding(vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
  }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Card2(modifier: Modifier = Modifier, border: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), content: @Composable () -> Unit) {
  Box(
    modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
      .border(1.dp, border, RoundedCornerShape(16.dp)).padding(14.dp)
  ) { content() }
}

// ---------------------------------- Overview ----------------------------------

@Composable
private fun OverviewPanel(campus: CampusSummary, viewModel: CampusViewModel, onOffline: () -> Unit, onEdit: () -> Unit, onAdd: () -> Unit, onAdmins: () -> Unit) {
  val uni = campus.university
  Panel {
    // Status
    val tint = if (uni.underMaintenance) WarmAmber else SuccessGreen
    Card2(border = tint.copy(alpha = 0.5f)) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PowerSettingsNew, null, tint = tint)
          }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(
              if (uni.underMaintenance) viewModel.t("Campus is offline", "Chuo kiko nje ya mtandao") else viewModel.t("Campus is online", "Chuo kiko mtandaoni"),
              fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
            )
            Text(
              if (uni.underMaintenance) viewModel.t("Everyone there sees the maintenance screen.", "Kila mtu huko huona skrini ya matengenezo.")
              else viewModel.t("Students, lecturers and administrators can use the app.", "Wanafunzi, wahadhiri na wasimamizi wanaweza kutumia programu."),
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        if (uni.underMaintenance && uni.maintenanceNote.isNotBlank()) {
          Spacer(Modifier.height(8.dp))
          Text("“${uni.maintenanceNote}”", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = onOffline, shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = tint),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("manage_maintenance")
        ) { Text(if (uni.underMaintenance) viewModel.t("Bring online", "Rudisha mtandaoni") else viewModel.t("Start maintenance (offline)", "Anza matengenezo (nje ya mtandao)"), fontWeight = FontWeight.SemiBold, color = Color.White) }
      }
    }

    SectionTitle(viewModel.t("What is on this campus", "Kilichopo katika chuo hiki"))
    StatGrid(
      listOf(
        Stat(Icons.Default.Groups, viewModel.t("People", "Watu"), campus.people),
        Stat(Icons.Default.HourglassTop, viewModel.t("Waiting", "Wanasubiri"), campus.pending),
        Stat(Icons.Default.AdminPanelSettings, viewModel.t("Admins", "Wasimamizi"), campus.admins),
        Stat(Icons.Default.School, viewModel.t("Programmes", "Programu"), campus.programmes),
        Stat(Icons.Default.MenuBook, viewModel.t("Courses", "Kozi"), campus.courses),
        Stat(Icons.Default.MeetingRoom, viewModel.t("Rooms", "Vyumba"), campus.rooms),
        Stat(Icons.Default.CalendarMonth, viewModel.t("Timetable", "Ratiba"), campus.sessions),
        Stat(Icons.Default.Campaign, viewModel.t("Notices", "Matangazo"), campus.announcements),
        Stat(Icons.Default.Assignment, viewModel.t("Deadlines", "Tarehe"), campus.works)
      )
    )

    SectionTitle(viewModel.t("Campus details", "Taarifa za chuo"))
    Card2 {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailLine(Icons.Default.LocationOn, uni.location.ifBlank { viewModel.t("No location yet", "Hakuna mahali bado") })
        DetailLine(Icons.Default.Email, uni.contactEmail.ifBlank { viewModel.t("No contact email yet", "Hakuna barua pepe bado") })
        DetailLine(Icons.Default.Phone, uni.contactPhone.ifBlank { viewModel.t("No phone yet", "Hakuna simu bado") })
        OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("manage_edit_details")) {
          Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Edit name and details", "Hariri jina na taarifa"))
        }
      }
    }

    SectionTitle(viewModel.t("Quick actions", "Vitendo vya haraka"))
    ActionTile(Icons.Default.AdminPanelSettings, viewModel.t("Administrators", "Wasimamizi"), viewModel.t("Reset passwords, change logins and roles, pause", "Weka upya nenosiri, badilisha kuingia na majukumu, simamisha"), "manage_admins", onAdmins)
    ActionTile(Icons.Default.PersonAdd, viewModel.t("Add a person", "Ongeza mtu"), viewModel.t("Administrator, lecturer or student", "Msimamizi, mhadhiri au mwanafunzi"), "manage_add_user", onAdd)
  }
}

private data class Stat(val icon: ImageVector, val label: String, val value: Int)

@Composable
private fun StatGrid(stats: List<Stat>) {
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
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(10.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
internal fun ActionTile(icon: ImageVector, title: String, hint: String, tag: String, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
      .clickable(onClick = onClick).padding(14.dp).testTag(tag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(DeepBlue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
      Icon(icon, null, tint = DeepBlue)
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

// ---------------------------------- Administrators ----------------------------------

@Composable
private fun AdminsPanel(campus: CampusSummary, viewModel: CampusViewModel, onAdd: () -> Unit) {
  val admins by viewModel.managedAdmins.collectAsState()
  LaunchedEffect(Unit) { viewModel.refreshPlatformAdmins() }
  val mine = admins.filter { it.campusCode.equals(campus.university.shortName, true) }
  var open by remember { mutableStateOf<String?>(null) }

  Panel {
    Text(
      viewModel.t("A campus can have more than one administrator. Tap one to manage them.", "Chuo kinaweza kuwa na wasimamizi zaidi ya mmoja. Gusa mmoja kumsimamia."),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (mine.isEmpty()) {
      Card2 { Text(viewModel.t("No administrators yet. Add the first one.", "Hakuna wasimamizi bado. Ongeza wa kwanza."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    mine.forEach { a ->
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
          .clickable { open = a.id }.padding(14.dp).testTag("admin_row_${a.id}"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Avatar(a.name); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(a.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(a.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (a.paused) Text("PAUSED", color = WarmAmber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Button(
      onClick = onAdd, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("add_admin_btn")
    ) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Add a person", "Ongeza mtu"), fontWeight = FontWeight.SemiBold) }
  }

  open?.let { id ->
    mine.firstOrNull { it.id == id }?.let { a ->
      PersonManageSheet(
        viewModel = viewModel, id = a.id, name = a.name, email = a.email, role = "admin", status = a.status, number = a.number,
        programmeId = null, programmeName = "", department = "", isClassRep = false, programmes = emptyList(),
        allowRoleChange = true, allowClassRep = false, roleChangeTargets = listOf("lecturer"), onDismiss = { open = null }
      )
    } ?: run { open = null }
  }
}

// ---------------------------------- Message ----------------------------------

@Composable
private fun MessagePanel(campus: CampusSummary, viewModel: CampusViewModel) {
  val scope = rememberCoroutineScope()
  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var urgent by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  Panel {
    Text(
      viewModel.t(
        "Send a notification to the ${campus.admins} administrator(s) of ${campus.university.shortName}. It appears in their notifications.",
        "Tuma arifa kwa wasimamizi ${campus.admins} wa ${campus.university.shortName}. Itaonekana kwenye arifa zao."
      ),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(viewModel.t("Title", "Kichwa")) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("room_msg_title"))
    OutlinedTextField(body, { body = it }, minLines = 4, label = { Text(viewModel.t("Message", "Ujumbe")) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("room_msg_body"))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(viewModel.t("Mark as urgent", "Weka kuwa ya dharura"), fontWeight = FontWeight.SemiBold)
        Text(viewModel.t("Urgent messages stand out.", "Ujumbe wa dharura huonekana zaidi."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Switch(urgent, { urgent = it })
    }
    error?.let { Text(it, color = DangerRed, style = MaterialTheme.typography.bodySmall) }
    Button(
      onClick = { scope.launch { busy = true; error = viewModel.sendToCampusAdmins(campus.university.id, title, body, urgent); busy = false; if (error == null) { title = ""; body = ""; urgent = false } } },
      enabled = !busy && title.isNotBlank() && body.isNotBlank(), shape = RoundedCornerShape(14.dp),
      colors = ButtonDefaults.buttonColors(containerColor = DeepBlue), modifier = Modifier.fillMaxWidth().height(52.dp).testTag("room_msg_send")
    ) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text(viewModel.t("Send", "Tuma"), fontWeight = FontWeight.SemiBold) }
  }
}

// ---------------------------------- Clean up / danger ----------------------------------

@Composable
private fun WipePanel(campus: CampusSummary, viewModel: CampusViewModel, danger: Boolean, onPick: (WipeItem) -> Unit) {
  val items = if (!danger) listOf(
    WipeItem(Icons.Default.Campaign, viewModel.t("Clear notices", "Futa matangazo"), viewModel.t("All announcements", "Matangazo yote"), campus.announcements, WipeScope.ANNOUNCEMENTS),
    WipeItem(Icons.Default.CalendarMonth, viewModel.t("Clear timetable", "Futa ratiba"), viewModel.t("Every class session", "Vipindi vyote vya darasa"), campus.sessions, WipeScope.TIMETABLE),
    WipeItem(Icons.Default.MenuBook, viewModel.t("Clear study materials", "Futa vifaa vya masomo"), viewModel.t("Notes and uploaded files", "Notisi na mafaili"), campus.materials, WipeScope.MATERIALS),
    WipeItem(Icons.Default.Assignment, viewModel.t("Clear deadlines and hand-ins", "Futa tarehe za mwisho na kazi"), viewModel.t("Assignments and submissions", "Kazi na majibu"), campus.works, WipeScope.WORKS),
    WipeItem(Icons.Default.Notifications, viewModel.t("Clear notifications", "Futa arifa"), viewModel.t("Everyone's notification list", "Orodha ya arifa za wote"), null, WipeScope.NOTIFICATIONS),
    WipeItem(Icons.Default.Category, viewModel.t("Clear programmes, courses and rooms", "Futa programu, kozi na vyumba"), viewModel.t("The academic setup", "Mpangilio wa masomo"), campus.programmes + campus.courses + campus.rooms, WipeScope.CATALOG),
    WipeItem(Icons.Default.Groups, viewModel.t("Remove students and lecturers", "Ondoa wanafunzi na wahadhiri"), viewModel.t("Accounts are deleted. Administrators stay.", "Akaunti zinafutwa. Wasimamizi hubaki."), (campus.people - campus.admins).coerceAtLeast(0), WipeScope.PEOPLE)
  ) else listOf(
    WipeItem(Icons.Default.Warning, viewModel.t("Erase everything", "Futa kila kitu"), viewModel.t("All data AND all administrators. The campus stays, empty.", "Data yote NA wasimamizi wote. Chuo kinabaki, tupu."), null, WipeScope.EVERYTHING),
    WipeItem(Icons.Default.DeleteForever, viewModel.t("Delete this campus completely", "Futa chuo hiki kabisa"), viewModel.t("The campus and everything in it disappear for good.", "Chuo na kila kitu ndani yake vinatoweka kabisa."), null, null)
  )
  Panel {
    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DangerRed.copy(alpha = 0.1f)).padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(Icons.Default.Warning, null, tint = DangerRed)
      Spacer(Modifier.width(10.dp))
      Text(
        if (danger) viewModel.t("These cannot be undone. You will be asked to type the campus code.", "Hivi haviwezi kurudishwa. Utaombwa kuandika msimbo wa chuo.")
        else viewModel.t("Clearing removes it for everyone on this campus and cannot be undone. Pause the campus first if people are using it.", "Kufuta huondoa kwa kila mtu wa chuo hiki na hakurudishwi. Simamisha chuo kwanza ikiwa watu wanakitumia."),
        style = MaterialTheme.typography.bodySmall
      )
    }
    items.forEach { item ->
      val empty = item.count == 0
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
          .border(1.dp, if (danger) DangerRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
          .padding(14.dp).testTag("wipe_${item.scope?.name ?: "CAMPUS"}"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(DangerRed.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
          Icon(item.icon, null, tint = DangerRed)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(item.title, fontWeight = FontWeight.SemiBold)
          Text(
            item.hint + (item.count?.let { "  ·  $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        OutlinedButton(
          onClick = { onPick(item) }, enabled = !empty, shape = RoundedCornerShape(10.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, if (empty) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else DangerRed)
        ) { Text(if (empty) viewModel.t("Empty", "Tupu") else viewModel.t("Delete", "Futa"), color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else DangerRed) }
      }
    }
  }
}
