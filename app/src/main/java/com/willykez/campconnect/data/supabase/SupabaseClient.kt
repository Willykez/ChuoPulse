package com.willykez.campconnect.data.supabase

import com.willykez.campconnect.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Small Supabase Auth + PostgREST + Storage transport built on the app's existing OkHttp dependency.
 * The anon/publishable key is intentionally the only key shipped in the APK; RLS is the security
 * boundary. Never add a service_role key to Android, Gradle, or a committed env file.
 */
class SupabaseClient(
  private val projectUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
  private val publishableKey: String = BuildConfig.SUPABASE_ANON_KEY,
  private val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS)
    .build()
) {
  private val jsonType = "application/json; charset=utf-8".toMediaType()
  val isConfigured: Boolean get() = projectUrl.startsWith("https://") && !projectUrl.contains("YOUR_PROJECT_REF", ignoreCase = true) && publishableKey.isNotBlank() && !publishableKey.contains("YOUR_SUPABASE", ignoreCase = true)

  data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val email: String?
  )

  data class Result(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
    fun jsonObject(): JSONObject = if (body.isBlank()) JSONObject() else JSONObject(body)
    fun jsonArray(): JSONArray = if (body.isBlank()) JSONArray() else JSONArray(body)
  }

  suspend fun signUp(email: String, password: String, metadata: JSONObject): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/signup?redirect_to=campusconnect://login-callback",
    body = JSONObject().put("email", email.trim()).put("password", password).put("data", metadata),
    bearer = null
  )

  suspend fun signIn(email: String, password: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/token?grant_type=password",
    body = JSONObject().put("email", email.trim()).put("password", password), bearer = null
  )

  /** Confirms a six-digit code from an email. [type] is "signup" or "recovery". Returns a session on success. */
  suspend fun verifyOtp(email: String, token: String, type: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/verify",
    body = JSONObject().put("email", email.trim()).put("token", token.trim()).put("type", type), bearer = null
  )

  /** Sends the sign-up confirmation code again. */
  suspend fun resendSignupCode(email: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/resend",
    body = JSONObject().put("type", "signup").put("email", email.trim()), bearer = null
  )

  /** Emails a password-reset code. Always answers the same way, whether or not the email has an account. */
  suspend fun recover(email: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/recover",
    body = JSONObject().put("email", email.trim()), bearer = null
  )

  suspend fun updatePassword(accessToken: String, newPassword: String): Result = request(
    method = "PUT", url = "$projectUrl/auth/v1/user",
    body = JSONObject().put("password", newPassword), bearer = accessToken
  )

  suspend fun getUser(accessToken: String): Result = request(
    method = "GET", url = "$projectUrl/auth/v1/user", bearer = accessToken
  )

  // ---- Second step (TOTP authenticator app). Administrators need it for admin actions. ----

  suspend fun mfaEnroll(accessToken: String, friendlyName: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/factors",
    body = JSONObject().put("factor_type", "totp").put("friendly_name", friendlyName).put("issuer", "ChuoPulse"),
    bearer = accessToken
  )

  suspend fun mfaChallenge(accessToken: String, factorId: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/factors/${encode(factorId)}/challenge", bearer = accessToken
  )

  /** On success the answer is a new session whose token is marked aal2. */
  suspend fun mfaVerify(accessToken: String, factorId: String, challengeId: String, code: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/factors/${encode(factorId)}/verify",
    body = JSONObject().put("challenge_id", challengeId).put("code", code.trim()), bearer = accessToken
  )

  suspend fun mfaUnenroll(accessToken: String, factorId: String): Result = request(
    method = "DELETE", url = "$projectUrl/auth/v1/factors/${encode(factorId)}", bearer = accessToken
  )

  suspend fun refresh(refreshToken: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/token?grant_type=refresh_token",
    body = JSONObject().put("refresh_token", refreshToken), bearer = null
  )

  suspend fun signOut(accessToken: String): Result = request(
    method = "POST", url = "$projectUrl/auth/v1/logout", bearer = accessToken
  )

  suspend fun getProfile(accessToken: String, userId: String): Result = request(
    method = "GET", url = "$projectUrl/rest/v1/profiles?id=eq.${encode(userId)}&select=*",
    bearer = accessToken
  )

  /** Query public tables. Supabase RLS policies determine which rows the signed-in user can read. */
  suspend fun select(table: String, query: String, accessToken: String): Result = request(
    method = "GET", url = "$projectUrl/rest/v1/${safeIdentifier(table)}?$query", bearer = accessToken
  )

  /** Insert/update rows. Keep user authorization in RLS; never trust UI role checks alone. */
  suspend fun upsert(table: String, rows: JSONArray, accessToken: String): Result = request(
    method = "POST", url = "$projectUrl/rest/v1/${safeIdentifier(table)}?on_conflict=id",
    body = rows, bearer = accessToken, prefer = "resolution=merge-duplicates,return=representation"
  )

  suspend fun delete(table: String, filter: String, accessToken: String): Result = request(
    method = "DELETE", url = "$projectUrl/rest/v1/${safeIdentifier(table)}?$filter",
    bearer = accessToken, prefer = "return=representation"
  )

  /** Upload bytes to a private bucket. The path should be generated by the app, never user input. */
  suspend fun upload(bucket: String, path: String, bytes: ByteArray, contentType: String, accessToken: String): Result {
    require(safeStorageSegment(bucket) && safeStoragePath(path)) { "Invalid storage path" }
    return requestRaw(
      method = "POST", url = "$projectUrl/storage/v1/object/$bucket/$path",
      bytes = bytes, contentType = contentType, bearer = accessToken,
      extraHeaders = mapOf("x-upsert" to "false")
    )
  }

  suspend fun removeStorageObjects(bucket: String, paths: List<String>, accessToken: String): Result {
    require(safeStorageSegment(bucket) && paths.all(::safeStoragePath)) { "Invalid storage path" }
    return request("DELETE", "$projectUrl/storage/v1/object/$bucket", body = JSONObject().put("prefixes", JSONArray(paths)), bearer = accessToken)
  }

  /** Inserts one row and returns it (`return=representation`). Row-level security decides if it is allowed. */
  suspend fun insert(table: String, row: JSONObject, accessToken: String): Result = request(
    method = "POST", url = "$projectUrl/rest/v1/${safeIdentifier(table)}",
    body = row, bearer = accessToken, prefer = "return=representation"
  )

  /** Changes the rows matching [filter] (for example `id=eq.<uuid>`). */
  suspend fun patch(table: String, filter: String, changes: JSONObject, accessToken: String): Result = request(
    method = "PATCH", url = "$projectUrl/rest/v1/${safeIdentifier(table)}?$filter",
    body = changes, bearer = accessToken, prefer = "return=representation"
  )

  /** Downloads a private file. Returns null when it cannot be read. */
  suspend fun download(bucket: String, path: String, accessToken: String): ByteArray? = withContext(Dispatchers.IO) {
    if (!(safeStorageSegment(bucket) && safeStoragePath(path))) return@withContext null
    val req = Request.Builder().url("$projectUrl/storage/v1/object/authenticated/$bucket/$path")
      .header("apikey", publishableKey).header("Authorization", "Bearer $accessToken").get().build()
    try {
      http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    } catch (e: Exception) { null }
  }

  /** Calls a database function (RPC). The function itself checks the caller's role; RLS and grants still apply. */
  suspend fun rpc(function: String, args: JSONObject, accessToken: String): Result {
    require(function.matches(Regex("[a-z_][a-z0-9_]{0,62}"))) { "Invalid function name" }
    return request("POST", "$projectUrl/rest/v1/rpc/$function", body = args, bearer = accessToken)
  }

  /** Calls a deployed Edge Function with the user's JWT. The function must independently authorize. */
  suspend fun invokeFunction(name: String, payload: JSONObject, accessToken: String): Result {
    require(name.matches(Regex("[a-z0-9-]{1,64}"))) { "Invalid function name" }
    return request("POST", "$projectUrl/functions/v1/$name", body = payload, bearer = accessToken)
  }

  /** Calls an Edge Function that does not need a signed-in person (deployed with --no-verify-jwt). */
  suspend fun invokePublic(name: String, payload: JSONObject): Result {
    require(name.matches(Regex("[a-z0-9-]{1,64}"))) { "Invalid function name" }
    return request("POST", "$projectUrl/functions/v1/$name", body = payload, bearer = null)
  }

  suspend fun request(
    method: String,
    url: String,
    body: Any? = null,
    bearer: String?,
    prefer: String? = null
  ): Result = withContext(Dispatchers.IO) {
    check(isConfigured) { "Supabase is not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY." }
    val builder = Request.Builder().url(url).header("apikey", publishableKey).header("Accept", "application/json")
    if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
    if (prefer != null) builder.header("Prefer", prefer)
    val requestBody = when (body) {
      null -> if (method.uppercase() in setOf("POST", "PUT", "PATCH")) "".toRequestBody(jsonType) else null
      is JSONObject -> body.toString().toRequestBody(jsonType)
      is JSONArray -> body.toString().toRequestBody(jsonType)
      else -> body.toString().toRequestBody(jsonType)
    }
    builder.method(method, requestBody)
    execute(builder.build())
  }

  private suspend fun requestRaw(
    method: String, url: String, bytes: ByteArray, contentType: String,
    bearer: String?, extraHeaders: Map<String, String> = emptyMap()
  ): Result = withContext(Dispatchers.IO) {
    check(isConfigured) { "Supabase is not configured. Set SUPABASE_URL and SUPABASE_ANON_KEY." }
    val builder = Request.Builder().url(url).header("apikey", publishableKey)
      .header("Content-Type", contentType).header("Accept", "application/json")
    if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
    extraHeaders.forEach { (k, v) -> builder.header(k, v) }
    builder.method(method, bytes.toRequestBody(contentType.toMediaType()))
    execute(builder.build())
  }

  private fun execute(request: Request): Result = http.newCall(request).execute().use { response ->
    Result(response.code, response.body?.string().orEmpty())
  }

  private fun safeIdentifier(value: String): String {
    require(value.matches(Regex("[a-z_][a-z0-9_]{0,62}"))) { "Invalid table name" }
    return value
  }

  private fun safeStorageSegment(value: String) = value.matches(Regex("[a-z0-9][a-z0-9._-]{0,99}"))
  private fun safeStoragePath(value: String): Boolean = value.isNotBlank() && !value.startsWith('/') &&
    value.split('/').all { it.isNotBlank() && it != "." && it != ".." && it.matches(Regex("[A-Za-z0-9._@+:-]{1,180}")) }

  private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
