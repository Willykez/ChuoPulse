package com.willykez.campconnect.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.CalendarToday
import com.willykez.campconnect.ui.components.ActionButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.EventAvailable
import com.willykez.campconnect.ui.theme.DeepBlueDark
import com.willykez.campconnect.ui.theme.PrimaryLightBlue
import com.willykez.campconnect.util.AppBrand
import com.willykez.campconnect.data.model.SessionStatus
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import com.willykez.campconnect.ui.util.syncStatusText
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.campconnect.data.model.AccountStatus
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.ui.components.AnnouncementCard
import com.willykez.campconnect.ui.components.DeadlineCard
import com.willykez.campconnect.ui.components.OfflineBanner
import com.willykez.campconnect.ui.components.SessionCard
import com.willykez.campconnect.ui.components.StatusBadge
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.WarmAmberLight
import com.willykez.campconnect.viewmodel.CampusViewModel
import com.willykez.campconnect.viewmodel.ScreenTab

@Composable
fun HomeScreen(
  viewModel: CampusViewModel,
  onNavigateToNotifications: () -> Unit,
  onOpenAnnouncementDetail: (String) -> Unit,
  onOpenPublishDialog: () -> Unit,
  onOpenChangeVenueDialog: (String) -> Unit,
  onOpenUploadDialog: () -> Unit,
  onOpenAddDeadlineDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val isOffline by viewModel.isOffline.collectAsState()
  val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
  val language by viewModel.appLanguage.collectAsState()
  val is24Hour by viewModel.is24HourFormat.collectAsState()
  val now by produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
      delay(30_000L)
      value = System.currentTimeMillis()
    }
  }
  val sessions by viewModel.sessions.collectAsState()
  val announcements by viewModel.announcements.collectAsState()
  val deadlines by viewModel.deadlines.collectAsState()
  val pendingUsers by viewModel.pendingUsers.collectAsState()
  val unreadNotifs by viewModel.unreadNotificationsCount.collectAsState()

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Spacer(modifier = Modifier.height(8.dp))
      val zone = java.time.ZoneId.systemDefault()
      val moment = java.time.Instant.ofEpochMilli(now).atZone(zone)
      val locale = java.util.Locale.forLanguageTag(if (language == com.willykez.campconnect.data.model.AppLanguage.SWAHILI) "sw" else "en")
      val dateText = moment.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
      val timeText = moment.format(java.time.format.DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale))
      val todayNumber = moment.dayOfWeek.value // 1 = Monday ... 7 = Sunday, from the phone's real clock
      val classesToday = sessions.count { it.dayOfWeek == todayNumber && it.status != SessionStatus.CANCELLED }
      val openDeadlines = deadlines.count { !it.isCompleted }
      val firstName = user.fullName.split(" ").firstOrNull() ?: user.fullName
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(26.dp))
          .background(Brush.linearGradient(listOf(DeepBlueDark, DeepBlue, PrimaryLightBlue)))
          .drawBehind {
            // Soft rings in the corner give the card depth without adding anything to read
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.95f, size.height * 0.05f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.78f, size.height * 1.0f))
          }
          .padding(18.dp)
          .testTag("home_hero")
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "$dateText  \u2022  $timeText",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "${viewModel.getString("greeting")}, $firstName",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = user.programme ?: AppBrand.NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
              )
            }
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .clickable { onNavigateToNotifications() }
                .testTag("notification_bell_btn"),
              contentAlignment = Alignment.Center
            ) {
              BadgedBox(
                badge = {
                  if (unreadNotifs > 0) {
                    Badge(containerColor = WarmAmber, contentColor = Color.White) { Text(text = "$unreadNotifs") }
                  }
                }
              ) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White, modifier = Modifier.size(24.dp))
              }
            }
          }
          Spacer(modifier = Modifier.height(16.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(
              value = "$classesToday",
              label = viewModel.t("classes today", "vipindi leo"),
              modifier = Modifier.weight(1f).clickable { viewModel.setTab(ScreenTab.TIMETABLE) }
            )
            HeroStat(
              value = "$openDeadlines",
              label = viewModel.t("open deadlines", "tarehe zinazosubiri"),
              modifier = Modifier.weight(1f).clickable { viewModel.setTab(ScreenTab.CALENDAR) }
            )
            HeroStat(
              value = "$unreadNotifs",
              label = viewModel.t("new alerts", "arifa mpya"),
              modifier = Modifier.weight(1f).clickable { onNavigateToNotifications() }
            )
          }
        }
      }
    }

    // Pending Account Warning (if user status is PENDING)
    if (user.status == AccountStatus.PENDING) {
      item {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = WarmAmberLight),
          modifier = Modifier.fillMaxWidth().testTag("pending_status_card")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.AddAlert,
              contentDescription = "Pending Approval",
              tint = Color(0xFFB45309),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Guest Access • Account Pending Registry Approval",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF78350F)
              )
              Text(
                text = "You have full access to view all timetables, announcements, study materials, and deadlines freely while waiting for verification.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF92400E)
              )
            }
          }
        }
      }
    }

    // Offline / Sync Status Banner
    item {
      OfflineBanner(
        isOffline = isOffline,
        statusText = syncStatusText(isOffline, lastSyncedAt, now, language),
        onSyncClick = { viewModel.triggerSync() }
      )
    }

    // Role-specific Home Content
    when (user.role) {
      Role.STUDENT -> {
        // Next Upcoming Deadline
        val nextDeadline = deadlines.firstOrNull { !it.isCompleted }
        if (nextDeadline != null) {
          item {
            HomeSectionHeader(viewModel.getString("next_deadline"), viewModel.t("View all", "Ona zote")) { viewModel.setTab(ScreenTab.CALENDAR) }
            Spacer(modifier = Modifier.height(8.dp))
            DeadlineCard(
              deadline = nextDeadline,
              onToggleComplete = { viewModel.toggleDeadlineCompleted(nextDeadline.id) }
            )
          }
        }

        // Highlighted Latest Announcement
        val topAnnouncement = announcements.firstOrNull()
        if (topAnnouncement != null) {
          item {
            HomeSectionHeader(viewModel.getString("latest_announcement"), viewModel.t("Inbox", "Kikasha")) { viewModel.setTab(ScreenTab.HOME) }
            Spacer(modifier = Modifier.height(8.dp))
            AnnouncementCard(
              announcement = topAnnouncement,
              showReadCount = user.role != Role.STUDENT,
              onClick = {
                viewModel.markAnnouncementRead(topAnnouncement.id)
                onOpenAnnouncementDetail(topAnnouncement.id)
              }
            )
          }
        }

        // Today's Classes Section
        item {
          HomeSectionHeader(viewModel.getString("today_classes"), viewModel.t("Full timetable", "Ratiba kamili")) { viewModel.setTab(ScreenTab.TIMETABLE) }
        }

        // Today's sessions, by the phone's real day of the week (Monday = 1)
        val todaySessions = sessions
          .filter { it.dayOfWeek == java.time.LocalDate.now().dayOfWeek.value }
          .sortedBy { it.startTime }
        if (todaySessions.isEmpty()) {
          item {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DeepBlue.copy(alpha = 0.06f))
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Default.EventAvailable, contentDescription = null, tint = DeepBlue, modifier = Modifier.size(32.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text(viewModel.t("No classes today", "Hakuna vipindi leo"), fontWeight = FontWeight.Bold)
              Text(
                viewModel.t("Enjoy the free time, or check the full timetable.", "Furahia muda huru, au angalia ratiba kamili."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
        items(todaySessions, key = { it.id }) { session ->
          Box(modifier = Modifier.animateItem()) {
          SessionCard(
            session = session,
            currentUserRole = user.role,
            is24Hour = is24Hour
          )
        
          }
        }
      }

      Role.LECTURER -> {
        // Lecturer Quick Actions
        item {
          Text(
            text = viewModel.getString("quick_actions"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            LecturerQuickActionButton(
              title = "Post Notice",
              icon = Icons.Default.Campaign,
              color = DeepBlue,
              onClick = onOpenPublishDialog,
              modifier = Modifier.weight(1f)
            )
            LecturerQuickActionButton(
              title = "Change Venue",
              icon = Icons.Default.EditCalendar,
              color = WarmAmber,
              onClick = {
                val firstSession = sessions.firstOrNull { it.lecturerName.contains("Mushi") }
                if (firstSession != null) onOpenChangeVenueDialog(firstSession.id)
              },
              modifier = Modifier.weight(1f)
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            LecturerQuickActionButton(
              title = "Upload Notes",
              icon = Icons.Default.CloudUpload,
              color = SuccessGreen,
              onClick = onOpenUploadDialog,
              modifier = Modifier.weight(1f)
            )
            LecturerQuickActionButton(
              title = "Add Deadline",
              icon = Icons.Default.CalendarMonth,
              color = Color(0xFF7C3AED),
              onClick = onOpenAddDeadlineDialog,
              modifier = Modifier.weight(1f)
            )
          }
        }

        // Lecturer's Assigned Courses
        item {
          Text(
            text = viewModel.getString("my_assigned_courses"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(8.dp))
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "CS210 • Data Structures & Algorithms",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "BSc Computer Science • 54 Enrolled Students",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "CS215 • Object-Oriented Software Engineering",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "BSc Computer Science • 54 Enrolled Students",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Lecturer's Recent Posts & Read Receipts
        item {
          Text(
            text = "Recent Announcements & Read Receipts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        val lecturerPosts = announcements.filter { it.authorId == user.id }
        items(lecturerPosts) { announcement ->
          AnnouncementCard(
            announcement = announcement,
            showReadCount = true,
            onClick = { onOpenAnnouncementDetail(announcement.id) }
          )
        }
      }

      Role.ADMIN -> {
        // Admin Summary
        item {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = DeepBlue),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth().testTag("admin_summary_card")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Administrator Control Center",
                  style = MaterialTheme.typography.titleMedium,
                  color = Color.White,
                  fontWeight = FontWeight.Bold
                )
                Icon(
                  imageVector = Icons.Default.VerifiedUser,
                  contentDescription = "Admin",
                  tint = Color.White
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Academic Registry & Faculty Schedule Management",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
              )
              Spacer(modifier = Modifier.height(14.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                AdminStatItem("Pending Users", "${pendingUsers.size}", WarmAmberLight)
                AdminStatItem("Active Sessions", "${sessions.size}", Color(0xFFDCFCE7))
                AdminStatItem("Announcements", "${announcements.size}", Color(0xFFE0E7FF))
              }
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { viewModel.setTab(ScreenTab.ADMIN) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("go_to_admin_portal_btn")
              ) {
                Text(
                  text = "Open Approvals & Audit Log (${pendingUsers.size} pending)",
                  color = DeepBlue,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        item {
          Text(
            text = "Active Timetable Schedule Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        if (sessions.isEmpty()) {
          // Centred in the space that is left, so the screen does not look broken when it is empty
          item {
            Box(
              modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.45f),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                  modifier = Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(DeepBlue.copy(alpha = 0.08f)),
                  contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.EventAvailable, contentDescription = null, tint = DeepBlue, modifier = Modifier.size(40.dp)) }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  viewModel.t("No sessions registered yet", "Hakuna vipindi vilivyosajiliwa bado"),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  viewModel.t("Open the Timetable tab to schedule the first class.", "Fungua kichupo cha Ratiba kuweka kipindi cha kwanza."),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                ActionButton(
                  label = viewModel.t("Open timetable", "Fungua ratiba"),
                  icon = Icons.Default.CalendarToday,
                  onClick = { viewModel.setTab(ScreenTab.TIMETABLE) },
                  filled = true,
                  tag = "empty_open_timetable_btn"
                )
              }
            }
          }
        }
        items(sessions.take(4)) { session ->
          SessionCard(
            session = session,
            currentUserRole = user.role,
            is24Hour = is24Hour,
            onEditClick = { onOpenChangeVenueDialog(session.id) }
          )
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
fun LecturerQuickActionButton(
  title: String,
  icon: ImageVector,
  color: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier
      .clickable { onClick() }
      .testTag("action_${title.lowercase().replace(" ", "_")}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = color,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
fun AdminStatItem(label: String, value: String, bgColor: Color) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(Color.White.copy(alpha = 0.15f))
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White.copy(alpha = 0.85f)
    )
  }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White.copy(alpha = 0.16f))
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2)
  }
}

@Composable
private fun HomeSectionHeader(title: String, action: String, onAction: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(DeepBlue))
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    Surface(shape = RoundedCornerShape(50), color = DeepBlue.copy(alpha = 0.10f), onClick = onAction) {
      Text(
        text = action,
        style = MaterialTheme.typography.labelMedium,
        color = DeepBlue,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
      )
    }
  }
}
