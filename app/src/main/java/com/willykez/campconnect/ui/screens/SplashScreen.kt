package com.willykez.campconnect.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.DeepBlueDark
import com.willykez.campconnect.ui.theme.PrimaryLightBlue
import com.willykez.campconnect.util.AppBrand
import kotlinx.coroutines.delay

/**
 * The first thing people see: the logo eases in, the name and tagline follow, then the app opens.
 * It is purely visual (about 1.7 seconds) and never blocks anything that is loading behind it.
 */
@Composable
fun SplashScreen(swahili: Boolean, onFinished: () -> Unit) {
  val logoScale = remember { Animatable(0.6f) }
  val logoAlpha = remember { Animatable(0f) }
  val textAlpha = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    logoAlpha.animateTo(1f, tween(420))
    logoScale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    textAlpha.animateTo(1f, tween(420))
    delay(750)
    onFinished()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(DeepBlueDark, DeepBlue, PrimaryLightBlue)))
      .testTag("splash_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Box(
        modifier = Modifier
          .size(104.dp)
          .scale(logoScale.value)
          .alpha(logoAlpha.value)
          .clip(RoundedCornerShape(30.dp))
          .background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.School, contentDescription = null, tint = DeepBlue, modifier = Modifier.size(44.dp))
        }
      }
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        AppBrand.NAME,
        color = Color.White,
        fontSize = 34.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.alpha(textAlpha.value)
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        if (swahili) AppBrand.TAGLINE_SW else AppBrand.TAGLINE_EN,
        color = Color.White.copy(alpha = 0.82f),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.alpha(textAlpha.value)
      )
      Spacer(modifier = Modifier.height(36.dp))
      LinearProgressIndicator(
        modifier = Modifier.size(width = 96.dp, height = 3.dp).alpha(textAlpha.value),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.25f)
      )
    }
  }
}
