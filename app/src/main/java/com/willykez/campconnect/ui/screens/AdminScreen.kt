package com.willykez.campconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.willykez.campconnect.data.model.Role
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.model.UserProfile
import com.willykez.campconnect.ui.components.StatusBadge
import com.willykez.campconnect.ui.components.ConfirmSheet
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DangerRedLight
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.SuccessGreenLight
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.ui.theme.WarmAmberLight
import com.willykez.campconnect.viewmodel.CampusViewModel

@Composable
fun AdminScreen(
  viewModel: CampusViewModel,
  modifier: Modifier = Modifier
) {
  val pendingUsers by viewModel.pendingUsers.collectAsState()
  androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refreshAdminData() }
  val auditLogs by viewModel.auditLogs.collectAsState()
  var selectedTab by remember { mutableStateOf(0) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Academic Registry & Admin",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = "Role approvals, academic audits & access control",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(14.dp))

    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = DeepBlue
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = {
          Text(
            text = "Approvals (${pendingUsers.size})",
            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
          )
        }
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = {
          Text(
            text = "Audit Log",
            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
          )
        }
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        modifier = Modifier.testTag("admin_tab_campus"),
        text = {
          Text(
            text = viewModel.t("Campus", "Chuo"),
            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
          )
        }
      )
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (selectedTab == 0) {
      // Pending Approvals
      if (pendingUsers.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "All done",
              tint = SuccessGreen,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "No pending approvals",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "All student and lecturer account requests are resolved",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(pendingUsers, key = { it.id }) { pUser ->
          Box(modifier = Modifier.animateItem()) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              modifier = Modifier
                .fillMaxWidth()
                .border(
                  width = 1.dp,
                  color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                  shape = RoundedCornerShape(14.dp)
                )
                .testTag("pending_user_card_${pUser.id}")
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DeepBlue.copy(alpha = 0.15f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = pUser.avatarInitials,
                        fontWeight = FontWeight.Bold,
                        color = DeepBlue
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = pUser.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = pUser.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  StatusBadge(
                    text = "Asked to be: " + pUser.requestedRole.name,
                    backgroundColor = if (pUser.requestedRole == Role.STUDENT) Color(0xFFE0E7FF) else Color(0xFFFEF3C7),
                    contentColor = if (pUser.requestedRole == Role.STUDENT) DeepBlue else Color(0xFFB45309)
                  )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column {
                    Text(
                      text = "ID Number:",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                      text = pUser.studentNumber ?: pUser.staffNumber ?: "N/A",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold
                    )
                  }
                  Column {
                    Text(
                      text = "Programme:",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                      text = pUser.programme ?: "General",
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }

                Spacer(modifier = Modifier.height(12.dp))

                PendingActions(viewModel, pUser)
              }
            }
          
          }
        }

          item {
            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    } else if (selectedTab == 2) {
      val myCampus by viewModel.myCampus.collectAsState()
      var adminSheet by remember { mutableStateOf(0) }
      Column(modifier = Modifier.weight(1f)) {
        myCampus?.let { c ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { adminSheet = 1 }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("admin_edit_campus")) {
              Text(viewModel.t("Campus details", "Taarifa za chuo"))
            }
            OutlinedButton(onClick = { adminSheet = 2 }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("admin_add_person")) {
              Text(viewModel.t("Add person", "Ongeza mtu"))
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          when (adminSheet) {
            1 -> EditCampusDetailsSheet(viewModel, c, canRename = false) { adminSheet = 0 }
            2 -> AddCampusUserSheet(viewModel, c.id, allowAdmin = false) { adminSheet = 0 }
          }
        }
        CampusSetupTab(viewModel = viewModel, modifier = Modifier.weight(1f))
      }
    } else {
      // Audit Logs
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(auditLogs) { log ->
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            modifier = Modifier.fillMaxWidth().testTag("audit_log_${log.id}")
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                StatusBadge(
                  text = log.action,
                  backgroundColor = DeepBlue.copy(alpha = 0.12f),
                  contentColor = DeepBlue
                )
                Text(
                  text = log.timestamp,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = log.details,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Actor: ${log.actor}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }
  }
}


/**
 * Registry decision for one waiting account. Nobody can make themselves a lecturer:
 * the administrator chooses the role, and risky choices ask for a second confirmation.
 */
@Composable
private fun PendingActions(viewModel: CampusViewModel, pUser: UserProfile) {
  val wantsStaff = pUser.requestedRole == Role.LECTURER
  var teaches by remember(pUser.id) { mutableStateOf(setOf<String>()) }
  var confirm by remember(pUser.id) { mutableStateOf<String?>(null) }
  val allCourses = remember(pUser.universityId) { viewModel.coursesFor(pUser.universityId).map { it.first } }

  Column {
    Spacer(modifier = Modifier.height(10.dp))
    if (wantsStaff) {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "Courses they will teach:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
      ) {
        allCourses.forEach { code ->
          val on = code in teaches
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (on) DeepBlue else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .clickable { teaches = if (on) teaches - code else teaches + code }
              .testTag("teach_chip_${pUser.id}_$code")
          ) {
            Text(
              text = code,
              style = MaterialTheme.typography.labelMedium,
              color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      OutlinedButton(
        onClick = { confirm = "reject" },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
        modifier = Modifier.weight(1f).testTag("reject_btn_${pUser.id}")
      ) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "Reject", modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Reject")
      }

      Button(
        onClick = { viewModel.approveUser(pUser.id, Role.STUDENT, pUser.programme?.takeIf { it in viewModel.programmesFor(pUser.universityId) }, emptySet()) },
        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
        modifier = Modifier.weight(1f).testTag("approve_btn_${pUser.id}")
      ) {
        Icon(imageVector = Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("As student")
      }
    }

    if (wantsStaff) {
      Spacer(modifier = Modifier.height(8.dp))
      Button(
        onClick = { confirm = "lecturer" },
        colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
        modifier = Modifier.fillMaxWidth().testTag("approve_lecturer_btn_${pUser.id}")
      ) { Text("Approve as lecturer") }
    }
  }

  val action = confirm
  if (action != null) {
    ConfirmSheet(
      title = if (action == "reject") "Reject this account?" else "Give lecturer powers?",
      message = if (action == "reject") "${pUser.fullName} will not be able to use the app."
        else "${pUser.fullName} will be able to post notices and upload files for: ${teaches.sorted().joinToString(", ").ifBlank { "no courses yet" }}. This is recorded in the audit log.",
      confirmLabel = if (action == "reject") "Reject account" else "Approve as lecturer",
      destructive = action == "reject",
      onDismiss = { confirm = null },
      onConfirm = {
        if (action == "reject") viewModel.rejectUser(pUser.id)
        else viewModel.approveUser(pUser.id, Role.LECTURER, null, teaches)
        confirm = null
      },
      confirmTag = "confirm_action_btn"
    )
  }
}
