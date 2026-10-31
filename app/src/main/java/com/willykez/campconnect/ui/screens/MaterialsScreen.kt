package com.willykez.campconnect.ui.screens

import com.willykez.campconnect.ui.components.SoftIconButton
import com.willykez.campconnect.ui.components.ActionButton
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.data.model.ContentApprovalStatus
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.StudyMaterial
import com.willykez.campconnect.ui.components.StatusBadge
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DangerRedLight
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.SuccessGreenLight
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.WarmAmberLight
import com.willykez.campconnect.viewmodel.CampusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(
  viewModel: CampusViewModel,
  onOpenUploadDialog: () -> Unit,
  onOpenProcessedDocuments: () -> Unit,
  onPreviewDocument: (AttachmentFile, authorName: String, courseCode: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val user by viewModel.currentUser.collectAsState()
  val materials by viewModel.materials.collectAsState()
  var query by remember { mutableStateOf("") }
  var collapsed by remember { mutableStateOf(setOf<String>()) }
  val shown = materials.filter {
    query.isBlank() || it.title.contains(query, true) || it.courseCode.contains(query, true) ||
      it.courseTitle.contains(query, true) || it.description.contains(query, true)
  }
  // One section per subject, so everything for a module is in one place.
  val grouped = shown.groupBy { it.courseCode }.toSortedMap()

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = viewModel.getString("tab_materials"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Offline course notes, slides & handouts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Card(
        onClick = onOpenProcessedDocuments,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text("AI Summaries (Room DB)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Browse saved analyses offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
          }
          Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Open summaries", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
      }
      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(viewModel.t("Search subject or file", "Tafuta somo au faili")) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("materials_search")
      )
      Spacer(modifier = Modifier.height(12.dp))

      if (grouped.isEmpty()) {
        Text(
          text = if (materials.isEmpty()) viewModel.t("No study materials yet.", "Hakuna vifaa vya masomo bado.")
          else viewModel.t("No file matches your search.", "Hakuna faili inayolingana na utafutaji."),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 24.dp)
        )
      }

      // Materials List
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        grouped.forEach { (code, list) ->
          item(key = "subject_$code") {
            val open = code !in collapsed
            SubjectHeader(
              code = code,
              title = list.first().courseTitle,
              count = list.size,
              expanded = open,
              modifier = Modifier.animateItem()
            ) { collapsed = if (open) collapsed + code else collapsed - code }
          }
          if (code !in collapsed) {
            items(list, key = { it.id }) { mat ->
          Box(modifier = Modifier.animateItem()) {
          val canModerate = viewModel.canEditOrDelete(mat.uploaderId)

          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
              .fillMaxWidth()
              .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
              )
              .testTag("material_item_${mat.id}")
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (mat.isDownloaded) SuccessGreenLight else DeepBlue.copy(alpha = 0.12f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = if (mat.isDownloaded) Icons.Default.FileDownloadDone else Icons.Default.PictureAsPdf,
                    contentDescription = "File",
                    tint = if (mat.isDownloaded) SuccessGreen else DeepBlue,
                    modifier = Modifier.size(24.dp)
                  )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = mat.courseCode,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = DeepBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "• ${mat.attachment.fileSize}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mat.isDownloaded) {
                      Spacer(modifier = Modifier.width(6.dp))
                      StatusBadge(
                        text = "Cached",
                        backgroundColor = SuccessGreenLight,
                        contentColor = SuccessGreen
                      )
                    }
                    if (mat.approvalStatus == ContentApprovalStatus.PENDING_REVIEW) {
                      Spacer(modifier = Modifier.width(6.dp))
                      StatusBadge(
                        text = "Pending",
                        backgroundColor = WarmAmberLight,
                        contentColor = Color(0xFFB45309)
                      )
                    } else if (mat.approvalStatus == ContentApprovalStatus.DISAPPROVED) {
                      Spacer(modifier = Modifier.width(6.dp))
                      StatusBadge(
                        text = "Disapproved",
                        backgroundColor = DangerRedLight,
                        contentColor = DangerRed
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(4.dp))

                  Text(
                    text = mat.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )

                  Spacer(modifier = Modifier.height(2.dp))

                  Text(
                    text = "By ${mat.uploadedBy} • ${mat.uploadedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Actions: Read in the built-in viewer, save for offline, and moderation
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                ActionButton(
                  label = viewModel.t("Read", "Soma"),
                  icon = Icons.Default.Visibility,
                  onClick = { onPreviewDocument(mat.attachment, mat.uploadedBy, mat.courseCode) },
                  filled = true,
                  modifier = Modifier.weight(1f),
                  tag = "view_doc_btn_${mat.id}"
                )
                ActionButton(
                  label = if (mat.isDownloaded) viewModel.t("Saved", "Imehifadhiwa") else viewModel.t("Save", "Hifadhi"),
                  icon = if (mat.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                  onClick = { viewModel.toggleMaterialDownloaded(mat.id) },
                  color = if (mat.isDownloaded) SuccessGreen else DeepBlue,
                  modifier = Modifier.weight(1f),
                  tag = "download_toggle_${mat.id}"
                )
              }

              if (canModerate) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  if (user.role == Role.ADMIN) {
                    if (mat.approvalStatus != ContentApprovalStatus.APPROVED) {
                      SoftIconButton(Icons.Default.ThumbUp, "Approve", { viewModel.setMaterialApproval(mat.id, ContentApprovalStatus.APPROVED) }, color = SuccessGreen, tag = "approve_mat_${mat.id}")
                    }
                    if (mat.approvalStatus != ContentApprovalStatus.DISAPPROVED) {
                      SoftIconButton(Icons.Default.ThumbDown, "Disapprove", { viewModel.setMaterialApproval(mat.id, ContentApprovalStatus.DISAPPROVED) }, color = WarmAmber, tag = "disapprove_mat_${mat.id}")
                    }
                  }
                  SoftIconButton(Icons.Default.Delete, "Delete", { viewModel.deleteMaterial(mat.id) }, color = DangerRed, tag = "delete_mat_${mat.id}")
                }
              }
            }
          }
        
          }
        }
          }
        }

        item {
          Spacer(modifier = Modifier.height(80.dp))
        }
      }
    }

    if (user.role == Role.LECTURER || user.role == Role.ADMIN || (user.role == Role.STUDENT && user.isClassRep)) {
      FloatingActionButton(
        onClick = onOpenUploadDialog,
        containerColor = DeepBlue,
        contentColor = Color.White,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(20.dp)
          .size(66.dp)
          .testTag("fab_upload_material")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Upload Study Material"
        )
      }
    }
  }
}


@Composable
private fun SubjectHeader(
  code: String,
  title: String,
  count: Int,
  expanded: Boolean,
  modifier: Modifier = Modifier,
  onToggle: () -> Unit
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(DeepBlue.copy(alpha = 0.07f))
      .border(1.dp, DeepBlue.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
      .clickable { onToggle() }
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .testTag("subject_header_$code"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(DeepBlue),
      contentAlignment = Alignment.Center
    ) {
      Text(code.take(4), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
    }
    Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
      Text(code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = DeepBlue)
      Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
    Surface(shape = RoundedCornerShape(50), color = DeepBlue.copy(alpha = 0.14f)) {
      Text(
        "$count",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = DeepBlue,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
      )
    }
    Icon(
      imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
