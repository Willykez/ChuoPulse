@file:OptIn(ExperimentalMaterial3Api::class)
package com.willykez.campconnect.ui.screens

import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Badge
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import com.willykez.campconnect.ui.theme.DeepBlueDark
import com.willykez.campconnect.ui.theme.PrimaryLightBlue
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.AccountStatus
import com.willykez.campconnect.data.model.AppLanguage
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.ui.components.StatusBadge
import com.willykez.campconnect.ui.theme.AppThemeMode
import com.willykez.campconnect.ui.theme.AppThemeSettings
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.WarmAmberLight
import com.willykez.campconnect.viewmodel.CampusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
  viewModel: CampusViewModel,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val appLanguage by viewModel.appLanguage.collectAsState()
  val isOffline by viewModel.isOffline.collectAsState()
  val is24Hour by viewModel.is24HourFormat.collectAsState()
  val devices by viewModel.myDevices.collectAsState()
  val deletionRequests by viewModel.deletionRequests.collectAsState()
  val context = LocalContext.current

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      val universities by viewModel.universities.collectAsState()
      val campusName = universities.firstOrNull { it.id == user.universityId }?.name.orEmpty()
      // Hero: big avatar, name, and who/where this person is
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(Brush.linearGradient(listOf(DeepBlueDark, DeepBlue, PrimaryLightBlue)))
          .drawBehind {
            drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.6f, center = Offset(size.width * 0.92f, size.height * 0.0f))
            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.4f, center = Offset(size.width * 0.05f, size.height * 0.95f))
          }
          .padding(20.dp)
          .testTag("profile_user_card")
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(4.dp),
            contentAlignment = Alignment.Center
          ) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
              Text(user.avatarInitials, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DeepBlue)
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text(user.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
          Text(user.email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
          if (campusName.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(campusName, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
          }
          Spacer(modifier = Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
              text = when (user.role) {
                Role.STUDENT -> viewModel.t("Student", "Mwanafunzi")
                Role.LECTURER -> viewModel.t("Lecturer", "Mhadhiri")
                Role.ADMIN -> viewModel.t("Administrator", "Msimamizi")
              },
              backgroundColor = Color.White,
              contentColor = DeepBlue
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadge(
              text = if (user.status == AccountStatus.APPROVED) viewModel.t("Verified", "Amethibitishwa") else viewModel.t("Pending review", "Anasubiri"),
              backgroundColor = if (user.status == AccountStatus.APPROVED) Color(0xFFDCFCE7) else WarmAmberLight,
              contentColor = if (user.status == AccountStatus.APPROVED) SuccessGreen else Color(0xFFB45309)
            )
          }
        }
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
          .testTag("profile_details_card")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          ProfileSectionTitle(viewModel.t("Details", "Taarifa"), Icons.Default.Badge)
          Spacer(modifier = Modifier.height(6.dp))
          ProfileInfoRow(viewModel.t("Registration / staff number", "Namba ya usajili / mfanyakazi"), user.studentNumber ?: user.staffNumber ?: viewModel.t("Not assigned", "Haijatolewa"), Icons.Default.Badge)
          ProfileInfoRow(viewModel.t("Programme / department", "Programu / idara"), user.programme ?: viewModel.t("General", "Jumla"), Icons.Default.School)
          user.cohort?.let { ProfileInfoRow(viewModel.t("Year / designation", "Mwaka / cheo"), it, Icons.Default.CalendarToday) }
        }
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
          ProfileSectionTitle(viewModel.t("Appearance", "Mwonekano"), Icons.Default.Palette)
          Text(viewModel.t("Choose how ChuoPulse looks", "Chagua jinsi ChuoPulse inavyoonekana"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(12.dp))
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AppThemeMode.values().forEachIndexed { index, mode ->
              SegmentedButton(
                selected = AppThemeSettings.mode == mode,
                onClick = { AppThemeSettings.set(context, mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = AppThemeMode.values().size),
                icon = {
                  Icon(
                    when (mode) { AppThemeMode.SYSTEM -> Icons.Default.PhoneAndroid; AppThemeMode.LIGHT -> Icons.Default.LightMode; AppThemeMode.DARK -> Icons.Default.DarkMode },
                    contentDescription = null, modifier = Modifier.size(16.dp)
                  )
                }
              ) {
                Text(
                  when (mode) {
                    AppThemeMode.SYSTEM -> viewModel.t("System", "Mfumo")
                    AppThemeMode.LIGHT -> viewModel.t("Light", "Mwanga")
                    AppThemeMode.DARK -> viewModel.t("Dark", "Giza")
                  },
                  style = MaterialTheme.typography.labelSmall
                )
              }
            }
          }
        }
      }
    }

    item { ClassRepsCard(viewModel, user) }
    item { ChangePasswordCard(viewModel) }
    item {
      ProfileSectionTitle(viewModel.t("Preferences & Offline Sync", "Mapendeleo na Nje ya Mtandao"), Icons.Default.Tune)
      Spacer(modifier = Modifier.height(8.dp))

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.Language, contentDescription = "Language", tint = DeepBlue)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Language / Lugha",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = if (appLanguage == AppLanguage.ENGLISH) "English (Active)" else "Kiswahili (Inayotumika)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = DeepBlue.copy(alpha = 0.12f),
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { viewModel.toggleLanguage() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("toggle_language_btn")
            ) {
              Text(
                text = if (appLanguage == AppLanguage.ENGLISH) "Switch to Kiswahili" else "Badili kwa Kiingereza",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = DeepBlue
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.AccessTime, contentDescription = "Time format", tint = DeepBlue)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Timezone & Format",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "East Africa Time (EAT, UTC+3) • 24-Hour",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = is24Hour,
              onCheckedChange = { viewModel.toggle24HourFormat() },
              colors = SwitchDefaults.colors(checkedThumbColor = DeepBlue),
              modifier = Modifier.testTag("toggle_24h_switch")
            )
          }

          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.CloudOff, contentDescription = "Offline", tint = WarmAmber)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Simulate Offline Mode",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = if (isOffline) "Offline active (Loading from Room cache)" else "Online (Connected)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Switch(
              checked = isOffline,
              onCheckedChange = { viewModel.toggleOfflineSimulation() },
              colors = SwitchDefaults.colors(checkedThumbColor = WarmAmber),
              modifier = Modifier.testTag("toggle_offline_switch")
            )
          }
        }
      }
    }

    if (devices.isNotEmpty()) {
      item {
        ProfileSectionTitle(viewModel.t("Phones signed in", "Simu zilizoingia"), Icons.Default.Devices)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            devices.forEach { device ->
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = device.label + if (device.id == viewModel.thisDeviceId) " • " + viewModel.t("this phone", "simu hii") else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(device.lastActive, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (device.id != viewModel.thisDeviceId) {
                  OutlinedButton(
                    onClick = { viewModel.signOutDevice(device.id) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    modifier = Modifier.testTag("sign_out_device_${device.id}")
                  ) { Text(viewModel.t("Sign out", "Toka")) }
                }
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = { viewModel.signOutEverywhere() },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
              modifier = Modifier.fillMaxWidth().testTag("sign_out_everywhere_btn")
            ) { Text(viewModel.t("Sign out everywhere", "Toka kila mahali")) }
          }
        }
      }
    }

    item {
      ProfileSectionTitle(viewModel.t("My data", "Data zangu"), Icons.Default.Shield)
      Spacer(modifier = Modifier.height(8.dp))
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = viewModel.t(
              "You can see and take a copy of everything stored about you, or ask for it to be deleted.",
              "Unaweza kuona na kuchukua nakala ya kila kitu kilichohifadhiwa kukuhusu, au kuomba kifutwe."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, viewModel.exportMyData())
              }
              context.startActivity(Intent.createChooser(send, null))
            },
            modifier = Modifier.fillMaxWidth().testTag("export_my_data_btn")
          ) { Text(viewModel.t("Share a copy of my data", "Shiriki nakala ya data zangu")) }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = { viewModel.requestDataDeletion() },
            enabled = user.id !in deletionRequests,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
            modifier = Modifier.fillMaxWidth().testTag("delete_my_data_btn")
          ) {
            Text(
              if (user.id in deletionRequests) viewModel.t("Deletion requested", "Ombi la kufuta limetumwa")
              else viewModel.t("Ask for my data to be deleted", "Omba data zangu zifutwe")
            )
          }
        }
      }
    }

    item {
      Button(
        onClick = { viewModel.signOut() },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_btn")
      ) {
        Text(viewModel.t("Sign out", "Toka"))
      }
      Text(
        text = viewModel.t(
          "Signing out removes offline copies kept on this phone.",
          "Kutoka kunaondoa nakala za nje ya mtandao zilizo kwenye simu hii."
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
fun ProfileSectionTitle(text: String, icon: ImageVector) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(DeepBlue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = DeepBlue, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.width(10.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun ProfileInfoRow(label: String, value: String, icon: ImageVector? = null) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(DeepBlue.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = DeepBlue, modifier = Modifier.size(20.dp))
      }
      Spacer(Modifier.width(12.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
  }
}
