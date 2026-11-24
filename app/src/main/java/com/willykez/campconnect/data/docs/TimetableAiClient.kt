package com.willykez.campconnect.data.docs

import com.willykez.campconnect.data.supabase.Backend
import org.json.JSONObject

object TimetableAiClient {

  sealed interface Outcome {
    data class Success(val proposal: JSONObject) : Outcome
    data class Unavailable(val reason: String) : Outcome
  }

  suspend fun propose(text: String, fileName: String, context: JSONObject): Outcome {
    if (!Backend.live) return Outcome.Unavailable("not_configured")
    var token = Backend.tokenSource?.invoke() ?: return Outcome.Unavailable("signed_out")
    val payload = JSONObject().put("text", text.take(TimetableText.MAX_CHARS)).put("file_name", fileName).put("context", context)
    return try {
      var r = Backend.client.invokeFunction("ai-timetable", payload, token)
      if (r.code == 401) {
        token = Backend.tokenRefresh?.invoke() ?: return Outcome.Unavailable("signed_out")
        r = Backend.client.invokeFunction("ai-timetable", payload, token)
      }
      if (!r.isSuccessful) {
        val code = runCatching { r.jsonObject().optString("error") }.getOrDefault("")
        return Outcome.Unavailable(code.ifBlank { "failed" })
      }
      val proposal = r.jsonObject().optJSONObject("proposal")
      if (proposal == null) Outcome.Unavailable("empty") else Outcome.Success(proposal)
    } catch (e: Exception) {
      Outcome.Unavailable("network")
    }
  }

  fun explain(reason: String): String = when (reason) {
    "not_authorized" -> "Only campus administrators can use the timetable assistant."
    "daily_limit" -> "You have used today's AI allowance. Try again tomorrow."
    "ai_not_configured", "not_configured" -> "AI is not set up yet. Ask the app manager to add the AI key."
    "ai_disabled" -> "AI has been switched off by the app manager."
    "text_too_short" -> "That file has too little text to read."
    "text_too_large" -> "That file is too large. Split it into smaller files."
    "campus_paused" -> "Your campus is paused at the moment."
    "ai_busy" -> "The AI service is busy. Try again in a minute."
    "ai_timeout" -> "The AI took too long to answer. Try a smaller file."
    "ai_blocked" -> "The AI could not read that file."
    "ai_key_rejected" -> "The AI key was rejected. Ask the app manager to check it."
    "network", "signed_out" -> "No connection. Connect to the internet and try again."
    else -> "The AI could not turn that file into a timetable. Try again or add the items by hand."
  }
}
