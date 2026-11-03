package com.willykez.campconnect.ui.screens

import com.willykez.campconnect.ui.theme.DeepBlueDark
import com.willykez.campconnect.ui.components.EmptyState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Notifications
import com.willykez.campconnect.ui.components.ScreenHeading
import com.willykez.campconnect.ui.components.ActionButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.NotificationItem
import com.willykez.campconnect.data.model.NotificationType
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.viewmodel.CampusViewModel

private enum class NotifFilter { ALL, UNREAD, SCHEDULE, NOTICES, WORK, ACCOUNT }

private fun NotificationType.group(): NotifFilter = when (this) {
  NotificationType.TIMETABLE_CHANGE -> NotifFilter.SCHEDULE
  NotificationType.ANNOUNCEMENT -> NotifFilter.NOTICES
  NotificationType.APPROVAL -> NotifFilter.ACCOUNT
  else -> NotifFilter.WORK
}

@Composable
fun NotificationsScreen(
  viewModel: CampusViewModel,
  modifier: Modifier = Modifier
) {
  val notifications by viewModel.notifications.collectAsState()
  var filter by remember { mutableStateOf(NotifFilter.ALL) }
  val unread = notifications.count { !it.isRead }

  val shown = notifications.filter {
    when (filter) {
      NotifFilter.ALL -> true
      NotifFilter.UNREAD -> !it.isRead
      else -> it.type.group() == filter
    }
  }
  val fresh = shown.filter { !it.isRead }
  val earlier = shown.filter { it.isRead }

  Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Spacer(modifier = Modifier.height(8.dp))

    // Summary card: how many are new, and the one action that matters
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(DeepBlueDark, DeepBlue)))
        .padding(18.dp)
        .testTag("notifications_hero")
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)),
          contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(viewModel.getString("notifications"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
          Text(
            if (unread == 0) viewModel.t("You are all caught up", "Umeshasoma zote")
            else viewModel.t("$unread new of ${notifications.size}", "Mpya $unread kati ya ${notifications.size}"),
            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f)
          )
        }
        if (unread > 0) {
          Surface(
            onClick = { viewModel.markAllNotificationsRead() },
            shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.testTag("mark_all_read_btn")
          ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.DoneAll, null, tint = Color.White, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(viewModel.t("Read all", "Soma zote"), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Filters
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(NotifFilter.values().size) { i ->
        val f = NotifFilter.values()[i]
        val label = when (f) {
          NotifFilter.ALL -> viewModel.t("All", "Zote")
          NotifFilter.UNREAD -> viewModel.t("Unread", "Mpya") + if (unread > 0) " ($unread)" else ""
          NotifFilter.SCHEDULE -> viewModel.t("Schedule", "Ratiba")
          NotifFilter.NOTICES -> viewModel.t("Notices", "Matangazo")
          NotifFilter.WORK -> viewModel.t("Work", "Kazi")
          NotifFilter.ACCOUNT -> viewModel.t("Account", "Akaunti")
        }
        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(label) }, modifier = Modifier.testTag("notif_filter_${f.name}"))
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (shown.isEmpty()) {
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        EmptyState(
          icon = Icons.Default.NotificationsNone,
          title = if (notifications.isEmpty()) viewModel.t("No alerts yet", "Hakuna arifa bado") else viewModel.t("Nothing here", "Hakuna kitu hapa"),
          hint = if (notifications.isEmpty()) viewModel.t("Timetable changes, approvals and messages will show up here.", "Mabadiliko ya ratiba, idhini na ujumbe vitaonekana hapa.")
          else viewModel.t("Try another filter.", "Jaribu kichujio kingine.")
        )
      }
    } else {
      LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (fresh.isNotEmpty()) {
          item { NotifSectionLabel(viewModel.t("New", "Mpya"), fresh.size, DeepBlue) }
          items(fresh, key = { it.id }) { notif -> Box(Modifier.animateItem()) { NotificationCard(item = notif) } }
        }
        if (earlier.isNotEmpty()) {
          item { NotifSectionLabel(viewModel.t("Earlier", "Za awali"), earlier.size, MaterialTheme.colorScheme.onSurfaceVariant) }
          items(earlier, key = { it.id }) { notif -> Box(Modifier.animateItem()) { NotificationCard(item = notif) } }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
      }
    }
  }
}

@Composable
private fun NotifSectionLabel(text: String, count: Int, color: Color) {
  Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    Spacer(Modifier.width(8.dp))
    Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun NotificationCard(item: NotificationItem) {
  val icon: ImageVector
  val tint: Color
  val label: String
  when (item.type) {
    NotificationType.TIMETABLE_CHANGE -> { icon = Icons.Default.EditCalendar; tint = WarmAmber; label = "Schedule" }
    NotificationType.ANNOUNCEMENT -> { icon = Icons.Default.Campaign; tint = DeepBlue; label = "Notice" }
    NotificationType.DEADLINE_ALERT -> { icon = Icons.Default.CalendarMonth; tint = DangerRed; label = "Deadline" }
    NotificationType.APPROVAL -> { icon = Icons.Default.VerifiedUser; tint = SuccessGreen; label = "Account" }
    NotificationType.SUBMISSION_RECEIVED -> { icon = Icons.Default.Assignment; tint = DeepBlue; label = "Submission" }
    NotificationType.GRADE_POSTED -> { icon = Icons.Default.Grade; tint = SuccessGreen; label = "Grade" }
  }
  val shape = RoundedCornerShape(18.dp)

  Card(
    shape = shape,
    colors = CardDefaults.cardColors(containerColor = if (!item.isRead) tint.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, if (!item.isRead) tint.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), shape)
      .testTag("notification_card_${item.id}")
  ) {
    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
      Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = tint,
            modifier = Modifier.weight(1f)
          )
          Text(item.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (!item.isRead) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = item.title, style = MaterialTheme.typography.titleSmall,
          fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
