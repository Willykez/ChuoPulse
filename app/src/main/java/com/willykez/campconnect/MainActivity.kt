package com.willykez.campconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.willykez.campconnect.ui.CampusConnectApp
import com.willykez.campconnect.ui.theme.AppThemeSettings

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    com.willykez.campconnect.data.supabase.Backend.init(this)
    enableEdgeToEdge()
    AppThemeSettings.load(this)
    setContent {
      CampusConnectApp()
    }
  }
}
