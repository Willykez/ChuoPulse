package com.willykez.campconnect.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App-wide theme preference. Stored locally; System mode follows Android's appearance setting. */
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

object AppThemeSettings {
  var mode by mutableStateOf(AppThemeMode.SYSTEM)
    private set

  fun load(context: Context) {
    val saved = context.applicationContext.getSharedPreferences("campusconnect_ui", Context.MODE_PRIVATE)
      .getString("theme_mode", AppThemeMode.SYSTEM.name)
    mode = runCatching { AppThemeMode.valueOf(saved ?: AppThemeMode.SYSTEM.name) }.getOrDefault(AppThemeMode.SYSTEM)
  }

  fun set(context: Context, newMode: AppThemeMode) {
    mode = newMode
    context.applicationContext.getSharedPreferences("campusconnect_ui", Context.MODE_PRIVATE)
      .edit().putString("theme_mode", newMode.name).apply()
  }

  fun toggle(context: Context, systemDark: Boolean) {
    val next = if (mode == AppThemeMode.DARK || (mode == AppThemeMode.SYSTEM && systemDark)) AppThemeMode.LIGHT else AppThemeMode.DARK
    set(context, next)
  }
}
