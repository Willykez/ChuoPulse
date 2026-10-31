package com.willykez.campconnect.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.graphics.Brush
import com.willykez.campconnect.ui.theme.DeepBlueDark
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.campconnect.data.auth.AuthOutcome
import com.willykez.campconnect.data.auth.CodePurpose
import com.willykez.campconnect.data.auth.PasswordStrength
import com.willykez.campconnect.data.model.AccountStatus
import com.willykez.campconnect.data.model.AppLanguage
import com.willykez.campconnect.ui.components.PickerField
import com.willykez.campconnect.ui.components.PickerItem
import com.willykez.campconnect.ui.components.PickerSheet
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.viewmodel.CampusViewModel

private enum class AuthPage { SIGN_IN, SIGN_UP, CODE, FORGOT, RESET, MFA }

private val FieldShape = RoundedCornerShape(14.dp)

/** Shown to anyone who has not signed in. One calm column, one primary action per page. */
@Composable
fun AuthGate(viewModel: CampusViewModel) {
  val language by viewModel.appLanguage.collectAsState()

  var page by remember { mutableStateOf(AuthPage.SIGN_IN) }
  var pendingEmail by remember { mutableStateOf("") }
  var codePurpose by remember { mutableStateOf(CodePurpose.VERIFY_EMAIL) }
  var error by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var mfaEnrolling by remember { mutableStateOf(false) }
  var mfaSecret by remember { mutableStateOf<String?>(null) }
  var mfaUri by remember { mutableStateOf<String?>(null) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  fun go(next: AuthPage) {
    error = null
    page = next
  }

  // Back returns to the sign-in page from any other page instead of closing the app.
  BackHandler(enabled = page != AuthPage.SIGN_IN) { go(AuthPage.SIGN_IN) }

  fun handle(outcome: AuthOutcome) {
    when (outcome) {
      is AuthOutcome.NeedMfa -> {
        mfaEnrolling = outcome.enrolling
        mfaSecret = outcome.secret
        mfaUri = outcome.uri
        notice = null
        go(AuthPage.MFA)
      }
      is AuthOutcome.Failure -> error = viewModel.authErrorText(outcome.error)
      is AuthOutcome.NeedCode -> {
        pendingEmail = outcome.email
        codePurpose = outcome.purpose
        notice = null
        go(if (outcome.purpose == CodePurpose.RESET_PASSWORD) AuthPage.RESET else AuthPage.CODE)
      }
      is AuthOutcome.PasswordChanged -> {
        notice = viewModel.t(
          "Password changed. Your other phones were signed out. Sign in with the new one.",
          "Nenosiri limebadilishwa. Simu zako nyingine zimetolewa. Ingia kwa nenosiri jipya."
        )
        go(AuthPage.SIGN_IN)
      }
      is AuthOutcome.Success -> Unit // the app switches screens by itself
    }
  }

  /** Runs a server call off the main thread, shows progress, and hands the outcome to [handle]. */
  fun launchAuth(call: suspend () -> AuthOutcome) {
    if (busy) return
    busy = true
    error = null
    scope.launch {
      try {
        handle(call())
      } finally {
        busy = false
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.verticalGradient(listOf(DeepBlueDark, DeepBlue, MaterialTheme.colorScheme.background)))
      .systemBarsPadding()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 12.dp)
      .testTag("auth_gate")
  ) {
    // Top row: back (when not on the first page) and the language switch.
    Row(
      modifier = Modifier.fillMaxWidth().height(44.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      if (page != AuthPage.SIGN_IN) {
        IconButton(onClick = { go(AuthPage.SIGN_IN) }, modifier = Modifier.testTag("auth_back_btn")) {
          Icon(Icons.Filled.ArrowBack, contentDescription = viewModel.t("Back", "Rudi"), tint = Color.White)
        }
      } else {
        Spacer(modifier = Modifier.size(1.dp))
      }
      LanguageSwitch(viewModel, language)
    }

    Spacer(modifier = Modifier.height(if (page == AuthPage.SIGN_IN) 12.dp else 4.dp))
    AuthHeader(viewModel, compact = page != AuthPage.SIGN_IN)
    Spacer(modifier = Modifier.height(if (page == AuthPage.SIGN_IN) 22.dp else 14.dp))

    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
      modifier = Modifier.fillMaxWidth().testTag("auth_gate_card")
    ) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
    if (busy) {
      androidx.compose.material3.LinearProgressIndicator(
        color = DeepBlue,
        modifier = Modifier.fillMaxWidth().height(3.dp).testTag("auth_busy")
      )
      Spacer(modifier = Modifier.height(12.dp))
    }
    if (page == AuthPage.SIGN_IN || page == AuthPage.SIGN_UP) {
      // Sign in / Register switch, like a tab bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        AuthTab(viewModel.t("Sign in", "Ingia"), page == AuthPage.SIGN_IN, "tab_sign_in", Modifier.weight(1f)) { notice = null; go(AuthPage.SIGN_IN) }
        AuthTab(viewModel.t("Register", "Jisajili"), page == AuthPage.SIGN_UP, "tab_register", Modifier.weight(1f)) { notice = null; go(AuthPage.SIGN_UP) }
      }
      Spacer(modifier = Modifier.height(18.dp))
    }
    AnimatedContent(
      targetState = page,
      transitionSpec = {
        val forward = targetState.ordinal >= initialState.ordinal
        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (forward) it / 10 else -it / 10 }) togetherWith
          (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { if (forward) -it / 10 else it / 10 })
      },
      label = "auth_page"
    ) { current ->
      Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(4.dp))

        notice?.let {
          Text(it, color = SuccessGreen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("auth_notice"))
          Spacer(modifier = Modifier.height(12.dp))
        }

        when (current) {
          AuthPage.SIGN_IN -> SignInPage(
            viewModel = viewModel,
            error = error,
            onSubmit = { email, password ->
              error = null
              launchAuth { viewModel.signInSuspend(email, password) }
            },
            onForgot = { notice = null; go(AuthPage.FORGOT) },
            onCreate = { notice = null; go(AuthPage.SIGN_UP) }
          )

          AuthPage.SIGN_UP -> SignUpPage(
            viewModel = viewModel,
            error = error,
            onError = { error = it },
            onSubmit = { university, name, email, number, staff, programme, password ->
              error = null
              launchAuth { viewModel.signUpSuspend(university, name, email, number, staff, programme, password) }
            }
          )

          AuthPage.CODE -> CodePage(
            viewModel = viewModel,
            purpose = codePurpose,
            email = pendingEmail,
            error = error,
            onSubmit = { code ->
              error = null
              launchAuth { viewModel.submitCodeSuspend(codePurpose, pendingEmail, code) }
            },
            onResend = {
              error = null
              scope.launch { viewModel.resendCodeSuspend(codePurpose, pendingEmail) }
            }
          )

          AuthPage.FORGOT -> ForgotPage(
            viewModel = viewModel,
            onSubmit = { email -> launchAuth { viewModel.requestPasswordResetSuspend(email) } }
          )

          AuthPage.RESET -> ResetPage(
            viewModel = viewModel,
            email = pendingEmail,
            error = error,
            onError = { error = it },
            onSubmit = { code, password ->
              error = null
              launchAuth { viewModel.resetPasswordSuspend(pendingEmail, code, password) }
            }
          )

          AuthPage.MFA -> MfaPage(
            viewModel = viewModel,
            enrolling = mfaEnrolling,
            secret = mfaSecret,
            uri = mfaUri,
            error = error,
            onSubmit = { code -> launchAuth { viewModel.verifyMfa(code) } }
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
      }
    }
    }
    }
    Spacer(modifier = Modifier.height(16.dp))
  }
}

// ---------- Pages ----------

@Composable
private fun SignInPage(
  viewModel: CampusViewModel,
  error: String?,
  onSubmit: (String, String) -> Unit,
  onForgot: () -> Unit,
  onCreate: () -> Unit
) {
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  val canSubmit = email.isNotBlank() && password.isNotEmpty()

  AuthField(
    value = email, onChange = { email = it }, tag = "auth_email",
    label = viewModel.t("Registration number or email", "Namba ya usajili au barua pepe"),
    icon = Icons.Default.Badge
  )
  Spacer(modifier = Modifier.height(12.dp))
  AuthField(
    value = password, onChange = { password = it }, tag = "auth_password",
    label = viewModel.t("Password", "Nenosiri"),
    icon = Icons.Default.Lock, keyboard = KeyboardType.Password, isPassword = true,
    imeAction = ImeAction.Done, onDone = { if (canSubmit) onSubmit(email, password) }
  )
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = onForgot, modifier = Modifier.testTag("auth_forgot_btn")) {
      Text(viewModel.t("Forgot password?", "Umesahau nenosiri?"))
    }
  }
  ErrorLine(error)
  Spacer(modifier = Modifier.height(8.dp))
  PrimaryAction(viewModel.t("Sign in", "Ingia"), canSubmit, "auth_sign_in_btn") { onSubmit(email, password) }

  Spacer(modifier = Modifier.height(12.dp))
  OutlinedButton(
    onClick = onCreate,
    shape = FieldShape,
    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("auth_create_btn")
  ) { Text(viewModel.t("Create account", "Fungua akaunti"), fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SignUpPage(
  viewModel: CampusViewModel,
  error: String?,
  onError: (String?) -> Unit,
  onSubmit: (university: String, name: String, email: String, number: String, staff: Boolean, programme: String, password: String) -> Unit
) {
  val universities by viewModel.universities.collectAsState()
  var universityId by remember { mutableStateOf("") }
  var pickUniversity by remember { mutableStateOf(false) }
  var pickProgramme by remember { mutableStateOf(false) }
  var name by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var number by remember { mutableStateOf("") }
  var isStaff by remember { mutableStateOf(false) }
  var programme by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }

  val programmes = viewModel.programmesFor(universityId)
  // A campus with set-up programmes offers them as choices; otherwise the person just types theirs.
  val chosenProgramme = if (programmes.isNotEmpty()) programme.takeIf { it in programmes } ?: "" else programme

  PageTitle(
    viewModel.t("Create your account", "Fungua akaunti yako"),
    viewModel.t(
      "Your university's registry approves every account before you can see anything.",
      "Msajili wa chuo chako huidhinisha kila akaunti kabla hujaona chochote."
    )
  )

  // 1. Which university: all your data lives with it, and only its registry approves you.
  PickerField(
    label = viewModel.t("Your university", "Chuo chako"),
    value = universities.firstOrNull { it.id == universityId }?.let { "${it.name} (${it.shortName})" },
    placeholder = viewModel.t("Choose your university", "Chagua chuo chako"),
    onClick = { pickUniversity = true },
    tag = "auth_university",
    leadingIcon = Icons.Default.School
  )
  Text(
    viewModel.t("Cannot find your campus? Ask your university to join ChuoPulse.", "Huoni chuo chako? Mwombe chuo chako kijiunge na ChuoPulse."),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
  )
  Spacer(modifier = Modifier.height(14.dp))

  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    Segment(viewModel.t("Student", "Mwanafunzi"), !isStaff, "auth_type_student", Modifier.weight(1f)) { isStaff = false }
    Segment(viewModel.t("Staff", "Mfanyakazi"), isStaff, "auth_type_staff", Modifier.weight(1f)) { isStaff = true }
  }
  Spacer(modifier = Modifier.height(14.dp))

  AuthField(name, { name = it }, viewModel.t("Full name", "Jina kamili"), "auth_name", icon = Icons.Default.Person)
  Spacer(modifier = Modifier.height(10.dp))

  // 2. The registration number comes first: it is your identity at your university.
  AuthField(
    number, { number = it },
    if (isStaff) viewModel.t("Staff number", "Namba ya mfanyakazi") else viewModel.t("Registration number", "Namba ya usajili"),
    "auth_number", icon = Icons.Default.Badge,
    hint = viewModel.t("Write it exactly as your university does", "Iandike kama chuo chako kinavyoiandika")
  )
  Spacer(modifier = Modifier.height(10.dp))

  if (isStaff) {
    AuthField(programme, { programme = it }, viewModel.t("Department", "Idara"), "auth_department", icon = Icons.Default.Business)
  } else if (universityId.isEmpty()) {
    // The programme list appears once a campus is chosen.
  } else if (programmes.isNotEmpty()) {
    PickerField(
      label = viewModel.t("Programme", "Programu"),
      value = chosenProgramme.ifBlank { null },
      placeholder = viewModel.t("Choose your programme", "Chagua programu yako"),
      onClick = { pickProgramme = true },
      tag = "auth_programme",
      leadingIcon = Icons.Default.School
    )
  } else {
    AuthField(
      programme, { programme = it }, viewModel.t("Programme (optional)", "Programu (hiari)"), "auth_programme_text",
      icon = Icons.Default.School
    )
    Text(
      viewModel.t("Your campus has not added its programmes yet, so type yours.", "Chuo chako hakijaongeza programu bado, kwa hiyo andika yako."),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
  }
  Spacer(modifier = Modifier.height(10.dp))

  // 3. Email is only for codes and password reset. Any address you can open will do.
  AuthField(
    email, { email = it }, viewModel.t("Email", "Barua pepe"), "auth_signup_email",
    icon = Icons.Default.Email, keyboard = KeyboardType.Email,
    hint = viewModel.t("Any email you can open", "Barua pepe yoyote unayoweza kuifungua")
  )
  Text(
    viewModel.t("Used for sign-in codes and to reset your password.", "Hutumika kwa misimbo ya kuingia na kubadilisha nenosiri."),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
  )
  Spacer(modifier = Modifier.height(10.dp))

  AuthField(
    password, { password = it }, viewModel.t("Password", "Nenosiri"), "auth_signup_password",
    icon = Icons.Default.Lock, keyboard = KeyboardType.Password, isPassword = true
  )
  PasswordHint(viewModel, password, email)
  Spacer(modifier = Modifier.height(10.dp))

  val canSubmit = universityId.isNotEmpty() && name.isNotBlank() && email.isNotBlank() && number.isNotBlank() &&
    password.isNotEmpty() && (isStaff || programmes.isEmpty() || chosenProgramme.isNotBlank())
  val submit = {
    if (password != confirm) {
      onError(viewModel.t("The two passwords are not the same.", "Manenosiri mawili hayafanani."))
    } else {
      onSubmit(universityId, name, email, number, isStaff, if (isStaff) programme else chosenProgramme, password)
    }
  }
  AuthField(
    confirm, { confirm = it }, viewModel.t("Repeat password", "Rudia nenosiri"), "auth_confirm_password",
    icon = Icons.Default.Lock, keyboard = KeyboardType.Password, isPassword = true,
    imeAction = ImeAction.Done, onDone = { if (canSubmit) submit() }
  )

  ErrorLine(error)
  Spacer(modifier = Modifier.height(16.dp))
  PrimaryAction(viewModel.t("Create account", "Fungua akaunti"), canSubmit, "auth_sign_up_btn") { submit() }

  if (pickUniversity) {
    PickerSheet(
      title = viewModel.t("Your university", "Chuo chako"),
      options = universities.map { PickerItem(it.id, it.name, it.shortName) },
      selectedId = universityId,
      onSelect = { universityId = it; programme = ""; onError(null) },
      onDismiss = { pickUniversity = false },
      emptyText = viewModel.t("No campus has joined yet.", "Hakuna chuo kilichojiunga bado.")
    )
  }
  if (pickProgramme) {
    PickerSheet(
      title = viewModel.t("Programme", "Programu"),
      options = programmes.map { PickerItem(it, it) },
      selectedId = chosenProgramme,
      onSelect = { programme = it },
      onDismiss = { pickProgramme = false }
    )
  }
}

@Composable
private fun CodePage(
  viewModel: CampusViewModel,
  purpose: CodePurpose,
  email: String,
  error: String?,
  onSubmit: (String) -> Unit,
  onResend: () -> Unit
) {
  var code by remember { mutableStateOf("") }

  PageTitle(
    if (purpose == CodePurpose.SECOND_STEP) viewModel.t("Second step", "Hatua ya pili")
    else viewModel.t("Check your email", "Angalia barua pepe yako"),
    viewModel.t(
      "We sent a code to $email. Enter it below.",
      "Tumetuma msimbo kwa $email. Uingize hapa chini."
    )
  )
  CodeField(
    value = code,
    onChange = { code = it.filter(Char::isDigit).take(8) },
    tag = "auth_code",
    onDone = { if (code.length >= 6) onSubmit(code) }
  )
  ErrorLine(error)
  Spacer(modifier = Modifier.height(16.dp))
  PrimaryAction(viewModel.t("Continue", "Endelea"), code.length >= 6, "auth_code_btn") { onSubmit(code) }
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    TextButton(onClick = onResend) { Text(viewModel.t("Send a new code", "Tuma msimbo mpya")) }
  }
}

@Composable
private fun ForgotPage(viewModel: CampusViewModel, onSubmit: (String) -> Unit) {
  var email by remember { mutableStateOf("") }

  PageTitle(
    viewModel.t("Forgot password", "Umesahau nenosiri"),
    viewModel.t(
      "Enter your university email. If it has an account, we send a code.",
      "Andika barua pepe yako ya chuo. Ikiwa ina akaunti, tutatuma msimbo."
    )
  )
  AuthField(
    email, { email = it }, viewModel.t("University email", "Barua pepe ya chuo"), "auth_forgot_email",
    icon = Icons.Default.Email, keyboard = KeyboardType.Email,
    imeAction = ImeAction.Done, onDone = { if (email.isNotBlank()) onSubmit(email) }
  )
  Spacer(modifier = Modifier.height(16.dp))
  PrimaryAction(viewModel.t("Send code", "Tuma msimbo"), email.isNotBlank(), "auth_forgot_send_btn") { onSubmit(email) }
}

@Composable
private fun ResetPage(
  viewModel: CampusViewModel,
  email: String,
  error: String?,
  onError: (String?) -> Unit,
  onSubmit: (code: String, password: String) -> Unit
) {
  var code by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf("") }

  PageTitle(
    viewModel.t("Choose a new password", "Chagua nenosiri jipya"),
    viewModel.t(
      "If $email has an account, a code was sent to it.",
      "Ikiwa $email ina akaunti, msimbo umetumwa."
    )
  )
  CodeField(value = code, onChange = { code = it.filter(Char::isDigit).take(8) }, tag = "auth_reset_code", onDone = null)
  Spacer(modifier = Modifier.height(12.dp))
  AuthField(
    password, { password = it }, viewModel.t("New password", "Nenosiri jipya"), "auth_reset_password",
    icon = Icons.Default.Lock, keyboard = KeyboardType.Password, isPassword = true
  )
  PasswordHint(viewModel, password, email)
  Spacer(modifier = Modifier.height(10.dp))

  val canSubmit = code.length >= 6 && password.isNotEmpty()
  val submit = {
    if (password != confirm) {
      onError(viewModel.t("The two passwords are not the same.", "Manenosiri mawili hayafanani."))
    } else {
      onSubmit(code, password)
    }
  }
  AuthField(
    confirm, { confirm = it }, viewModel.t("Repeat new password", "Rudia nenosiri jipya"), "auth_reset_confirm",
    icon = Icons.Default.Lock, keyboard = KeyboardType.Password, isPassword = true,
    imeAction = ImeAction.Done, onDone = { if (canSubmit) submit() }
  )
  ErrorLine(error)
  Spacer(modifier = Modifier.height(16.dp))
  PrimaryAction(viewModel.t("Change password", "Badilisha nenosiri"), canSubmit, "auth_reset_btn") { submit() }
}

// ---------- Waiting room ----------

/** What a brand-new (or switched-off) account sees: nothing but its own status. */
@Composable
fun WaitingRoomScreen(viewModel: CampusViewModel) {
  val user by viewModel.currentUser.collectAsState()
  val language by viewModel.appLanguage.collectAsState()
  val notActive = user.status == AccountStatus.REJECTED

  // A slow breathing pulse says "something is happening" without a spinner.
  val pulse = rememberInfiniteTransition(label = "waiting_pulse")
  val pulseAlpha by pulse.animateFloat(
    initialValue = 0.45f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
    label = "waiting_alpha"
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .systemBarsPadding()
      .padding(24.dp)
      .testTag("waiting_room"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      LanguageSwitch(viewModel, language)
    }
    Spacer(modifier = Modifier.weight(1f))
    Box(
      modifier = Modifier
        .size(96.dp)
        .clip(RoundedCornerShape(28.dp))
        .background((if (notActive) DangerRed else WarmAmber).copy(alpha = 0.14f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (notActive) Icons.Default.Block else Icons.Default.HourglassTop,
        contentDescription = null,
        tint = if (notActive) DangerRed else WarmAmber,
        modifier = Modifier.size(44.dp).alpha(if (notActive) 1f else pulseAlpha)
      )
    }
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = if (notActive) viewModel.t("Account not active", "Akaunti haifanyi kazi")
      else viewModel.t("Awaiting approval", "Inasubiri idhini"),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = if (notActive) {
        viewModel.t("Please contact the Academic Registry.", "Tafadhali wasiliana na Msajili wa Taaluma.")
      } else {
        viewModel.t(
          "The Academic Registry checks every new account. You will get a notification as soon as you are approved.",
          "Msajili wa Taaluma hukagua kila akaunti mpya. Utapata arifa mara tu utakapoidhinishwa."
        )
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(user.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val number = user.studentNumber ?: user.staffNumber
        if (number != null) {
          Text(number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Spacer(modifier = Modifier.weight(1f))
    // Check on our own every 15 seconds, and let the person check right now.
    androidx.compose.runtime.LaunchedEffect(Unit) {
      while (true) {
        kotlinx.coroutines.delay(15_000)
        viewModel.refreshProfile()
      }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    com.willykez.campconnect.ui.components.ActionButton(
      label = viewModel.t("Check approval now", "Angalia idhini sasa"),
      icon = Icons.Default.HourglassTop,
      onClick = { scope.launch { viewModel.refreshProfile() } },
      filled = true,
      modifier = Modifier.fillMaxWidth().height(56.dp),
      tag = "waiting_check_btn"
    )
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedButton(
      onClick = { viewModel.signOut() },
      shape = FieldShape,
      modifier = Modifier.fillMaxWidth().height(56.dp).testTag("waiting_sign_out_btn")
    ) { Text(viewModel.t("Sign out", "Toka"), fontWeight = FontWeight.SemiBold) }
  }
}

// ---------- Building blocks (shared by every page) ----------

@Composable
private fun AuthHeader(viewModel: CampusViewModel, compact: Boolean = false) {
  Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier = Modifier.size(if (compact) 44.dp else 68.dp).clip(RoundedCornerShape(if (compact) 14.dp else 22.dp)).background(Color.White),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.School, contentDescription = "ChuoPulse", tint = DeepBlue, modifier = Modifier.size(if (compact) 26.dp else 38.dp))
    }
    Spacer(modifier = Modifier.height(if (compact) 8.dp else 14.dp))
    Text(
      "ChuoPulse",
      style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
    if (!compact) {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = viewModel.t(
          "Your timetable, notices and deadlines in one place.",
          "Ratiba, matangazo na tarehe za mwisho mahali pamoja."
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
private fun AuthTab(label: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(11.dp))
      .background(if (selected) DeepBlue else Color.Transparent)
      .clickable { onClick() }
      .padding(vertical = 11.dp)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Text(
      label,
      fontWeight = FontWeight.Bold,
      style = MaterialTheme.typography.bodyMedium,
      color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
  Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
  Spacer(modifier = Modifier.height(6.dp))
  Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun PrimaryAction(text: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = FieldShape,
    colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
    modifier = Modifier.fillMaxWidth().height(56.dp).testTag(tag)
  ) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LanguageSwitch(viewModel: CampusViewModel, language: AppLanguage) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceVariant
  ) {
    Row(modifier = Modifier.padding(3.dp)) {
      LanguageOption("EN", language == AppLanguage.ENGLISH, "lang_en") {
        if (language != AppLanguage.ENGLISH) viewModel.toggleLanguage()
      }
      LanguageOption("SW", language == AppLanguage.SWAHILI, "lang_sw") {
        if (language != AppLanguage.SWAHILI) viewModel.toggleLanguage()
      }
    }
  }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(17.dp))
      .background(if (selected) DeepBlue else Color.Transparent)
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/** A choice that fills its row: used for Student/Staff and for the programme list. */
@Composable
private fun Segment(label: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
  Surface(
    shape = FieldShape,
    color = if (selected) DeepBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
    border = BorderStroke(
      if (selected) 1.5.dp else 1.dp,
      if (selected) DeepBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    ),
    modifier = modifier
      .clip(FieldShape)
      .clickable { onClick() }
      .testTag(tag)
  ) {
    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) DeepBlue else MaterialTheme.colorScheme.onSurface
      )
    }
  }
}

@Composable
private fun AuthField(
  value: String,
  onChange: (String) -> Unit,
  label: String,
  tag: String,
  icon: ImageVector? = null,
  keyboard: KeyboardType = KeyboardType.Text,
  isPassword: Boolean = false,
  hint: String? = null,
  imeAction: ImeAction = ImeAction.Next,
  onDone: (() -> Unit)? = null
) {
  val focus = LocalFocusManager.current
  var visible by remember { mutableStateOf(false) }

  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    placeholder = if (hint != null) ({ Text(hint) }) else null,
    leadingIcon = if (icon != null) ({ Icon(icon, contentDescription = null) }) else null,
    trailingIcon = if (isPassword) {
      {
        IconButton(onClick = { visible = !visible }) {
          Icon(
            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (visible) "Hide password" else "Show password"
          )
        }
      }
    } else null,
    singleLine = true,
    shape = FieldShape,
    visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
    keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
    keyboardActions = KeyboardActions(
      onNext = { focus.moveFocus(FocusDirection.Down) },
      onDone = {
        focus.clearFocus()
        onDone?.invoke()
      }
    ),
    modifier = Modifier.fillMaxWidth().testTag(tag)
  )
}

/** The six-digit code: large, centred, easy to read back from an email. */
@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, tag: String, onDone: (() -> Unit)?) {
  val focus = LocalFocusManager.current
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    singleLine = true,
    shape = FieldShape,
    textStyle = TextStyle(fontSize = 26.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
    placeholder = {
      Text(
        "000000",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = TextStyle(fontSize = 26.sp, letterSpacing = 8.sp),
        color = MaterialTheme.colorScheme.outline
      )
    },
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.NumberPassword,
      imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next
    ),
    keyboardActions = KeyboardActions(
      onNext = { focus.moveFocus(FocusDirection.Down) },
      onDone = {
        focus.clearFocus()
        onDone?.invoke()
      }
    ),
    modifier = Modifier.fillMaxWidth().testTag(tag)
  )
}

@Composable
private fun ErrorLine(error: String?) {
  if (error != null) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(error, color = DangerRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("auth_error"))
  }
}

/** Three-segment strength bar plus the single most useful tip. */
@Composable
private fun PasswordHint(viewModel: CampusViewModel, password: String, email: String) {
  if (password.isEmpty()) return
  val check = viewModel.checkPassword(password, email)
  val (label, color, level) = when (check.strength) {
    PasswordStrength.WEAK -> Triple(viewModel.t("Weak", "Dhaifu"), DangerRed, 1)
    PasswordStrength.FAIR -> Triple(viewModel.t("Fair", "Wastani"), WarmAmber, 2)
    PasswordStrength.STRONG -> Triple(viewModel.t("Strong", "Imara"), SuccessGreen, 3)
  }
  Spacer(modifier = Modifier.height(8.dp))
  Row(verticalAlignment = Alignment.CenterVertically) {
    repeat(3) { index ->
      val fill by animateFloatAsState(if (index < level) 1f else 0f, tween(250), label = "strength_$index")
      Box(
        modifier = Modifier
          .weight(1f)
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Box(modifier = Modifier.fillMaxWidth(fill).height(4.dp).background(color))
      }
      if (index < 2) Spacer(modifier = Modifier.width(4.dp))
    }
    Spacer(modifier = Modifier.width(10.dp))
    Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
  }
  check.problems.firstOrNull()?.let {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      viewModel.passwordProblemText(it),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/** The administrator's second step: set up (first time) or enter the six digits from the authenticator app. */
@Composable
private fun MfaPage(
  viewModel: CampusViewModel,
  enrolling: Boolean,
  secret: String?,
  uri: String?,
  error: String?,
  onSubmit: (String) -> Unit
) {
  var code by remember { mutableStateOf("") }
  val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
  val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

  PageTitle(
    if (enrolling) viewModel.t("Set up your second step", "Weka hatua ya pili") else viewModel.t("Second step", "Hatua ya pili"),
    if (enrolling) viewModel.t(
      "Administrators confirm every sign-in with an authenticator app. Add the key below to Google Authenticator, Aegis or a similar app, then enter the six digits it shows.",
      "Wasimamizi huthibitisha kila kuingia kwa programu ya uthibitisho. Weka ufunguo ulio hapa chini kwenye Google Authenticator, Aegis au kama hiyo, kisha andika tarakimu sita inazoonyesha."
    ) else viewModel.t(
      "Open your authenticator app and enter the six digits shown for ChuoPulse.",
      "Fungua programu yako ya uthibitisho na uandike tarakimu sita zilizoonyeshwa kwa ChuoPulse."
    )
  )
  if (enrolling && !secret.isNullOrBlank()) {
    androidx.compose.material3.Surface(
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
      modifier = Modifier.fillMaxWidth().testTag("mfa_secret_card")
    ) {
      Column(Modifier.padding(14.dp)) {
        Text(viewModel.t("Setup key", "Ufunguo wa kuweka"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
          secret.chunked(4).joinToString(" "),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          com.willykez.campconnect.ui.components.ActionButton(
            label = viewModel.t("Copy key", "Nakili ufunguo"),
            icon = Icons.Default.ContentCopy,
            onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(secret)) },
            modifier = Modifier.weight(1f),
            tag = "mfa_copy_btn"
          )
          if (!uri.isNullOrBlank()) {
            com.willykez.campconnect.ui.components.ActionButton(
              label = viewModel.t("Open app", "Fungua programu"),
              icon = Icons.Default.OpenInNew,
              onClick = { runCatching { uriHandler.openUri(uri) } },
              modifier = Modifier.weight(1f),
              tag = "mfa_open_btn"
            )
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
  CodeField(
    value = code,
    onChange = { code = it.filter(Char::isDigit).take(6) },
    tag = "mfa_code",
    onDone = { if (code.length == 6) onSubmit(code) }
  )
  ErrorLine(error)
  Spacer(modifier = Modifier.height(16.dp))
  PrimaryAction(viewModel.t("Verify", "Thibitisha"), code.length == 6, "mfa_verify_btn") { onSubmit(code) }
}
