package com.willykez.campconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.WarmAmberLight
import com.willykez.campconnect.viewmodel.CampusViewModel

/**
 * Shown instead of the whole app while a person's campus is in offline (maintenance) mode.
 * Nothing behind it can be used. The campus comes back the moment the app manager switches it on again.
 */
@Composable
fun MaintenanceScreen(viewModel: CampusViewModel) {
  val campus by viewModel.myCampus.collectAsState()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .systemBarsPadding()
      .padding(28.dp)
      .testTag("maintenance_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Box(
        modifier = Modifier.size(96.dp).clip(CircleShape).background(WarmAmberLight),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Default.Construction, contentDescription = null, tint = WarmAmber, modifier = Modifier.size(46.dp))
      }
      Spacer(modifier = Modifier.height(22.dp))
      Text(
        viewModel.t("Under maintenance", "Matengenezo yanaendelea"),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        campus?.name ?: "",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        campus?.maintenanceNote?.takeIf { it.isNotBlank() } ?: viewModel.t(
          "Your campus is paused while we fix something. Everything you saved is safe. Please check back soon.",
          "Chuo chako kimesimamishwa kwa muda tunaporekebisha kitu. Kila ulichohifadhi kiko salama. Tafadhali rudi baadaye."
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
      campus?.contactEmail?.takeIf { it.isNotBlank() }?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          viewModel.t("Questions? $it", "Maswali? $it"),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
      Spacer(modifier = Modifier.height(28.dp))
      OutlinedButton(
        onClick = { viewModel.signOut() },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("maintenance_sign_out")
      ) { Text(viewModel.t("Sign out", "Toka")) }
    }
  }
}
