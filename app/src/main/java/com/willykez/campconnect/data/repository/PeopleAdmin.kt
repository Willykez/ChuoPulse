package com.willykez.campconnect.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A student, lecturer or administrator as an administrator sees them. */
data class ManagedPerson(
  val id: String,
  val name: String,
  val email: String,
  /** "student", "lecturer" or "admin". */
  val role: String,
  /** "approved", "pending", "suspended" or "rejected". */
  val status: String,
  val isClassRep: Boolean,
  val number: String,
  val programmeId: String?,
  val programmeName: String,
  val department: String,
  val createdAt: String
) {
  val paused: Boolean get() = status == "suspended"
  val approved: Boolean get() = status == "approved"
}

/** An administrator of one campus, as the app manager sees them. */
data class ManagedAdmin(
  val id: String,
  val name: String,
  val email: String,
  val status: String,
  val number: String,
  val campusId: String,
  val campusCode: String,
  val campusName: String
) {
  val paused: Boolean get() = status == "suspended"
}

data class ProgrammeRef(val id: String, val name: String)

/** What anyone may know about the AI: on or off, which model. Never the key itself. */
data class AiStatus(
  val enabled: Boolean = true,
  val provider: String = "gemini",
  val model: String = "",
  val hasKey: Boolean = false,
  /** First and last characters of the key, shown to the app manager only. */
  val keyHint: String = ""
)

/** Result of one admin action. [code] is a short reason ("number_taken", "not_allowed", ...). */
data class PeopleResult(val ok: Boolean, val code: String = "")

/**
 * Everything an administrator does to people (create is in CampusRepository.addCampusUser),
 * and the app manager's AI settings. The DATABASE and the `admin-manage-user` function decide who
 * may do what; nothing here is trusted on its own.
 */
class PeopleAdmin(private val remote: RemoteAdmin) {

  private val _people = MutableStateFlow<List<ManagedPerson>>(emptyList())
  val people: StateFlow<List<ManagedPerson>> = _people.asStateFlow()

  private val _programmes = MutableStateFlow<List<ProgrammeRef>>(emptyList())
  val programmes: StateFlow<List<ProgrammeRef>> = _programmes.asStateFlow()

  private val _admins = MutableStateFlow<List<ManagedAdmin>>(emptyList())
  val admins: StateFlow<List<ManagedAdmin>> = _admins.asStateFlow()

  private val _ai = MutableStateFlow(AiStatus())
  val ai: StateFlow<AiStatus> = _ai.asStateFlow()

  private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k)

  private inline fun JSONArray.each(block: (JSONObject) -> Unit) { for (i in 0 until length()) block(getJSONObject(i)) }

  private fun codeOf(r: RemoteResult): String {
    val t = (r.detail + " " + r.body).lowercase()
    return when {
      "number_taken" in t -> "number_taken"
      "email_taken" in t -> "email_taken"
      "weak_password" in t -> "weak_password"
      "invalid_email" in t -> "invalid_email"
      "invalid_name" in t -> "invalid_name"
      "invalid_number" in t -> "invalid_number"
      "programme_required" in t -> "programme_required"
      "unknown_programme" in t -> "unknown_programme"
      "role_not_allowed" in t -> "role_not_allowed"
      r.error == RemoteError.NOT_ALLOWED -> "not_allowed"
      r.error == RemoteError.NETWORK -> "network"
      else -> "failed"
    }
  }

  private fun done(r: RemoteResult) = if (r.ok) PeopleResult(true) else PeopleResult(false, codeOf(r))

  // ---------- Lists ----------

  /** The people of my campus (campus administrator). */
  suspend fun loadPeople(): Boolean {
    val r = remote.rpcCall("admin_list_people", JSONObject())
    if (!r.ok) return false
    val rows = runCatching { JSONArray(r.body) }.getOrNull() ?: return false
    val out = ArrayList<ManagedPerson>()
    rows.each { o ->
      val role = o.s("role")
      out += ManagedPerson(
        id = o.s("id"), name = o.s("full_name"), email = o.s("email"), role = role, status = o.s("status"),
        isClassRep = o.optBoolean("is_class_rep"),
        number = if (role == "student") o.s("student_number") else o.s("staff_number").ifBlank { o.s("student_number") },
        programmeId = o.s("programme_id").ifBlank { null }, programmeName = o.s("programme_name"),
        department = o.s("department"), createdAt = o.s("created_at")
      )
    }
    _people.value = out
    return true
  }

  /** The programmes of my campus, with their ids (needed to move a student or change a role). */
  suspend fun loadProgrammes(universityUuid: String?): Boolean {
    val filter = if (universityUuid != null) "&university_id=eq.$universityUuid" else ""
    val rows = remote.query("programmes", "select=id,name$filter&order=name") ?: return false
    val out = ArrayList<ProgrammeRef>()
    rows.each { out += ProgrammeRef(it.s("id"), it.s("name")) }
    _programmes.value = out
    return true
  }

  /** The administrators of every campus (app manager). */
  suspend fun loadAdmins(): Boolean {
    val r = remote.rpcCall("platform_list_admins", JSONObject())
    if (!r.ok) return false
    val rows = runCatching { JSONArray(r.body) }.getOrNull() ?: return false
    val out = ArrayList<ManagedAdmin>()
    rows.each { o ->
      out += ManagedAdmin(
        id = o.s("id"), name = o.s("full_name"), email = o.s("email"), status = o.s("status"),
        number = o.s("staff_number"), campusId = o.s("university_id"),
        campusCode = o.s("university_code"), campusName = o.s("university_name")
      )
    }
    _admins.value = out
    return true
  }

  fun clear() {
    _people.value = emptyList(); _admins.value = emptyList(); _programmes.value = emptyList()
  }

  // ---------- Actions on one person ----------

  suspend fun updatePerson(id: String, name: String, number: String, programmeId: String?, department: String?): PeopleResult {
    val args = JSONObject().put("p_user", id).put("p_full_name", name.trim()).put("p_number", number.trim())
      .put("p_programme", programmeId ?: JSONObject.NULL).put("p_department", department ?: JSONObject.NULL)
    return done(remote.rpcCall("admin_update_person", args)).also { if (it.ok) loadPeople() }
  }

  /** [role] is "student" or "lecturer". A new student needs a programme. */
  suspend fun changeRole(id: String, role: String, number: String?, programmeId: String?): PeopleResult {
    val args = JSONObject().put("p_user", id).put("p_role", role)
      .put("p_number", number?.trim()?.ifBlank { null } ?: JSONObject.NULL)
      .put("p_programme", programmeId ?: JSONObject.NULL)
    return done(remote.rpcCall("admin_change_role", args)).also { if (it.ok) { loadPeople(); loadAdmins() } }
  }

  suspend fun resetPassword(id: String, password: String): PeopleResult =
    done(remote.callFunction("admin-manage-user", JSONObject().put("action", "reset_password").put("user_id", id).put("password", password)))

  suspend fun updateLogin(id: String, email: String): PeopleResult =
    done(remote.callFunction("admin-manage-user", JSONObject().put("action", "update_login").put("user_id", id).put("email", email.trim().lowercase())))
      .also { if (it.ok) { loadPeople(); loadAdmins() } }

  suspend fun setPaused(id: String, pause: Boolean, reason: String = ""): PeopleResult =
    done(remote.callFunction("admin-manage-user", JSONObject().put("action", if (pause) "pause" else "resume").put("user_id", id).put("reason", reason)))
      .also { if (it.ok) { loadPeople(); loadAdmins() } }

  suspend fun delete(id: String): PeopleResult =
    done(remote.callFunction("admin-manage-user", JSONObject().put("action", "delete").put("user_id", id)))
      .also { if (it.ok) { loadPeople(); loadAdmins() } }

  suspend fun setClassRep(id: String, on: Boolean): PeopleResult =
    done(remote.rpcCall("set_class_rep", JSONObject().put("p_user", id).put("p_on", on))).also { if (it.ok) loadPeople() }

  // ---------- AI settings ----------

  suspend fun loadAiStatus(): Boolean {
    val r = remote.rpcCall("ai_status", JSONObject())
    if (!r.ok) return false
    val o = runCatching { JSONArray(r.body).optJSONObject(0) }.getOrNull() ?: return false
    _ai.value = AiStatus(
      enabled = o.optBoolean("enabled", true), provider = o.s("provider").ifBlank { "gemini" },
      model = o.s("model"), hasKey = o.optBoolean("has_key"), keyHint = o.s("key_hint")
    )
    return true
  }

  /** An empty [apiKey] keeps the key that is already saved. */
  suspend fun saveAi(provider: String, model: String, apiKey: String, enabled: Boolean): PeopleResult {
    val args = JSONObject().put("p_provider", provider).put("p_model", model).put("p_api_key", apiKey.trim()).put("p_enabled", enabled)
    return done(remote.rpcCall("platform_set_ai_settings", args)).also { if (it.ok) loadAiStatus() }
  }

  suspend fun clearAiKey(): PeopleResult =
    done(remote.rpcCall("platform_clear_ai_key", JSONObject())).also { if (it.ok) loadAiStatus() }
}
