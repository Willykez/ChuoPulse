package com.willykez.campconnect.data.auth

import com.willykez.campconnect.data.model.University
import org.json.JSONObject
import com.willykez.campconnect.data.supabase.SupabaseClient
import com.willykez.campconnect.data.supabase.Backend
import com.willykez.campconnect.data.model.AccountStatus
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.data.model.UserProfile
import com.willykez.campconnect.data.repository.CampusCatalog
import com.willykez.campconnect.data.repository.PlatformConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

object AuthConfig {
  const val MIN_PASSWORD_LENGTH = 8
}

enum class CodePurpose { VERIFY_EMAIL, SECOND_STEP, RESET_PASSWORD }

enum class PasswordStrength { WEAK, FAIR, STRONG }

enum class PasswordProblem { TOO_SHORT, COMMON, CONTAINS_EMAIL, REPEATED }

data class PasswordCheck(val strength: PasswordStrength, val problems: List<PasswordProblem>) {
  val acceptable: Boolean get() = problems.isEmpty()
}

enum class AuthError {
  NAME_REQUIRED,
  EMAIL_INVALID,
  UNIVERSITY_REQUIRED,
  USE_EMAIL,
  EMAIL_TAKEN,
  NUMBER_INVALID,
  NUMBER_TAKEN,
  PROGRAMME_REQUIRED,
  PASSWORD_WEAK,
  WRONG_CREDENTIALS,
  ACCOUNT_NOT_ACTIVE,
  CODE_WRONG,
  CODE_EXPIRED,
  TOO_MANY_ATTEMPTS,
  /** The server accepted the request but could not send the email (email service not set up, or recipient not allowed). */
  EMAIL_SEND_FAILED,
  /** Too many emails requested in a short time. */
  RATE_LIMITED,
  /** The server could not be reached, or it failed. Nothing was changed. */
  NETWORK
}

sealed interface AuthOutcome {
  /** A session was started. */
  data class Success(val userId: String) : AuthOutcome

  /** A six-digit code was issued and must be entered next. */
  data class NeedCode(val purpose: CodePurpose, val email: String) : AuthOutcome

  /** Password was changed. The person now signs in with the new one. */
  object PasswordChanged : AuthOutcome

  data class Failure(val error: AuthError) : AuthOutcome

  /**
   * An administrator must finish the second step (authenticator app) before the session starts.
   * [enrolling] is true the first time: [secret] and [uri] then set the authenticator app up.
   */
  data class NeedMfa(val enrolling: Boolean, val secret: String?, val uri: String?) : AuthOutcome
}

data class Session(val userId: String, val deviceId: String)

data class DeviceSession(val id: String, val label: String, val lastActive: String)

data class Account(
  val profile: UserProfile,
  val salt: String,
  val passwordHash: String,
  val emailVerified: Boolean,
  val trustedDevices: Set<String> = emptySet()
)

/**
 * Sign-in, sign-up, email codes, password reset, second step and the registry's account actions.
 * The server (Supabase Auth) decides every outcome; this class keeps the signed-in person and the
 * directory of accounts the phone has been told about.
 */
class AuthRepository(
  private val catalog: CampusCatalog = CampusCatalog()
) {

  val thisDeviceId: String = "dev-" + UUID.randomUUID().toString().take(8)

  // Nobody is held on the phone until the server returns them.
  private val _accounts = MutableStateFlow<Map<String, Account>>(emptyMap())
  val accounts: StateFlow<Map<String, Account>> = _accounts.asStateFlow()

  private val _session = MutableStateFlow<Session?>(null)
  val session: StateFlow<Session?> = _session.asStateFlow()

  private val _devices = MutableStateFlow<Map<String, List<DeviceSession>>>(emptyMap())
  val devices: StateFlow<Map<String, List<DeviceSession>>> = _devices.asStateFlow()

  /** Wired by the data repository: (userId, title, message). */
  var onNotify: (String, String, String) -> Unit = { _, _, _ -> }

  // ---------- Password rules ----------

  private val commonPasswords = setOf(
    "password", "password1", "password123", "12345678", "123456789", "1234567890",
    "qwerty123", "qwertyuiop", "iloveyou", "admin123", "welcome1", "letmein123",
    "abc12345", "11111111", "campus123", "university", "tanzania1", "dar es salaam"
  )

  fun checkPassword(password: String, email: String = ""): PasswordCheck {
    val problems = mutableListOf<PasswordProblem>()
    if (password.length < AuthConfig.MIN_PASSWORD_LENGTH) problems += PasswordProblem.TOO_SHORT
    if (password.lowercase() in commonPasswords) problems += PasswordProblem.COMMON
    val local = email.trim().substringBefore('@').lowercase()
    if (local.length >= 3 && password.lowercase().contains(local)) problems += PasswordProblem.CONTAINS_EMAIL
    if (password.isNotEmpty() && password.toSet().size <= 2) problems += PasswordProblem.REPEATED

    val strength = if (problems.isNotEmpty()) {
      PasswordStrength.WEAK
    } else {
      var score = 0
      if (password.length >= 12) score++
      if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
      if (password.any { !it.isLetterOrDigit() }) score++
      if (score >= 2) PasswordStrength.STRONG else PasswordStrength.FAIR
    }
    return PasswordCheck(strength, problems)
  }

  // ---------- Server sign-in, sign-up, codes and password reset ----------

  private data class LivePending(
    val access: String,
    val refresh: String?,
    val profile: UserProfile,
    val factorId: String
  )

  private var liveAccess: String? = null
  private var liveRefresh: String? = null
  private var pendingMfa: LivePending? = null
  private var uniAppIdByUuid: Map<String, String> = emptyMap()
  private var programmeNameById: Map<String, String> = emptyMap()

  /** The signed-in person's access token, for calls to the server. Null when signed out. */
  val accessToken: String? get() = liveAccess

  private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).trim().ifBlank { null }

  /** Loads the campuses (and their programmes) people can choose at sign-up. Safe to call again. */
  suspend fun loadDirectory(): Boolean {
    return try {
      val u = Backend.client.select(
        "universities",
        "select=id,code,name,location,contact_email,contact_phone,under_maintenance,maintenance_note,is_platform&order=name",
        ""
      )
      if (!u.isSuccessful) return false
      val rows = u.jsonArray()
      val ids = mutableMapOf<String, String>()
      val list = mutableListOf<University>()
      for (i in 0 until rows.length()) {
        val o = rows.getJSONObject(i)
        val uuid = o.getString("id")
        val code = o.getString("code")
        if (o.optBoolean("is_platform")) {
          ids[uuid] = PlatformConfig.ID
          continue
        }
        val appId = code.lowercase()
        ids[uuid] = appId
        list += University(
          id = appId, name = o.getString("name"), shortName = code,
          location = o.text("location").orEmpty(), contactEmail = o.text("contact_email").orEmpty(),
          contactPhone = o.text("contact_phone").orEmpty(),
          underMaintenance = o.optBoolean("under_maintenance"), maintenanceNote = o.text("maintenance_note").orEmpty()
        )
      }
      uniAppIdByUuid = ids
      catalog.replaceUniversities(list)

      // Programmes are optional here: sign-up works without them (a student can type theirs).
      val p = Backend.client.select("programmes", "select=id,name,university_id&order=name", "")
      if (p.isSuccessful) {
        val prow = p.jsonArray()
        val byUni = mutableMapOf<String, MutableMap<String, Set<String>>>()
        val names = mutableMapOf<String, String>()
        for (i in 0 until prow.length()) {
          val o = prow.getJSONObject(i)
          val app = ids[o.optString("university_id")] ?: continue
          byUni.getOrPut(app) { mutableMapOf() }[o.getString("name")] = emptySet()
          names[o.getString("id")] = o.getString("name")
        }
        programmeNameById = names
        catalog.replaceProgrammes(byUni)
      }
      true
    } catch (e: Exception) {
      false
    }
  }

  /**
   * The signed-in person changes their own password (for example after an administrator gave them a
   * starting one). The current password is checked first, so a borrowed unlocked phone cannot change it.
   */
  suspend fun changePassword(currentPassword: String, newPassword: String): AuthOutcome {
    val id = _session.value?.userId ?: return AuthOutcome.Failure(AuthError.WRONG_CREDENTIALS)
    val email = _accounts.value[id]?.profile?.email ?: return AuthOutcome.Failure(AuthError.WRONG_CREDENTIALS)
    if (!checkPassword(newPassword, email).acceptable) return AuthOutcome.Failure(AuthError.PASSWORD_WEAK)
    var access = liveAccess ?: return AuthOutcome.Failure(AuthError.WRONG_CREDENTIALS)
    val check = try { Backend.client.signIn(email, currentPassword) } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    if (!check.isSuccessful) return AuthOutcome.Failure(if (check.code >= 500) AuthError.NETWORK else AuthError.WRONG_CREDENTIALS)
    safeJson(check).optString("access_token").takeIf { it.isNotBlank() }?.let { runCatching { Backend.client.signOut(it) } }
    var u = try { Backend.client.updatePassword(access, newPassword) } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    if (u.code == 401) {
      access = refreshAccessToken() ?: return AuthOutcome.Failure(AuthError.CODE_EXPIRED)
      u = try { Backend.client.updatePassword(access, newPassword) } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    }
    return if (u.isSuccessful) AuthOutcome.PasswordChanged else AuthOutcome.Failure(AuthError.PASSWORD_WEAK)
  }

  /** Checks the details typed for a person an administrator is adding. Null when they are fine. */
  fun checkAdminDetails(fullName: String, email: String, idNumber: String, password: String): AuthError? {
    val cleanEmail = normalizeEmail(email)
    return when {
      fullName.trim().length < 3 -> AuthError.NAME_REQUIRED
      !EMAIL_REGEX.matches(cleanEmail) -> AuthError.EMAIL_INVALID
      idNumber.trim().length < 2 -> AuthError.NUMBER_INVALID
      !checkPassword(password, cleanEmail).acceptable -> AuthError.PASSWORD_WEAK
      else -> null
    }
  }

  /** Gets a new access token with the refresh token (the old one lasts about an hour). Null if that fails. */
  suspend fun refreshAccessToken(): String? {
    val refresh = liveRefresh ?: return null
    val rr = runCatching { Backend.client.refresh(refresh) }.getOrNull() ?: return null
    if (!rr.isSuccessful) return null
    val j = safeJson(rr)
    val access = j.optString("access_token").ifBlank { return null }
    liveAccess = access
    liveRefresh = j.text("refresh_token") ?: refresh
    return access
  }

  /** The database id of a campus, from the id the app uses (its lower-case code). */
  fun universityUuid(appId: String): String? = uniAppIdByUuid.entries.firstOrNull { it.value == appId }?.key

  /** The database id of a programme by name, within one campus. */
  fun programmeUuid(universityAppId: String, name: String?): String? {
    if (name.isNullOrBlank()) return null
    return programmeNameById.entries.firstOrNull { it.value.equals(name.trim(), ignoreCase = true) }?.key
  }

  /**
   * Loads the accounts the signed-in administrator is allowed to see (row-level security limits this to
   * their own campus) so the approvals list shows real people. A student or lecturer only sees themselves.
   */
  suspend fun loadAccounts(): Boolean {
    val access = liveAccess ?: return false
    return try {
      if (uniAppIdByUuid.isEmpty()) loadDirectory()
      val r = Backend.client.select("profiles", "select=*&order=created_at.desc&limit=500", access)
      if (!r.isSuccessful) return false
      val rows = r.jsonArray()
      val me = _session.value?.userId
      val loaded = mutableMapOf<String, Account>()
      for (i in 0 until rows.length()) {
        val o = rows.getJSONObject(i)
        val p = profileFrom(o)
        if (p.id == me) continue // my own record is kept by the sign-in
        loaded[p.id] = Account(p, "live", "", emailVerified = o.optBoolean("email_verified"))
      }
      // Who teaches / studies what (administrators see their whole campus).
      val teach = Backend.client.select("teaching_assignments", "select=lecturer_id,course_code&limit=5000", access)
      val enrol = Backend.client.select("enrollments", "select=student_id,course_code&limit=20000", access)
      val teachMap = mutableMapOf<String, MutableSet<String>>()
      val enrolMap = mutableMapOf<String, MutableSet<String>>()
      if (teach.isSuccessful) teach.jsonArray().let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let {
        teachMap.getOrPut(it.getString("lecturer_id")) { mutableSetOf() }.add(it.getString("course_code").substringAfter(':')) } }
      if (enrol.isSuccessful) enrol.jsonArray().let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let {
        enrolMap.getOrPut(it.getString("student_id")) { mutableSetOf() }.add(it.getString("course_code").substringAfter(':')) } }
      val withCourses = loaded.mapValues { (id, acc) ->
        acc.copy(profile = acc.profile.copy(
          teachesCourses = teachMap[id].orEmpty().ifEmpty { acc.profile.teachesCourses },
          enrolledCourses = enrolMap[id].orEmpty().ifEmpty { acc.profile.enrolledCourses }))
      }
      _accounts.value = _accounts.value.filterKeys { it == me } + withCourses
      true
    } catch (e: Exception) {
      false
    }
  }

  private fun profileFrom(row: JSONObject): UserProfile {
    val platform = row.optBoolean("is_platform_admin")
    val uni = if (platform) PlatformConfig.ID else uniAppIdByUuid[row.optString("university_id")] ?: row.optString("university_id")
    val role = when (row.optString("role")) {
      "admin" -> Role.ADMIN
      "lecturer" -> Role.LECTURER
      else -> Role.STUDENT
    }
    val requested = if (row.optString("requested_role") == "lecturer") Role.LECTURER else Role.STUDENT
    val status = when (row.optString("status")) {
      "approved" -> AccountStatus.APPROVED
      "pending" -> AccountStatus.PENDING
      else -> AccountStatus.REJECTED // rejected or suspended: not allowed in
    }
    val programme = row.text("department") ?: row.text("programme_id")?.let { programmeNameById[it] }
    return UserProfile(
      id = row.getString("id"),
      fullName = row.optString("full_name"),
      email = row.optString("email"),
      role = role,
      status = status,
      universityId = uni,
      studentNumber = row.text("student_number"),
      staffNumber = row.text("staff_number"),
      programme = programme,
      cohort = row.text("cohort"),
      requestedRole = requested,
      isPlatformAdmin = platform,
      isClassRep = row.optBoolean("is_class_rep")
    )
  }

  /** Updates one loaded profile in place (courses, class-representative mark). */
  fun updateProfile(id: String, change: (UserProfile) -> UserProfile) {
    val acc = _accounts.value[id] ?: return
    putAccount(acc.copy(profile = change(acc.profile)))
  }

  /** The signed-in person's id, or null. */
  val myId: String? get() = _session.value?.userId

  /** Short (CS210) -> server course code (CUOM:CS210) for this campus. */
  fun serverCourseCode(universityCode: String, short: String): String =
    if (short.contains(':')) short else "${universityCode.uppercase()}:${short.trim().uppercase()}"

  private fun safeJson(r: SupabaseClient.Result): JSONObject = try { r.jsonObject() } catch (e: Exception) { JSONObject() }

  suspend fun liveSignIn(identifier: String, password: String): AuthOutcome {
    val email = normalizeEmail(identifier)
    // The server signs in by email. A registration number alone cannot be looked up before sign-in.
    if (!EMAIL_REGEX.matches(email)) return AuthOutcome.Failure(AuthError.USE_EMAIL)
    val r = try { Backend.client.signIn(email, password) } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    if (!r.isSuccessful) {
      return when {
        safeJson(r).optString("error_code") == "email_not_confirmed" -> {
          runCatching { Backend.client.resendSignupCode(email) }
          AuthOutcome.NeedCode(CodePurpose.VERIFY_EMAIL, email)
        }
        r.code >= 500 || r.code == 0 -> AuthOutcome.Failure(AuthError.NETWORK)
        else -> AuthOutcome.Failure(AuthError.WRONG_CREDENTIALS)
      }
    }
    return finishLiveSignIn(safeJson(r))
  }

  suspend fun liveSignUp(
    universityId: String,
    fullName: String,
    email: String,
    idNumber: String,
    isStaff: Boolean,
    programmeOrDepartment: String?,
    password: String
  ): AuthOutcome {
    val cleanEmail = normalizeEmail(email)
    if (fullName.trim().length < 3) return AuthOutcome.Failure(AuthError.NAME_REQUIRED)
    if (!EMAIL_REGEX.matches(cleanEmail)) return AuthOutcome.Failure(AuthError.EMAIL_INVALID)
    if (universityId.isBlank()) return AuthOutcome.Failure(AuthError.UNIVERSITY_REQUIRED)
    if (idNumber.trim().length < 2) return AuthOutcome.Failure(AuthError.NUMBER_INVALID)
    if (!isStaff && programmeOrDepartment.isNullOrBlank()) return AuthOutcome.Failure(AuthError.PROGRAMME_REQUIRED)
    if (!checkPassword(password, cleanEmail).acceptable) return AuthOutcome.Failure(AuthError.PASSWORD_WEAK)

    val meta = JSONObject()
      .put("university", universityId.trim().uppercase())
      .put("full_name", fullName.trim())
      .put("is_staff", isStaff)
      .put("id_number", idNumber.trim())
      .put(if (isStaff) "department" else "programme", programmeOrDepartment?.trim().orEmpty())
    val r = try { Backend.client.signUp(cleanEmail, password, meta) } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    if (r.isSuccessful) return AuthOutcome.NeedCode(CodePurpose.VERIFY_EMAIL, cleanEmail)
    val code = safeJson(r).optString("error_code")
    val msg = safeJson(r).let { it.optString("msg").ifBlank { it.optString("message") } }.lowercase()
    return AuthOutcome.Failure(
      when {
        code == "user_already_exists" || code == "email_exists" -> AuthError.EMAIL_TAKEN
        code == "weak_password" -> AuthError.PASSWORD_WEAK
        code == "over_email_send_rate_limit" || r.code == 429 -> AuthError.RATE_LIMITED
        code == "email_address_not_authorized" || msg.contains("sending") || msg.contains("smtp") -> AuthError.EMAIL_SEND_FAILED
        // The database refuses a sign-up with a generic "Database error saving new user": the usual
        // cause is a registration or staff number already used at that campus.
        r.code == 500 -> AuthError.NUMBER_TAKEN
        else -> AuthError.NETWORK
      }
    )
  }

  suspend fun liveSubmitCode(purpose: CodePurpose, email: String, code: String): AuthOutcome {
    if (purpose != CodePurpose.VERIFY_EMAIL) return AuthOutcome.Failure(AuthError.CODE_EXPIRED)
    val r = try { Backend.client.verifyOtp(normalizeEmail(email), code, "signup") } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    if (!r.isSuccessful) {
      return AuthOutcome.Failure(if (r.code >= 500) AuthError.NETWORK else if (r.body.contains("expired", ignoreCase = true)) AuthError.CODE_EXPIRED else AuthError.CODE_WRONG)
    }
    return finishLiveSignIn(safeJson(r))
  }

  suspend fun liveResend(purpose: CodePurpose, email: String) {
    runCatching {
      if (purpose == CodePurpose.RESET_PASSWORD) Backend.client.recover(normalizeEmail(email))
      else Backend.client.resendSignupCode(normalizeEmail(email))
    }
  }

  suspend fun liveRequestReset(email: String): AuthOutcome {
    val clean = normalizeEmail(email)
    val r = try { Backend.client.recover(clean) } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    if (!r.isSuccessful) {
      // An unknown email still answers 200 (no account enumeration), so a failure here is a real
      // delivery problem and the person must be told instead of waiting for a code that never comes.
      val j = safeJson(r)
      val c = j.optString("error_code")
      val msg = j.optString("msg").ifBlank { j.optString("message") }.lowercase()
      return AuthOutcome.Failure(
        when {
          r.code == 429 || c == "over_email_send_rate_limit" -> AuthError.RATE_LIMITED
          c == "email_address_not_authorized" || r.code >= 500 || msg.contains("sending") -> AuthError.EMAIL_SEND_FAILED
          else -> AuthError.NETWORK
        }
      )
    }
    return AuthOutcome.NeedCode(CodePurpose.RESET_PASSWORD, clean)
  }

  suspend fun liveResetPassword(email: String, code: String, newPassword: String): AuthOutcome {
    if (!checkPassword(newPassword, email).acceptable) return AuthOutcome.Failure(AuthError.PASSWORD_WEAK)
    val v = try { Backend.client.verifyOtp(normalizeEmail(email), code, "recovery") } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    if (!v.isSuccessful) return AuthOutcome.Failure(if (v.body.contains("expired", ignoreCase = true)) AuthError.CODE_EXPIRED else AuthError.CODE_WRONG)
    val access = safeJson(v).optString("access_token")
    if (access.isBlank()) return AuthOutcome.Failure(AuthError.CODE_WRONG)
    val u = try { Backend.client.updatePassword(access, newPassword) } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    runCatching { Backend.client.signOut(access) } // the reset session is not kept
    return if (u.isSuccessful) AuthOutcome.PasswordChanged else AuthOutcome.Failure(AuthError.PASSWORD_WEAK)
  }

  /** Reads the profile with the new token and either starts the session or asks for the second step. */
  private suspend fun finishLiveSignIn(json: JSONObject): AuthOutcome {
    val access = json.optString("access_token")
    val refresh = json.text("refresh_token")
    val userId = json.optJSONObject("user")?.optString("id").orEmpty()
    if (access.isBlank() || userId.isBlank()) return AuthOutcome.Failure(AuthError.WRONG_CREDENTIALS)

    if (uniAppIdByUuid.isEmpty()) loadDirectory()
    val pr = try { Backend.client.getProfile(access, userId) } catch (e: Exception) { return AuthOutcome.Failure(AuthError.NETWORK) }
    val row = try { pr.jsonArray().optJSONObject(0) } catch (e: Exception) { null }
      ?: return AuthOutcome.Failure(AuthError.ACCOUNT_NOT_ACTIVE)
    val profile = profileFrom(row)
    if (profile.status == AccountStatus.REJECTED) return AuthOutcome.Failure(AuthError.ACCOUNT_NOT_ACTIVE)

    // Administrators finish a second step (authenticator app) because the database asks for it.
    if (profile.role == Role.ADMIN && profile.status == AccountStatus.APPROVED) {
      return beginMfa(access, refresh, profile)
    }
    return startLiveSession(access, refresh, profile)
  }

  private suspend fun beginMfa(access: String, refresh: String?, profile: UserProfile): AuthOutcome {
    return try {
      val me = Backend.client.getUser(access)
      var factorId: String? = null
      val factors = safeJson(me).optJSONArray("factors")
      if (factors != null) {
        for (i in 0 until factors.length()) {
          val f = factors.getJSONObject(i)
          if (f.optString("factor_type") == "totp" && f.optString("status") == "verified") { factorId = f.getString("id"); break }
        }
      }
      if (factorId != null) {
        pendingMfa = LivePending(access, refresh, profile, factorId)
        AuthOutcome.NeedMfa(enrolling = false, secret = null, uri = null)
      } else {
        val e = Backend.client.mfaEnroll(access, "ChuoPulse " + System.currentTimeMillis())
        if (!e.isSuccessful) return AuthOutcome.Failure(AuthError.NETWORK)
        val o = safeJson(e)
        val totp = o.optJSONObject("totp")
        pendingMfa = LivePending(access, refresh, profile, o.getString("id"))
        AuthOutcome.NeedMfa(enrolling = true, secret = totp?.text("secret"), uri = totp?.text("uri"))
      }
    } catch (e: Exception) {
      AuthOutcome.Failure(AuthError.NETWORK)
    }
  }

  /** Completes the administrator's second step with the six digits from the authenticator app. */
  suspend fun liveVerifyMfa(code: String): AuthOutcome {
    val p = pendingMfa ?: return AuthOutcome.Failure(AuthError.CODE_EXPIRED)
    return try {
      val ch = Backend.client.mfaChallenge(p.access, p.factorId)
      if (!ch.isSuccessful) return AuthOutcome.Failure(AuthError.NETWORK)
      val v = Backend.client.mfaVerify(p.access, p.factorId, safeJson(ch).getString("id"), code)
      if (!v.isSuccessful) return AuthOutcome.Failure(AuthError.CODE_WRONG)
      val j = safeJson(v)
      startLiveSession(j.optString("access_token").ifBlank { p.access }, j.text("refresh_token") ?: p.refresh, p.profile)
    } catch (e: Exception) {
      AuthOutcome.Failure(AuthError.NETWORK)
    }
  }

  private fun startLiveSession(access: String, refresh: String?, profile: UserProfile): AuthOutcome {
    liveAccess = access
    liveRefresh = refresh
    pendingMfa = null
    putAccount(Account(profile = profile, salt = "live", passwordHash = "", emailVerified = true))
    _session.value = Session(userId = profile.id, deviceId = thisDeviceId)
    return AuthOutcome.Success(profile.id)
  }

  /** Re-reads the signed-in person's profile (for example to see that the registry approved them). */
  suspend fun liveRefreshProfile(): Boolean {
    val id = _session.value?.userId ?: return false
    var access = liveAccess ?: return false
    var r = runCatching { Backend.client.getProfile(access, id) }.getOrNull() ?: return false
    val refresh = liveRefresh
    if (r.code == 401 && refresh != null) {
      val rr = runCatching { Backend.client.refresh(refresh) }.getOrNull()
      if (rr == null || !rr.isSuccessful) return false
      val j = safeJson(rr)
      access = j.optString("access_token").ifBlank { return false }
      liveAccess = access
      liveRefresh = j.text("refresh_token") ?: refresh
      r = runCatching { Backend.client.getProfile(access, id) }.getOrNull() ?: return false
    }
    if (!r.isSuccessful) return false
    val row = runCatching { r.jsonArray().optJSONObject(0) }.getOrNull() ?: return false
    putAccount(Account(profile = profileFrom(row), salt = "live", passwordHash = "", emailVerified = true))
    return true
  }

  /** Tells the server this session is over (best effort) and forgets the tokens. */
  suspend fun liveSignOutRemote() {
    val token = liveAccess
    liveAccess = null
    liveRefresh = null
    pendingMfa = null
    if (token != null) runCatching { Backend.client.signOut(token) }
  }

  // ---------- Sessions ----------

  fun signOut() {
    val s = _session.value
    if (s != null) removeDevice(s.userId, thisDeviceId)
    _session.value = null
  }

  fun signOutDevice(userId: String, deviceId: String) {
    if (deviceId == thisDeviceId) {
      signOut()
    } else {
      removeDevice(userId, deviceId)
    }
  }

  fun signOutEverywhere(userId: String) {
    _devices.value = _devices.value - userId
    if (_session.value?.userId == userId) _session.value = null
  }

  // ---------- Registry actions (permission is checked by the caller) ----------

  fun approve(userId: String, role: Role, programme: String?, teaches: Set<String>): UserProfile? {
    val account = _accounts.value[userId] ?: return null
    val old = account.profile
    val updated = when (role) {
      Role.STUDENT -> {
        val programmes = catalog.catalogFor(old.universityId)
        val prog = programme ?: old.programme ?: programmes.keys.firstOrNull()
        old.copy(
          role = Role.STUDENT,
          status = AccountStatus.APPROVED,
          programme = prog,
          cohort = if (old.cohort.isNullOrBlank() || old.cohort == "Awaiting registry") "Year 1 • Semester 1" else old.cohort,
          enrolledCourses = programmes[prog].orEmpty()
        )
      }
      Role.LECTURER -> old.copy(
        role = Role.LECTURER,
        status = AccountStatus.APPROVED,
        cohort = if (old.cohort.isNullOrBlank() || old.cohort == "Staff (awaiting role)") "Lecturer" else old.cohort,
        teachesCourses = teaches,
        enrolledCourses = emptySet()
      )
      // Administrator accounts are never created from this queue.
      Role.ADMIN -> return null
    }
    putAccount(account.copy(profile = updated))
    return updated
  }

  /** Replaces the courses a lecturer teaches. The caller has already checked who may do this. */
  fun setTeaching(userId: String, courses: Set<String>): UserProfile? {
    val account = _accounts.value[userId] ?: return null
    if (account.profile.role != Role.LECTURER) return null
    val updated = account.profile.copy(teachesCourses = courses)
    putAccount(account.copy(profile = updated))
    return updated
  }

  /** The app manager creates a campus together with its first administrator. */
  fun createApprovedAdmin(
    universityId: String,
    fullName: String,
    email: String,
    staffNumber: String,
    password: String
  ): AuthError? {
    val cleanEmail = normalizeEmail(email)
    val number = staffNumber.trim()
    if (fullName.trim().length < 3) return AuthError.NAME_REQUIRED
    if (number.length < 2) return AuthError.NUMBER_INVALID
    if (!EMAIL_REGEX.matches(cleanEmail)) return AuthError.EMAIL_INVALID
    if (_accounts.value.values.any { it.profile.email.equals(cleanEmail, ignoreCase = true) }) return AuthError.EMAIL_TAKEN
    if (!checkPassword(password, cleanEmail).acceptable) return AuthError.PASSWORD_WEAK
    val profile = UserProfile(
      id = "usr-" + UUID.randomUUID().toString().take(8),
      fullName = fullName.trim(),
      email = cleanEmail,
      role = Role.ADMIN,
      status = AccountStatus.APPROVED,
      universityId = universityId,
      staffNumber = number,
      programme = "Registry",
      cohort = "Campus administrator",
      requestedRole = Role.ADMIN
    )
    val salt = UUID.randomUUID().toString()
    putAccount(Account(profile, salt, hash(password, salt), emailVerified = true))
    return null
  }

  /**
   * An administrator (or the app manager) adds a person directly. The account is approved at once and
   * the person signs in with the email and starting password they were given.
   */
  fun createApprovedUser(
    universityId: String,
    role: Role,
    fullName: String,
    email: String,
    idNumber: String,
    programme: String?,
    password: String
  ): AuthError? {
    val cleanEmail = normalizeEmail(email)
    val number = idNumber.trim()
    if (fullName.trim().length < 3) return AuthError.NAME_REQUIRED
    if (number.length < 2) return AuthError.NUMBER_INVALID
    if (!EMAIL_REGEX.matches(cleanEmail)) return AuthError.EMAIL_INVALID
    if (_accounts.value.values.any { it.profile.email.equals(cleanEmail, ignoreCase = true) }) return AuthError.EMAIL_TAKEN
    if (role == Role.STUDENT && programme.isNullOrBlank()) return AuthError.PROGRAMME_REQUIRED
    if (!checkPassword(password, cleanEmail).acceptable) return AuthError.PASSWORD_WEAK
    val profile = UserProfile(
      id = "usr-" + UUID.randomUUID().toString().take(8),
      fullName = fullName.trim(),
      email = cleanEmail,
      role = role,
      status = AccountStatus.APPROVED,
      universityId = universityId,
      studentNumber = if (role == Role.STUDENT) number else null,
      staffNumber = if (role != Role.STUDENT) number else null,
      programme = programme?.trim()?.ifBlank { null } ?: if (role == Role.ADMIN) "Registry" else null,
      cohort = when (role) {
        Role.ADMIN -> "Campus administrator"
        Role.LECTURER -> "Lecturer"
        Role.STUDENT -> null
      },
      requestedRole = role,
      enrolledCourses = if (role == Role.STUDENT) catalog.catalogFor(universityId)[programme?.trim().orEmpty()].orEmpty() else emptySet()
    )
    val salt = UUID.randomUUID().toString()
    putAccount(Account(profile, salt, hash(password, salt), emailVerified = true))
    return null
  }

  /** A new course in a programme reaches the students already in that programme. */
  fun enrollProgramme(universityId: String, programme: String, courseCode: String) {
    _accounts.value = _accounts.value.mapValues { (_, account) ->
      val p = account.profile
      if (p.universityId == universityId && p.role == Role.STUDENT && p.status == AccountStatus.APPROVED &&
        p.programme.equals(programme, ignoreCase = true)
      ) {
        account.copy(profile = p.copy(enrolledCourses = p.enrolledCourses + courseCode))
      } else account
    }
  }

  /** Removes the people of one campus (the app manager's delete). Returns how many were removed. */
  fun removeAccountsOf(universityId: String, includeAdmins: Boolean): Int {
    val doomed = _accounts.value.values
      .filter { it.profile.universityId == universityId && (includeAdmins || it.profile.role != Role.ADMIN) }
      .map { it.profile.id }
      .toSet()
    _accounts.value = _accounts.value - doomed
    _devices.value = _devices.value - doomed
    if (_session.value?.userId in doomed) _session.value = null
    return doomed.size
  }

  fun reject(userId: String): UserProfile? {
    val account = _accounts.value[userId] ?: return null
    val updated = account.profile.copy(status = AccountStatus.REJECTED)
    putAccount(account.copy(profile = updated))
    removeAllDevices(userId)
    return updated
  }

  // ---------- Internals ----------

  private fun removeDevice(userId: String, deviceId: String) {
    val list = _devices.value[userId].orEmpty().filterNot { it.id == deviceId }
    _devices.value = if (list.isEmpty()) _devices.value - userId else _devices.value + (userId to list)
  }

  private fun removeAllDevices(userId: String) {
    _devices.value = _devices.value - userId
    if (_session.value?.userId == userId) _session.value = null
  }

  private fun putAccount(account: Account) {
    _accounts.value = _accounts.value + (account.profile.id to account)
  }

  private fun normalizeEmail(raw: String) = raw.trim().lowercase()

  private fun hash(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }

  private companion object {
    // Any address that looks like an address. No domain is required.
    val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
  }
}
