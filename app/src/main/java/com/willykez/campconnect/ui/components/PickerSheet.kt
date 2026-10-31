@file:OptIn(ExperimentalMaterial3Api::class)
package com.willykez.campconnect.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.ui.theme.DeepBlue
import kotlinx.coroutines.launch

/** One choice in a [PickerSheet]. A disabled choice stays visible with a [note] saying why (for example "In use: CS215"). */
data class PickerItem(
  val id: String,
  val title: String,
  val subtitle: String? = null,
  val enabled: Boolean = true,
  val note: String? = null
)

/**
 * The one way the app asks "choose one of these": a bottom sheet with a short list, a search box for
 * long lists, and a smooth close once something is picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
  title: String,
  options: List<PickerItem>,
  selectedId: String?,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
  emptyText: String = "Nothing to choose yet.",
  searchHint: String = "Search"
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = options.size > 6)
  val scope = rememberCoroutineScope()
  var query by remember { mutableStateOf("") }
  val shown = remember(options, query) {
    if (query.isBlank()) options
    else options.filter { it.title.contains(query, ignoreCase = true) || it.subtitle?.contains(query, ignoreCase = true) == true }
  }

  fun choose(id: String) {
    scope.launch { sheetState.hide() }.invokeOnCompletion {
      onSelect(id)
      onDismiss()
    }
  }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    title = title,
    scrollable = false
  ) {
    if (options.size > 8) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(searchHint) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("picker_search")
      )
      Spacer(modifier = Modifier.height(8.dp))
    }

    if (shown.isEmpty()) {
      Text(
        text = emptyText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp)
      )
    } else {
      LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
        items(shown, key = { it.id }) { item ->
          PickerRow(item = item, selected = item.id == selectedId) { choose(item.id) }
        }
      }
    }
  }
}

@Composable
private fun PickerRow(item: PickerItem, selected: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(enabled = item.enabled, onClick = onClick)
      .alpha(if (item.enabled) 1f else 0.5f)
      .padding(horizontal = 8.dp, vertical = 12.dp)
      .testTag("picker_item_${item.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) DeepBlue else MaterialTheme.colorScheme.onSurface
      )
      val second = item.note ?: item.subtitle
      if (second != null) {
        Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (selected) {
      Spacer(modifier = Modifier.width(8.dp))
      Icon(Icons.Default.Check, contentDescription = "Selected", tint = DeepBlue)
    }
  }
}

/** A field that looks like a text box but opens a [PickerSheet] when tapped. */
@Composable
fun PickerField(
  label: String,
  value: String?,
  placeholder: String,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leadingIcon: ImageVector? = null
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    modifier = modifier
      .fillMaxWidth()
      .alpha(if (enabled) 1f else 0.5f)
      .clip(RoundedCornerShape(14.dp))
      .clickable(enabled = enabled, onClick = onClick)
      .testTag(tag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          text = value ?: placeholder,
          style = MaterialTheme.typography.bodyLarge,
          color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
      }
      Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
