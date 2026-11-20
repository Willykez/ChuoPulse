@file:OptIn(ExperimentalMaterial3Api::class)
package com.willykez.campconnect.ui.components

import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState? = null,
  title: String? = null,
  subtitle: String? = null,
  titleColor: Color = Color.Unspecified,
  tag: String? = null,
  scrollable: Boolean = true,
  footer: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val guard = rememberKeyboardGuard()
  val state = sheetState ?: rememberGuardedSheetState(skipPartiallyExpanded = true, guard = guard)
  val scope = rememberCoroutineScope()
  val safeDismiss: () -> Unit = {
    if (guard.blocking()) {
      scope.launch { runCatching { state.show() } }
    } else onDismissRequest()
  }
  ModalBottomSheet(
    onDismissRequest = safeDismiss,
    sheetState = state,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 12.dp)
          .size(width = 40.dp, height = 4.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.outlineVariant)
      )
    },
    modifier = modifier
  ) {
    KeyboardBackGuard(guard)
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 680.dp)
          .imePadding()
          .then(if (tag != null) Modifier.testTag(tag) else Modifier)
      ) {
        if (title != null) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor
              )
              if (subtitle != null) {
                Text(
                  text = subtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            IconButton(onClick = onDismissRequest) {
              Icon(Icons.Default.Close, contentDescription = "Close")
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
        }

        Column(
          modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 20.dp)
            .padding(bottom = if (footer == null) 28.dp else 12.dp),
          content = content
        )

        if (footer != null) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = footer
          )
        } else {
          Spacer(modifier = Modifier.navigationBarsPadding())
        }
      }
    }
  }
}

@Composable
fun RowScope.SheetButtons(
  dismissLabel: String,
  onDismiss: () -> Unit,
  confirmLabel: String,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  confirmColor: Color = DeepBlue,
  confirmIcon: ImageVector? = null,
  confirmTag: String? = null
) {
  OutlinedButton(
    onClick = onDismiss,
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.weight(1f).height(56.dp)
  ) { Text(dismissLabel, maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }

  Button(
    onClick = onConfirm,
    enabled = enabled,
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(containerColor = confirmColor, contentColor = Color.White),
    modifier = modifier
      .weight(1.5f)
      .height(56.dp)
      .then(if (confirmTag != null) Modifier.testTag(confirmTag) else Modifier)
  ) {
    if (confirmIcon != null) {
      Icon(confirmIcon, contentDescription = null, modifier = Modifier.size(22.dp))
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(confirmLabel, maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
fun ConfirmSheet(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  dismissLabel: String = "Cancel",
  destructive: Boolean = true,
  confirmIcon: ImageVector? = null,
  tag: String? = null,
  confirmTag: String? = null,
  confirmEnabled: Boolean = true,
  extra: (@Composable ColumnScope.() -> Unit)? = null
) {
  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = title,
    titleColor = if (destructive) DangerRed else Color.Unspecified,
    tag = tag,
    footer = {
      SheetButtons(
        dismissLabel = dismissLabel,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        confirmColor = if (destructive) DangerRed else DeepBlue,
        confirmIcon = confirmIcon,
        confirmTag = confirmTag,
        enabled = confirmEnabled
      )
    }
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (extra != null) {
      Spacer(modifier = Modifier.height(12.dp))
      extra()
    }
    Spacer(modifier = Modifier.height(8.dp))
  }
}

class KeyboardGuard internal constructor() {
  var open by mutableStateOf(false)
    internal set
  internal var closedAt: Long = 0L

  fun blocking(): Boolean = open || (System.currentTimeMillis() - closedAt) < GRACE_MS

  internal fun update(nowOpen: Boolean) {
    if (open && !nowOpen) closedAt = System.currentTimeMillis()
    open = nowOpen
  }

  private companion object { const val GRACE_MS = 600L }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberKeyboardGuard(): KeyboardGuard {
  val view = LocalView.current
  val guard = remember { KeyboardGuard() }
  val composeIme = WindowInsets.isImeVisible
  LaunchedEffect(view, composeIme) {
    while (true) {
      val visible = composeIme ||
        ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
      guard.update(visible)
      kotlinx.coroutines.delay(30)
    }
  }
  return guard
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberGuardedSheetState(
  skipPartiallyExpanded: Boolean = true,
  guard: KeyboardGuard = rememberKeyboardGuard()
): SheetState = rememberModalBottomSheetState(
  skipPartiallyExpanded = skipPartiallyExpanded,
  confirmValueChange = { target -> !(target == SheetValue.Hidden && guard.blocking()) }
)

@Composable
private fun KeyboardBackGuard(guard: KeyboardGuard) {
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  BackHandler(enabled = guard.open || guard.blocking()) {
    keyboard?.hide()
    focus.clearFocus()
  }
}
