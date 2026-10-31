package com.willykez.campconnect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryDark,
  onPrimary = Color(0xFF001E6C),
  primaryContainer = DeepBlueDark,
  onPrimaryContainer = Color(0xFFDAE2FF),
  secondary = WarmAmber,
  onSecondary = Color(0xFF452B00),
  secondaryContainer = Color(0xFF633F00),
  onSecondaryContainer = WarmAmberLight,
  tertiary = SuccessGreen,
  background = BackgroundDark,
  onBackground = TextPrimaryDark,
  surface = SurfaceDark,
  surfaceContainerLowest = Color(0xFF090C12),
  surfaceContainerLow = Color(0xFF131722),
  surfaceContainer = Color(0xFF1E2536),
  surfaceContainerHigh = Color(0xFF252D40),
  surfaceContainerHighest = Color(0xFF30394D),
  onSurface = TextPrimaryDark,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = TextSecondaryDark,
  outline = OutlineDark,
  outlineVariant = Color(0xFF39445A),
  error = DangerRed
)

private val LightColorScheme = lightColorScheme(
  primary = DeepBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDDE3FF),
  onPrimaryContainer = Color(0xFF001453),
  secondary = WarmAmber,
  onSecondary = Color.White,
  secondaryContainer = WarmAmberLight,
  onSecondaryContainer = WarmAmberDark,
  tertiary = SuccessGreen,
  background = BackgroundLight,
  onBackground = TextPrimaryLight,
  surface = SurfaceLight,
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFF8FAFC),
  surfaceContainer = Color(0xFFF1F5F9),
  surfaceContainerHigh = Color(0xFFE8EEF5),
  surfaceContainerHighest = Color(0xFFDDE5EF),
  onSurface = TextPrimaryLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = TextSecondaryLight,
  outline = OutlineLight,
  outlineVariant = Color(0xFFD7E0EB),
  error = DangerRed
)

// One set of corner sizes for the whole app, so cards, fields, dialogs and buttons feel related.
private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CampusConnectTheme(
  darkTheme: Boolean? = null,
  content: @Composable () -> Unit
) {
  val mode = AppThemeSettings.mode
  val useDarkTheme = darkTheme ?: when (mode) {
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
  }
  val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content
  )
}
