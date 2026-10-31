package com.willykez.campconnect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.docs.DocumentKind

fun DocumentKind.icon(): ImageVector = when (this) {
  DocumentKind.PDF -> Icons.Default.PictureAsPdf
  DocumentKind.IMAGE -> Icons.Default.Image
  DocumentKind.WORD -> Icons.Default.Description
  DocumentKind.SLIDES -> Icons.Default.Slideshow
  DocumentKind.SHEET -> Icons.Default.TableChart
  DocumentKind.TEXT -> Icons.Default.Article
  DocumentKind.OTHER -> Icons.Default.InsertDriveFile
}

/** One colour per file type, so a list of mixed files can be scanned at a glance. */
fun DocumentKind.accent(): Color = when (this) {
  DocumentKind.PDF -> Color(0xFFDC2626)
  DocumentKind.IMAGE -> Color(0xFF2563EB)
  DocumentKind.WORD -> Color(0xFF1D4ED8)
  DocumentKind.SLIDES -> Color(0xFFEA580C)
  DocumentKind.SHEET -> Color(0xFF16A34A)
  DocumentKind.TEXT -> Color(0xFF64748B)
  DocumentKind.OTHER -> Color(0xFF64748B)
}

/** A rounded tile with the file type's icon on a soft background of its colour. */
@Composable
fun FileKindBadge(kind: DocumentKind, modifier: Modifier = Modifier, size: Dp = 44.dp) {
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(size / 3.5f))
      .background(kind.accent().copy(alpha = 0.14f)),
    contentAlignment = Alignment.Center
  ) {
    Icon(kind.icon(), contentDescription = kind.label, tint = kind.accent(), modifier = Modifier.size(size * 0.55f))
  }
}
