package com.willykez.campconnect.ui.screens

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

@Composable
fun NotificationsScreen(
  viewModel: CampusViewModel,
  modifier: Modifier = Modifier
) {
  val notifications by viewModel.notifications.collectAsState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = viewModel.getString("notifications"),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "Real-time timetable changes and alert inbox",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (notifications.any { !it.isRead }) {
        ActionButton(
          label = viewModel.t("Mark all read", "Soma zote"),
          icon = Icons.Default.DoneAll,
          onClick = { viewModel.markAllNotificationsRead() },
          tag = "mark_all_read_btn"
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (notifications.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(DeepBlue.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsNone,
              contentDescription = "No notifications",
              tint = DeepBlue,
              modifier = Modifier.size(40.dp)
            )
          }
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "No alerts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "You are all caught up on announcements and schedule changes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(notifications, key = { it.id }) { notif ->
          Box(modifier = Modifier.animateItem()) {
          NotificationCard(item = notif)
        
          }
        }

        item {
          Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }
  }
}

@Composable
fun NotificationCard(item: NotificationItem) {
  val icon: ImageVector
  val iconTint: Color
  val iconBg: Color

  when (item.type) {
    NotificationType.TIMETABLE_CHANGE -> {
      icon = Icons.Default.EditCalendar
      iconTint = WarmAmber
      iconBg = Color(0xFFFEF3C7)
    }
    NotificationType.ANNOUNCEMENT -> {
      icon = Icons.Default.Campaign
      iconTint = DeepBlue
      iconBg = Color(0xFFE0E7FF)
    }
    NotificationType.DEADLINE_ALERT -> {
      icon = Icons.Default.CalendarMonth
      iconTint = DangerRed
      iconBg = Color(0xFFFEE2E2)
    }
    NotificationType.APPROVAL -> {
      icon = Icons.Default.VerifiedUser
      iconTint = SuccessGreen
      iconBg = Color(0xFFDCFCE7)
    }
    NotificationType.SUBMISSION_RECEIVED -> {
      icon = Icons.Default.Assignment
      iconTint = DeepBlue
      iconBg = Color(0xFFE0E7FF)
    }
    NotificationType.GRADE_POSTED -> {
      icon = Icons.Default.Grade
      iconTint = SuccessGreen
      iconBg = Color(0xFFDCFCE7)
    }
  }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (!item.isRead) DeepBlue.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = if (!item.isRead) DeepBlue.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        shape = RoundedCornerShape(18.dp)
      )
      .testTag("notification_card_${item.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(iconBg),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = item.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = item.message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
