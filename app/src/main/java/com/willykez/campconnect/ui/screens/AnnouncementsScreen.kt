package com.willykez.campconnect.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.AudienceType
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.ui.components.AnnouncementCard
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.viewmodel.CampusViewModel

@Composable
fun AnnouncementsScreen(
  viewModel: CampusViewModel,
  onOpenDetail: (String) -> Unit,
  onOpenPublishDialog: () -> Unit,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val announcements by viewModel.announcements.collectAsState()
  val activeFilter by viewModel.announcementFilter.collectAsState()

  val filters = listOf(
    "ALL" to "All Notices",
    "CAMPUS" to "Campus-wide",
    "PROGRAMME" to "My Programme",
    "COURSE" to "Courses"
  )

  val filteredList = when (activeFilter) {
    "CAMPUS" -> announcements.filter { it.audienceType == AudienceType.CAMPUS }
    "PROGRAMME" -> announcements.filter { it.audienceType == AudienceType.PROGRAMME }
    "COURSE" -> announcements.filter { it.audienceType == AudienceType.COURSE }
    else -> announcements
  }

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      // Screen Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = viewModel.getString("tab_announcements"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = viewModel.t("Official verified academic notices", "Matangazo rasmi ya kitaaluma yaliyothibitishwa"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (user.role == Role.LECTURER || user.role == Role.ADMIN || (user.role == Role.STUDENT && user.isClassRep)) {
          Surface(
            shape = RoundedCornerShape(50),
            color = DeepBlue,
            shadowElevation = 4.dp,
            modifier = Modifier
              .clip(RoundedCornerShape(50))
              .testTag("publish_notice_header_btn")
          ) {
            Row(
              modifier = Modifier
                .clickable { onOpenPublishDialog() }
                .padding(horizontal = 16.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = viewModel.t("Post", "Tuma"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Filter chips row
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(filters) { (key, label) ->
          val isSelected = activeFilter == key
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
              width = 1.dp,
              color = if (isSelected) DeepBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .clickable { viewModel.setAnnouncementFilter(key) }
              .testTag("filter_$key")
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Announcements List
      if (filteredList.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Campaign,
              contentDescription = "No notices",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No announcements in this category",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(filteredList, key = { it.id }) { item ->
          Box(modifier = Modifier.animateItem()) {
            AnnouncementCard(
              announcement = item,
              showReadCount = user.role != Role.STUDENT,
              onClick = {
                viewModel.markAnnouncementRead(item.id)
                onOpenDetail(item.id)
              }
            )
          
          }
        }

          item {
            Spacer(modifier = Modifier.height(80.dp))
          }
        }
      }
    }

    // Floating action button for Lecturer
    if (user.role == Role.LECTURER || user.role == Role.ADMIN || (user.role == Role.STUDENT && user.isClassRep)) {
      FloatingActionButton(
        onClick = onOpenPublishDialog,
        containerColor = DeepBlue,
        contentColor = Color.White,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(20.dp)
          .size(66.dp)
          .testTag("fab_publish_announcement")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Publish Announcement"
        )
      }
    }
  }
}
