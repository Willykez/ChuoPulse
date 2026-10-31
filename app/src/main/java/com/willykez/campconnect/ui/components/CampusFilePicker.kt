package com.willykez.campconnect.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.docs.DocumentKind
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen

@Composable
fun CampusFilePicker(
  selectedFile: AttachmentFile?,
  onFileSelected: (AttachmentFile) -> Unit,
  onClearFile: () -> Unit,
  onPreviewFile: (AttachmentFile) -> Unit,
  title: String = "Select Document or Image",
  allowedTypesDescription: String = "PDF, photos, Word, PowerPoint, Excel or text",
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  // The system document picker, limited to the formats the app can open and analyse.
  val fileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      val processed = processSelectedUri(context, uri)
      onFileSelected(processed)
    }
  }

  Column(modifier = modifier.fillMaxWidth()) {
    if (selectedFile == null) {
      // Dropzone / File Picker Trigger
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier
          .fillMaxWidth()
          .border(
            width = 1.5.dp,
            color = DeepBlue.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp)
          )
          .testTag("file_picker_dropzone")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(DeepBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.FolderOpen,
              contentDescription = "Upload Document",
              tint = DeepBlue,
              modifier = Modifier.size(24.dp)
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )

          Text(
            text = allowedTypesDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Main Device File Picker button
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = DeepBlue,
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                  fileLauncher.launch(DocumentKind.pickerMimeTypes)
                }
                .testTag("btn_browse_device_files")
            ) {
              Row(
                modifier = Modifier.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.AttachFile,
                  contentDescription = "Attach",
                  tint = Color.White,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Choose From Device",
                  color = Color.White,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    } else {
      // Selected File Preview Card
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
          .fillMaxWidth()
          .border(
            width = 1.dp,
            color = SuccessGreen.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
          )
          .testTag("selected_file_card")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          FileKindBadge(kind = DocumentKind.of(selectedFile.fileName, selectedFile.mimeType), size = 42.dp)

          Spacer(modifier = Modifier.width(10.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = selectedFile.fileName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "${selectedFile.fileSize} • ${DocumentKind.of(selectedFile.fileName, selectedFile.mimeType).label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Ready to attach",
                style = MaterialTheme.typography.labelSmall,
                color = SuccessGreen,
                fontWeight = FontWeight.Bold
              )
            }
          }

          // Preview button
          IconButton(
            onClick = { onPreviewFile(selectedFile) },
            modifier = Modifier.testTag("preview_selected_file_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Visibility,
              contentDescription = "Preview",
              tint = DeepBlue
            )
          }

          // Clear button
          IconButton(
            onClick = onClearFile,
            modifier = Modifier.testTag("remove_selected_file_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Remove",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

private fun processSelectedUri(context: Context, uri: Uri): AttachmentFile {
  var displayName = "document_${System.currentTimeMillis()}"
  var fileSizeFormatted = "Size unknown"
  var mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

  context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
    if (cursor.moveToFirst()) {
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

      if (nameIndex != -1) {
        val name = cursor.getString(nameIndex)
        if (!name.isNullOrBlank()) displayName = name
      }
      if (sizeIndex != -1) {
        val sizeBytes = cursor.getLong(sizeIndex)
        fileSizeFormatted = if (sizeBytes > 1024 * 1024) {
          String.format("%.1f MB", sizeBytes / (1024f * 1024f))
        } else if (sizeBytes > 1024) {
          String.format("%d KB", sizeBytes / 1024)
        } else {
          "$sizeBytes B"
        }
      }
    }
  }

  val kind = DocumentKind.of(displayName, mimeType)
  if (mimeType == "application/octet-stream") {
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(DocumentKind.extensionOf(displayName))?.let { mimeType = it }
  }
  val isImage = kind == DocumentKind.IMAGE

  return AttachmentFile(
    uriString = uri.toString(),
    fileName = displayName,
    fileSize = fileSizeFormatted,
    mimeType = mimeType,
    isImage = isImage,
    previewText = null
  )
}
