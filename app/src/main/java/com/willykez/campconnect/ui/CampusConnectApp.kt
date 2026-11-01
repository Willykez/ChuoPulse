@file:OptIn(ExperimentalMaterial3Api::class)
package com.willykez.campconnect.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import com.willykez.campconnect.ui.theme.AppThemeSettings
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.willykez.campconnect.data.model.AccountStatus
import com.willykez.campconnect.data.model.Announcement
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.data.model.DeadlineItem
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.StudentSubmission
import com.willykez.campconnect.data.model.StudyMaterial
import com.willykez.campconnect.ui.components.AddDeadlineDialog
import com.willykez.campconnect.ui.components.AnnouncementDetailDialog
import com.willykez.campconnect.ui.components.CancelSessionDialog
import com.willykez.campconnect.ui.components.ChangeVenueDialog
import com.willykez.campconnect.ui.components.DocumentViewerDialog
import com.willykez.campconnect.ui.components.EditDeadlineSheet
import com.willykez.campconnect.ui.components.ViewerAnalysis
import com.willykez.campconnect.ui.components.AnalysisState
import com.willykez.campconnect.data.local.ProcessedDocumentRepository
import com.willykez.campconnect.data.docs.DocumentAnalysisService
import android.widget.Toast
import android.net.Uri
import com.willykez.campconnect.ui.components.EditAnnouncementDialog
import com.willykez.campconnect.ui.components.GradeSubmissionDialog
import com.willykez.campconnect.ui.components.PublishAnnouncementDialog
import com.willykez.campconnect.ui.components.SubmitWorkDialog
import com.willykez.campconnect.ui.components.UploadMaterialDialog
import com.willykez.campconnect.ui.screens.AdminScreen
import com.willykez.campconnect.ui.screens.AuthGate
import com.willykez.campconnect.ui.screens.PlatformScreen
import com.willykez.campconnect.ui.screens.SplashScreen
import com.willykez.campconnect.ui.screens.MaintenanceScreen
import com.willykez.campconnect.ui.screens.WaitingRoomScreen
import com.willykez.campconnect.ui.screens.AnnouncementsScreen
import com.willykez.campconnect.ui.screens.DeadlinesScreen
import com.willykez.campconnect.ui.screens.HomeScreen
import com.willykez.campconnect.ui.screens.MaterialsScreen
import com.willykez.campconnect.ui.screens.ProcessedDocumentsScreen
import com.willykez.campconnect.ui.screens.QuizzesScreen
import com.willykez.campconnect.ui.screens.NotificationsScreen
import com.willykez.campconnect.ui.screens.ProfileScreen
import com.willykez.campconnect.ui.screens.SubmissionsScreen
import com.willykez.campconnect.ui.screens.TimetableScreen
import com.willykez.campconnect.ui.theme.CampusConnectTheme
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.viewmodel.CampusViewModel
import com.willykez.campconnect.viewmodel.ScreenTab

/**
 * Root of the app. Decides what a person may see:
 *  - not signed in -> sign-in screens
 *  - signed in but not approved by the registry -> waiting room, nothing else
 *  - approved -> the app
 */
@Composable
fun CampusConnectApp(
  viewModel: CampusViewModel = viewModel()
) {
  val session by viewModel.session.collectAsState()
  val user by viewModel.currentUser.collectAsState()
  val current = session
  val paused by viewModel.campusPaused.collectAsState()
  val swahili by viewModel.appLanguage.collectAsState()
  var splashDone by rememberSaveable { mutableStateOf(false) }
  val restoring by viewModel.restoring.collectAsState()

  // Back at the very top of the app: the first press asks, a second press within two seconds exits.
  // Screens, tabs, sheets and dialogs register their own Back handling after this one, so they win;
  // this one only runs when nothing else needs Back.
  val rootContext = LocalContext.current
  var lastBackAt by remember { mutableStateOf(0L) }
  BackHandler(enabled = splashDone && !restoring) {
    val now = System.currentTimeMillis()
    if (now - lastBackAt < 2000L) {
      (rootContext as? android.app.Activity)?.finish()
    } else {
      lastBackAt = now
      Toast.makeText(
        rootContext,
        viewModel.t("Press back again to exit", "Bonyeza tena kurudi nyuma ili kutoka"),
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  CampusConnectTheme {
    when {
      !splashDone || restoring -> SplashScreen(
        swahili = swahili == com.willykez.campconnect.data.model.AppLanguage.SWAHILI,
        onFinished = { splashDone = true }
      )
      current == null -> AuthGate(viewModel)
      user.status != AccountStatus.APPROVED -> WaitingRoomScreen(viewModel)
      user.isPlatformAdmin -> PlatformScreen(viewModel)
      // Campus offline mode: the whole campus is paused until the app manager lifts it.
      paused -> MaintenanceScreen(viewModel)
      else -> CampusMainApp(viewModel)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusMainApp(viewModel: CampusViewModel) {
  val context = LocalContext.current
  val systemDark = isSystemInDarkTheme()
  val message by viewModel.message.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(message) {
    message?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.messageShown()
    }
  }
  val remindedIds by viewModel.remindedAnnouncements.collectAsState()
  val currentTab by viewModel.currentTab.collectAsState()

  // Back goes up one level instead of leaving the app: open dialogs and the viewer close on their
  // own, then any other tab returns to Home, and only Home lets the phone close the app.
  BackHandler(enabled = currentTab != ScreenTab.HOME) { viewModel.setTab(ScreenTab.HOME) }
  val currentUser by viewModel.currentUser.collectAsState()
  val isOffline by viewModel.isOffline.collectAsState()
  val syncing by viewModel.syncing.collectAsState()
  val unreadNotifs by viewModel.unreadNotificationsCount.collectAsState()
  val pendingUsers by viewModel.pendingUsers.collectAsState()
  val submissions by viewModel.submissions.collectAsState()

  val sessions by viewModel.sessions.collectAsState()
  val announcements by viewModel.announcements.collectAsState()

  // Dialog & Viewer states
  var selectedAnnouncementId by remember { mutableStateOf<String?>(null) }
  var showPublishDialog by remember { mutableStateOf(false) }
  var showUploadDialog by remember { mutableStateOf(false) }
  var showAddDeadlineDialog by remember { mutableStateOf(false) }
  var venueChangeSessionId by remember { mutableStateOf<String?>(null) }
  var cancelSessionId by remember { mutableStateOf<String?>(null) }

  var editingAnnouncement by remember { mutableStateOf<Announcement?>(null) }
  var submitWorkDeadline by remember { mutableStateOf<DeadlineItem?>(null) }
  var gradingSubmission by remember { mutableStateOf<StudentSubmission?>(null) }

  // Built-in Document Viewer state
  var editingDeadline by remember { mutableStateOf<DeadlineItem?>(null) }
  var viewingDocument by remember { mutableStateOf<AttachmentFile?>(null) }
  var viewingDocAuthor by remember { mutableStateOf<String?>(null) }
  var viewingDocCourse by remember { mutableStateOf<String?>(null) }

  CampusConnectTheme {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .testTag("campus_connect_main_scaffold"),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(DeepBlue),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.School,
                  contentDescription = "Logo",
                  tint = Color.White,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "ChuoPulse",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  // Role Badge
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(
                        when (currentUser.role) {
                          Role.ADMIN -> Color(0xFFDCFCE7)
                          Role.LECTURER -> WarmAmber.copy(alpha = 0.18f)
                          Role.STUDENT -> DeepBlue.copy(alpha = 0.12f)
                        }
                      )
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = currentUser.role.name,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = when (currentUser.role) {
                        Role.ADMIN -> Color(0xFF166534)
                        Role.LECTURER -> Color(0xFFB45309)
                        Role.STUDENT -> DeepBlue
                      },
                      fontSize = 9.sp
                    )
                  }
                }
                Text(
                  text = if (isOffline) viewModel.getString("offline_notice") else if (syncing) viewModel.t("Syncing…", "Inasawazisha…") else "${currentUser.fullName.split(" ").firstOrNull() ?: ""} • Tanzania",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isOffline) WarmAmber else if (syncing) DeepBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          },
          actions = {
            var menuOpen by remember { mutableStateOf(false) }
            val isDark = AppThemeSettings.mode == com.willykez.campconnect.ui.theme.AppThemeMode.DARK ||
              (AppThemeSettings.mode == com.willykez.campconnect.ui.theme.AppThemeMode.SYSTEM && systemDark)
            val pendingReview = submissions.count { it.grade == null }
            Box {
              IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("top_bar_menu_btn")) {
                BadgedBox(
                  badge = {
                    if (pendingReview > 0 && currentUser.role != Role.STUDENT) {
                      Badge(containerColor = WarmAmber, contentColor = Color.White) { Text("$pendingReview") }
                    }
                  }
                ) {
                  Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(DeepBlue),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(currentUser.avatarInitials.ifBlank { "?" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
              DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                  Text(currentUser.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                  Text(currentUser.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                DropdownMenuItem(
                  text = { Text(viewModel.t("My profile", "Wasifu wangu")) },
                  leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                  onClick = { menuOpen = false; viewModel.setTab(ScreenTab.PROFILE) },
                  modifier = Modifier.testTag("menu_profile")
                )
                DropdownMenuItem(
                  text = { Text(viewModel.t("Submissions", "Kazi zilizowasilishwa")) },
                  leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                  onClick = { menuOpen = false; viewModel.setTab(ScreenTab.SUBMISSIONS) },
                  modifier = Modifier.testTag("top_bar_submissions_btn")
                )
                DropdownMenuItem(
                  text = { Text(viewModel.t("AI summaries", "Muhtasari wa AI")) },
                  leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                  onClick = { menuOpen = false; viewModel.setTab(ScreenTab.PROCESSED_DOCUMENTS) },
                  modifier = Modifier.testTag("top_bar_processed_documents_btn")
                )
                DropdownMenuItem(
                  text = { Text(if (isDark) viewModel.t("Light mode", "Mwanga") else viewModel.t("Dark mode", "Giza")) },
                  leadingIcon = { Icon(if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = null) },
                  onClick = { AppThemeSettings.toggle(context, systemDark) },
                  modifier = Modifier.testTag("top_bar_theme_toggle")
                )
                HorizontalDivider()
                DropdownMenuItem(
                  text = { Text(viewModel.t("Sign out", "Toka"), color = com.willykez.campconnect.ui.theme.DangerRed) },
                  leadingIcon = { Icon(Icons.Filled.Logout, contentDescription = null, tint = com.willykez.campconnect.ui.theme.DangerRed) },
                  onClick = { menuOpen = false; viewModel.signOut() },
                  modifier = Modifier.testTag("menu_sign_out")
                )
              }
            }

            if (currentUser.role == Role.ADMIN) {
              IconButton(
                onClick = { viewModel.setTab(ScreenTab.ADMIN) },
                modifier = Modifier.testTag("top_bar_admin_btn")
              ) {
                BadgedBox(
                  badge = {
                    if (pendingUsers.isNotEmpty()) {
                      Badge(containerColor = WarmAmber, contentColor = Color.White) {
                        Text("${pendingUsers.size}")
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Admin Portal",
                    tint = if (currentTab == ScreenTab.ADMIN) DeepBlue else MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }

            IconButton(
              onClick = { viewModel.setTab(ScreenTab.NOTIFICATIONS) },
              modifier = Modifier.testTag("top_bar_notif_btn")
            ) {
              BadgedBox(
                badge = {
                  if (unreadNotifs > 0) {
                    Badge(containerColor = DeepBlue, contentColor = Color.White) {
                      Text("$unreadNotifs")
                    }
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.Default.Notifications,
                  contentDescription = "Notifications",
                  tint = if (currentTab == ScreenTab.NOTIFICATIONS) DeepBlue else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        )
      },
      bottomBar = {
        NavigationBar(
          containerColor = MaterialTheme.colorScheme.surface,
          tonalElevation = 10.dp,
          modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .testTag("bottom_nav_bar")
        ) {
          NavigationBarItem(
            selected = currentTab == ScreenTab.HOME,
            onClick = { viewModel.setTab(ScreenTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text(viewModel.getString("tab_home")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_home")
          )

          NavigationBarItem(
            selected = currentTab == ScreenTab.TIMETABLE,
            onClick = { viewModel.setTab(ScreenTab.TIMETABLE) },
            icon = { Icon(Icons.Default.CalendarToday, contentDescription = "Timetable") },
            label = { Text(viewModel.getString("tab_timetable")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_timetable")
          )

          NavigationBarItem(
            selected = currentTab == ScreenTab.MATERIALS,
            onClick = { viewModel.setTab(ScreenTab.MATERIALS) },
            icon = { Icon(Icons.Default.Folder, contentDescription = "Materials") },
            label = { Text(viewModel.getString("tab_materials")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_materials")
          )

          NavigationBarItem(
            selected = currentTab == ScreenTab.CALENDAR || currentTab == ScreenTab.QUIZZES,
            onClick = { viewModel.setTab(ScreenTab.CALENDAR) },
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Deadlines") },
            label = { Text(viewModel.getString("tab_calendar")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_calendar")
          )

          NavigationBarItem(
            selected = currentTab == ScreenTab.PROFILE,
            onClick = { viewModel.setTab(ScreenTab.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text(viewModel.getString("tab_profile")) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = DeepBlue.copy(alpha = 0.15f)),
            modifier = Modifier.testTag("nav_item_profile")
          )
        }
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        Crossfade(targetState = currentTab, animationSpec = tween(180), label = "tab_change") { tab ->
        when (tab) {
          ScreenTab.HOME -> HomeScreen(
            viewModel = viewModel,
            onNavigateToNotifications = { viewModel.setTab(ScreenTab.NOTIFICATIONS) },
            onOpenAnnouncementDetail = { selectedAnnouncementId = it },
            onOpenPublishDialog = { showPublishDialog = true },
            onOpenChangeVenueDialog = { venueChangeSessionId = it },
            onOpenUploadDialog = { showUploadDialog = true },
            onOpenAddDeadlineDialog = { showAddDeadlineDialog = true }
          )

          ScreenTab.TIMETABLE -> TimetableScreen(
            viewModel = viewModel,
            onOpenChangeVenueDialog = { venueChangeSessionId = it },
            onOpenCancelDialog = { cancelSessionId = it }
          )

          ScreenTab.MATERIALS -> MaterialsScreen(
            viewModel = viewModel,
            onOpenUploadDialog = { showUploadDialog = true },
            onOpenProcessedDocuments = { viewModel.setTab(ScreenTab.PROCESSED_DOCUMENTS) },
            onPreviewDocument = { file, author, course ->
              viewingDocument = file
              viewingDocAuthor = author
              viewingDocCourse = course
            }
          )

          ScreenTab.PROCESSED_DOCUMENTS -> ProcessedDocumentsScreen(
            viewModel = viewModel
          )

          ScreenTab.CALENDAR -> DeadlinesScreen(
            viewModel = viewModel,
            onOpenAddDeadlineDialog = { showAddDeadlineDialog = true },
            onSubmitWorkClick = { dl -> submitWorkDeadline = dl },
            onEditDeadline = { dl -> editingDeadline = dl },
            onOpenAttachment = { file, author, course ->
              viewingDocument = file
              viewingDocAuthor = author
              viewingDocCourse = course
            }
          )

          ScreenTab.QUIZZES -> QuizzesScreen(viewModel = viewModel)

          ScreenTab.SUBMISSIONS -> SubmissionsScreen(
            viewModel = viewModel,
            onPreviewDocument = { file ->
              viewingDocument = file
              viewingDocAuthor = null
              viewingDocCourse = null
            },
            onGradeSubmission = { sub -> gradingSubmission = sub }
          )

          ScreenTab.PROFILE -> ProfileScreen(
            viewModel = viewModel
          )

          ScreenTab.ADMIN -> AdminScreen(
            viewModel = viewModel
          )

          ScreenTab.NOTIFICATIONS -> NotificationsScreen(
            viewModel = viewModel
          )
        }
        }
      }
      }

      // Announcement Detail Dialog
      selectedAnnouncementId?.let { id ->
        val announcement = announcements.find { it.id == id }
        if (announcement != null) {
          AnnouncementDetailDialog(
            announcement = announcement,
            currentUserRole = currentUser.role,
            canModerate = viewModel.canEditOrDelete(announcement.authorId),
            onDismiss = { selectedAnnouncementId = null },
            onPreviewAttachment = { file ->
              viewingDocument = file
              viewingDocAuthor = announcement.authorName
              viewingDocCourse = announcement.audienceName
            },
            onEditClick = {
              selectedAnnouncementId = null
              editingAnnouncement = announcement
            },
            onDeleteClick = {
              viewModel.deleteAnnouncement(announcement.id)
              selectedAnnouncementId = null
            },
            onToggleApproval = { status ->
              viewModel.setAnnouncementApproval(announcement.id, status)
            },
            showReadStats = currentUser.role != Role.STUDENT && viewModel.canEditOrDelete(announcement.authorId),
            reminderSent = announcement.id in remindedIds,
            onRemindClick = { viewModel.remindUnread(announcement.id) },
            onRetractClick = {
              viewModel.retractAnnouncement(announcement.id)
              selectedAnnouncementId = null
            }
          )
        }
      }

      // Publish Announcement Dialog
      if (showPublishDialog) {
        PublishAnnouncementDialog(
          options = viewModel.audienceOptions(),
          urgentLeft = viewModel.urgentLeftToday(),
          canUseUrgentFor = { viewModel.canUseUrgentFor(it) },
          onDismiss = { showPublishDialog = false },
          lecturersFor = { viewModel.lecturersFor(it) },
          onPublish = { title, body, audienceType, audienceName, audienceCode, priority, attachment, responsible ->
            viewModel.publishAnnouncement(title, body, audienceType, audienceName, audienceCode, priority, attachment, responsible)
          },
          onPreviewAttachment = { file ->
            viewingDocument = file
          }
        )
      }

      // Edit Announcement Dialog
      editingAnnouncement?.let { ann ->
        EditAnnouncementDialog(
          announcement = ann,
          onDismiss = { editingAnnouncement = null },
          onSave = { newTitle, newBody, newPriority ->
            viewModel.updateAnnouncement(ann.id, newTitle, newBody, newPriority)
          }
        )
      }

      // Upload Study Material Dialog
      if (showUploadDialog) {
        UploadMaterialDialog(
          onDismiss = { showUploadDialog = false },
          defaultCourseCode = currentUser.teachesCourses.minOrNull() ?: "CS210",
          onUpload = { title, code, courseTitle, desc, attachment ->
            viewModel.uploadMaterial(title, code, courseTitle, desc, attachment)
          },
          onPreviewAttachment = { file ->
            viewingDocument = file
          }
        )
      }

      // Add Deadline Dialog
      if (showAddDeadlineDialog) {
        AddDeadlineDialog(
          onDismiss = { showAddDeadlineDialog = false },
          courses = viewModel.schedulableCourses(),
          defaultCourseCode = currentUser.teachesCourses.minOrNull() ?: "",
          onAdd = { code, courseTitle, title, instructions, dueDateTime, formats, attachment ->
            viewModel.addDeadline(code, courseTitle, title, instructions, dueDateTime, formats, attachment)
          },
          onPreviewAttachment = { file -> viewingDocument = file }
        )
      }

      // Edit / reschedule a deadline
      editingDeadline?.let { dl ->
        EditDeadlineSheet(
          deadline = dl,
          onDismiss = { editingDeadline = null },
          onSave = { title, instructions, due, formats, attachment, clear ->
            viewModel.updateDeadline(dl.id, title, instructions, due, formats, attachment, clear)
          },
          onPreviewAttachment = { file -> viewingDocument = file }
        )
      }

      // Student Coursework Submit Dialog
      submitWorkDeadline?.let { dl ->
        SubmitWorkDialog(
          deadline = dl,
          onDismiss = { submitWorkDeadline = null },
          onSubmit = { notes, file ->
            viewModel.submitStudentWork(
              deadlineId = dl.id,
              courseCode = dl.courseCode,
              assignmentTitle = dl.title,
              notes = notes,
              attachment = file
            )
          },
          onPreviewFile = { file ->
            viewingDocument = file
            viewingDocAuthor = currentUser.fullName
            viewingDocCourse = dl.courseCode
          }
        )
      }

      // Lecturer / Admin Grade Submission Dialog
      gradingSubmission?.let { sub ->
        GradeSubmissionDialog(
          submission = sub,
          onDismiss = { gradingSubmission = null },
          onPreviewDocument = { file ->
            viewingDocument = file
            viewingDocAuthor = sub.studentName
            viewingDocCourse = sub.courseCode
          },
          onGradeSubmitted = { grade, feedback ->
            viewModel.gradeStudentWork(sub.id, grade, feedback)
          }
        )
      }

      // Change Venue Dialog
      venueChangeSessionId?.let { sId ->
        val session = sessions.find { it.id == sId }
        if (session != null) {
          ChangeVenueDialog(
            session = session,
            onDismiss = { venueChangeSessionId = null },
            onConfirm = { newVenue, reason ->
              viewModel.updateSessionVenue(sId, newVenue, reason)
            }
          )
        }
      }

      // Cancel Session Dialog
      cancelSessionId?.let { sId ->
        val session = sessions.find { it.id == sId }
        if (session != null) {
          CancelSessionDialog(
            session = session,
            onDismiss = { cancelSessionId = null },
            onConfirm = { reason ->
              viewModel.cancelSession(sId, reason)
            }
          )
        }
      }

      // Built-in Document Viewer Dialog
      viewingDocument?.let { doc ->
        val appContext = LocalContext.current
        val analysisService = remember(appContext) { DocumentAnalysisService.get(appContext) }
        val libraryFlow = remember(appContext) { ProcessedDocumentRepository(appContext).observeDocuments() }
        val library by libraryFlow.collectAsState(initial = emptyList())
        var analysisId by remember(doc) { mutableStateOf<String?>(null) }
        val entry = library.firstOrNull { it.id == analysisId }
        DocumentViewerDialog(
          attachment = doc,
          authorOrUploaderName = viewingDocAuthor,
          courseCode = viewingDocCourse,
          onDismiss = {
            viewingDocument = null
            viewingDocAuthor = null
            viewingDocCourse = null
          },
          analysis = ViewerAnalysis(
            state = when (entry?.extractionStatus) {
              null -> AnalysisState.NONE
              DocumentAnalysisService.STATUS_COMPLETED -> AnalysisState.DONE
              DocumentAnalysisService.STATUS_PROCESSING -> AnalysisState.RUNNING
              else -> AnalysisState.FAILED
            },
            onAnalyse = {
              if (entry != null) {
                analysisService.retry(entry)
              } else {
                val code = viewingDocCourse?.takeIf { it.isNotBlank() } ?: "GENERAL"
                val titles = viewModel.catalogCourses.value[currentUser.universityId].orEmpty()
                analysisId = analysisService.enqueue(
                  DocumentAnalysisService.Request(
                    uri = Uri.parse(doc.uriString),
                    fileName = doc.fileName,
                    mimeType = doc.mimeType,
                    courseCode = code,
                    courseTitle = titles[code] ?: code,
                    university = currentUser.universityId,
                    uploaderName = currentUser.fullName,
                    category = "Notes"
                  )
                )
                Toast.makeText(appContext, viewModel.t("Analysing\u2026 the result will appear in AI Summaries.", "Inachambua\u2026 matokeo yataonekana kwenye Muhtasari wa AI."), Toast.LENGTH_LONG).show()
              }
            },
            onViewResult = {
              viewingDocument = null
              viewingDocAuthor = null
              viewingDocCourse = null
              viewModel.setTab(ScreenTab.PROCESSED_DOCUMENTS)
            }
          ).takeIf { doc.uriString != null }
        )
      }
    }
  }
}
