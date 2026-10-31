package com.willykez.campconnect.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.campconnect.ui.theme.DeepBlue

/**
 * The app's standard button for card actions (Read, Save, Cancel, Relocate, Sync...). At least 52dp
 * tall so it is easy to hit with a thumb, with an icon and a clear label.
 *
 * [filled] gives a solid button; otherwise it is a soft, tinted button.
 */
@Composable
fun ActionButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  color: Color = DeepBlue,
  filled: Boolean = false,
  tag: String? = null
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = if (filled) color else color.copy(alpha = 0.12f),
    border = if (filled) null else BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    modifier = modifier.defaultMinSize(minHeight = 52.dp).let { if (tag != null) it.testTag(tag) else it }
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      if (icon != null) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
      }
      Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) Color.White else color,
        maxLines = 1
      )
    }
  }
}

/** A square 44dp icon button with a soft tinted background, for secondary actions like delete or edit. */
@Composable
fun SoftIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  color: Color = DeepBlue,
  tag: String? = null
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = color.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    modifier = modifier.size(52.dp).let { if (tag != null) it.testTag(tag) else it }
  ) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(24.dp))
    }
  }
}
