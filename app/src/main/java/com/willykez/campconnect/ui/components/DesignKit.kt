package com.willykez.campconnect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.ui.theme.DeepBlue

/**
 * The same page heading on every screen: a tinted icon tile, the title, a one-line hint, and room for
 * an action on the right. Keeps the whole app looking like one product.
 */
@Composable
fun ScreenHeading(
  title: String,
  subtitle: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null
) {
  Row(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(DeepBlue.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = DeepBlue) }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
    if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
  }
}

/** A friendly empty list: big icon tile, what is missing, and what to do about it. */
@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String = "", modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(DeepBlue.copy(alpha = 0.08f)),
      contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = DeepBlue, modifier = Modifier.size(34.dp)) }
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
  }
}
