package com.willykez.campconnect

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.willykez.campconnect.ui.CampusConnectApp
import com.willykez.campconnect.ui.theme.AppThemeSettings

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Must run before anything reads the saved sign-in or talks to the server.
    com.willykez.campconnect.data.supabase.Backend.init(this)
    // Fully transparent status and navigation bars on every screen. Icon colours follow the app's
    // own light/dark choice (set in CampusConnectTheme), not only the phone's.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    )
    AppThemeSettings.load(this)
    setContent {
      CampusConnectApp()
    }
  }
}
