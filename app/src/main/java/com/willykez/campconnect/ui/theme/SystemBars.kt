package com.willykez.campconnect.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Dialog windows (full-screen viewers, quizzes) get their own system bars. Call this inside the
 * dialog so its status and navigation bars are transparent like the rest of the app and the icons
 * stay readable on the dialog's background.
 */
@Composable
fun TransparentDialogBars(lightIcons: Boolean) {
  val view = LocalView.current
  SideEffect {
    val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
    WindowCompat.setDecorFitsSystemWindows(window, false)
    @Suppress("DEPRECATION")
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    @Suppress("DEPRECATION")
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 29) {
      window.isStatusBarContrastEnforced = false
      window.isNavigationBarContrastEnforced = false
    }
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = lightIcons
    controller.isAppearanceLightNavigationBars = lightIcons
  }
}

/**
 * Sets the status bar icon colour of the main window while a screen with a dark header is shown
 * (for example the app manager's blue header), then puts the app's own choice back.
 */
@Composable
fun ActivityStatusBarIcons(darkIcons: Boolean) {
  val view = LocalView.current
  androidx.compose.runtime.DisposableEffect(darkIcons) {
    val window = (view.context as? android.app.Activity)?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    val previous = controller?.isAppearanceLightStatusBars
    controller?.isAppearanceLightStatusBars = darkIcons
    onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
  }
}
