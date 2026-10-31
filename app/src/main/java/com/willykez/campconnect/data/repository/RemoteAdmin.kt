package com.willykez.campconnect.data.repository

import com.willykez.campconnect.data.supabase.Backend
import com.willykez.campconnect.data.supabase.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject

enum class RemoteError {
  NOT_ALLOWED, CAMPUS_EXISTS, BAD_DETAILS, EMAIL_TAKEN, NUMBER_TAKEN, WEAK_PASSWORD,
  INVALID_NAME, INVALID_EMAIL, INVALID_NUMBER, UNKNOWN_CAMPUS, NOT_PENDING, EMAIL_NOT_VERIFIED, NETWORK, SERVER
}

data class RemoteResult(
  val ok: Boolean,
  val error: RemoteError? = null,
  val detail: String = "",
  val body: String = ""
)

/** What `platform_campus_summary()` returns for one campus. */
data class RemoteCampusSummary(
  val id: String, val code: String, val name: String,
  val people: Int, val pending: Int, val admins: Int, val courses: Int,
  val sessions: Int, val announcements: Int, val materials: Int,
  val programmes: Int, val rooms: Int, val works: Int
)

/**
 * Everything the registry and the app manager do on the SERVER: create campuses and accounts, approve
 * or reject people, read campus totals. The database and the `admin-create-user` Edge Function decide
 * who may do each of these; this class only sends the request and turns the answer into a result.
 * [token] gives the signed-in person's access token; [refresh] gets a new one when it has expired.
 */
class RemoteAdmin(
  private val token: () -> String?,
  private val refresh: suspend () -> String?
) {
  private val client get() = Backend.client

  private suspend fun authed(call: suspend (String) -> SupabaseClient.Result): SupabaseClient.Result? {
    val first = token() ?: return null
    var r = try { call(first) } catch (e: Exception) { return null }
    if (r.code == 401) {
      val next = refresh() ?: return r
      r = try { call(next) } catch (e: Exception) { return null }
    }
    return r
  }

  private fun errorOf(r: SupabaseClient.Result?): RemoteResult {
    if (r == null) return RemoteResult(false, RemoteError.NETWORK)
    val o = try { r.jsonObject() } catch (e: Exception) { JSONObject() }
    val text = listOf(o.optString("message"), o.optString("error"), o.optString("msg"), o.optString("detail"), r.body)
      .joinToString(" ").lowercase()
    val kind = when {
      r.code == 403 || r.code == 401 || text.contains("not_allowed") || text.contains("not_authorized") || o.optString("code") == "42501" -> RemoteError.NOT_ALLOWED
      text.contains("campus_exists") -> RemoteError.CAMPUS_EXISTS
      text.contains("bad_details") -> RemoteError.BAD_DETAILS
      text.contains("email_taken") -> RemoteError.EMAIL_TAKEN
      text.contains("number_taken") -> RemoteError.NUMBER_TAKEN
      text.contains("weak_password") -> RemoteError.WEAK_PASSWORD
      text.contains("invalid_name") || text.contains("name_required") -> RemoteError.INVALID_NAME
      text.contains("invalid_email") -> RemoteError.INVALID_EMAIL
      text.contains("invalid_number") || text.contains("number_required") -> RemoteError.INVALID_NUMBER
      text.contains("unknown_campus") || text.contains("unknown campus") -> RemoteError.UNKNOWN_CAMPUS
      text.contains("email_not_verified") -> RemoteError.EMAIL_NOT_VERIFIED
      text.contains("not_pending") -> RemoteError.NOT_PENDING
      r.code >= 500 || r.code == 0 -> RemoteError.SERVER
      else -> RemoteError.SERVER
    }
    return RemoteResult(false, kind, o.optString("message").ifBlank { o.optString("detail") }, r.body)
  }

  private fun result(r: SupabaseClient.Result?): RemoteResult =
    if (r != null && r.isSuccessful) RemoteResult(true, body = r.body) else errorOf(r)

  // ---------- Generic table access (row-level security on the server decides what is allowed) ----------

  /** Reads rows. Null when the request failed. */
  suspend fun query(table: String, qs: String): JSONArray? {
    val r = authed { client.select(table, qs, it) } ?: return null
    if (!r.isSuccessful) return null
    return try { r.jsonArray() } catch (e: Exception) { null }
  }

  suspend fun insertRow(table: String, row: JSONObject): RemoteResult = result(authed { client.insert(table, row, it) })

  suspend fun patchRows(table: String, filter: String, changes: JSONObject): RemoteResult =
    result(authed { client.patch(table, filter, changes, it) })

  suspend fun deleteRows(table: String, filter: String): RemoteResult = result(authed { client.delete(table, filter, it) })

  suspend fun rpcCall(function: String, args: JSONObject): RemoteResult = result(authed { client.rpc(function, args, it) })

  /** Uploads a private file. Returns ok when stored. */
  suspend fun uploadFile(bucket: String, path: String, bytes: ByteArray, mime: String): RemoteResult =
    result(authed { client.upload(bucket, path, bytes, mime, it) })

  suspend fun downloadFile(bucket: String, path: String): ByteArray? {
    val t = token() ?: return null
    return try { client.download(bucket, path, t) ?: refresh()?.let { client.download(bucket, path, it) } } catch (e: Exception) { null }
  }

  /** Creates a campus. The result's body is the new campus id (a JSON string). */
  suspend fun createUniversity(code: String, name: String): RemoteResult {
    val r = authed { client.rpc("platform_create_university", JSONObject().put("p_code", code).put("p_name", name), it) }
    val res = result(r)
    return if (res.ok) res.copy(detail = res.body.trim().trim('"')) else res
  }

  suspend fun deleteUniversity(universityUuid: String): RemoteResult =
    result(authed { client.rpc("platform_delete_university", JSONObject().put("p_university", universityUuid), it) })

  /** Removes a campus's data; the Edge Function also deletes the stored files. */
  suspend fun wipeCampus(universityUuid: String, scope: String): RemoteResult =
    result(authed { client.invokeFunction("campus-wipe-files", JSONObject().put("university_id", universityUuid).put("scope", scope), it) })

  /** Creates a confirmed sign-in account with a password and its approved profile. */
  suspend fun provisionUser(
    universityCode: String, role: String, fullName: String, email: String,
    idNumber: String, programme: String?, password: String
  ): RemoteResult {
    val payload = JSONObject()
      .put("university_code", universityCode).put("role", role).put("full_name", fullName)
      .put("email", email).put("id_number", idNumber).put("programme", programme.orEmpty())
      .put("password", password)
    return result(authed { client.invokeFunction("admin-create-user", payload, it) })
  }

  /** Approves a waiting account (checked by the database: administrator of that campus only). */
  suspend fun approve(userId: String, role: String, programmeUuid: String?, courses: List<String>): RemoteResult {
    val args = JSONObject().put("p_user", userId).put("p_role", role)
      .put("p_programme", programmeUuid ?: JSONObject.NULL)
      .put("p_courses", JSONArray(courses))
    return result(authed { client.rpc("approve_account", args, it) })
  }

  suspend fun reject(userId: String): RemoteResult =
    result(authed { client.rpc("reject_account", JSONObject().put("p_user", userId), it) })

  suspend fun suspendAccount(userId: String, reason: String): RemoteResult =
    result(authed { client.rpc("suspend_account", JSONObject().put("p_user", userId).put("p_reason", reason), it) })

  suspend fun setMaintenance(universityUuid: String, on: Boolean, note: String): RemoteResult =
    result(authed {
      client.rpc("platform_set_maintenance",
        JSONObject().put("p_university", universityUuid).put("p_on", on).put("p_note", note), it)
    })

  /** The app manager may rename the campus; a campus administrator's name is ignored by the database. */
  suspend fun updateDetails(universityUuid: String, name: String?, location: String, email: String, phone: String): RemoteResult =
    result(authed {
      client.rpc("update_campus_details",
        JSONObject().put("p_university", universityUuid).put("p_name", name.orEmpty())
          .put("p_location", location).put("p_email", email).put("p_phone", phone), it)
    })

  /** Makes someone who already signed up (and verified their email) the administrator of a campus. */
  suspend fun makeCampusAdmin(universityUuid: String, email: String, staffNumber: String): RemoteResult =
    result(authed {
      client.rpc("platform_make_campus_admin",
        JSONObject().put("p_university", universityUuid).put("p_email", email).put("p_staff_number", staffNumber), it)
    })

  suspend fun campusSummaries(): List<RemoteCampusSummary>? {
    val r = authed { client.rpc("platform_campus_summary", JSONObject(), it) } ?: return null
    if (!r.isSuccessful) return null
    return try {
      val a = r.jsonArray()
      (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        RemoteCampusSummary(
          o.getString("university_id"), o.getString("code"), o.getString("name"),
          o.optInt("people"), o.optInt("pending"), o.optInt("admins"), o.optInt("courses"),
          o.optInt("sessions"), o.optInt("announcements"), o.optInt("materials"),
          o.optInt("programmes"), o.optInt("rooms"), o.optInt("works")
        )
      }
    } catch (e: Exception) { null }
  }
}
