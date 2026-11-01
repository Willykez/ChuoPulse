package com.willykez.campconnect.data.docs

import com.willykez.campconnect.data.supabase.Backend
import org.json.JSONObject

/** Real AI analysis through the `ai-analyze` Edge Function. The key never reaches the phone. */
object AiAnalysisClient {

  data class Result(
    val summary: String,
    val topics: List<String>,
    val objectives: List<String>,
    val tips: List<String>,
    val provider: String,
    val model: String
  )

  sealed interface Outcome {
    data class Success(val result: Result) : Outcome
    /** AI could not be used; [reason] is a short code. The on-device analysis is used instead. */
    data class Unavailable(val reason: String) : Outcome
  }

  suspend fun analyse(text: String, fileName: String, course: String): Outcome {
    if (!Backend.live) return Outcome.Unavailable("not_configured")
    var token = Backend.tokenSource?.invoke() ?: return Outcome.Unavailable("signed_out")
    val payload = JSONObject().put("text", text.take(60_000)).put("file_name", fileName).put("course", course)
    return try {
      var r = Backend.client.invokeFunction("ai-analyze", payload, token)
      if (r.code == 401) {
        token = Backend.tokenRefresh?.invoke() ?: return Outcome.Unavailable("signed_out")
        r = Backend.client.invokeFunction("ai-analyze", payload, token)
      }
      if (!r.isSuccessful) {
        val code = runCatching { r.jsonObject().optString("error") }.getOrDefault("")
        return Outcome.Unavailable(code.ifBlank { "failed" })
      }
      val o = r.jsonObject()
      fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it).trim() }.filter { it.isNotBlank() } }.orEmpty()
      val summary = o.optString("summary").trim()
      if (summary.isBlank()) Outcome.Unavailable("empty")
      else Outcome.Success(Result(summary, list("key_topics"), list("objectives"), list("study_tips"), o.optString("provider"), o.optString("model")))
    } catch (e: Exception) {
      Outcome.Unavailable("network")
    }
  }
}
