package com.willykez.campconnect.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DeadlineTime {
  val zone: TimeZone = TimeZone.getTimeZone("Africa/Dar_es_Salaam")

  private fun shown() = SimpleDateFormat("EEEE, d MMM yyyy '\u2022' HH:mm", Locale.ENGLISH).apply { timeZone = zone }
  private fun core() = SimpleDateFormat("d MMM yyyy '\u2022' HH:mm", Locale.ENGLISH).apply {
    timeZone = zone
    isLenient = false
  }

  fun format(millis: Long): String = shown().format(Date(millis)) + " EAT"

  fun parse(text: String): Long? {
    val body = text.replace("EAT", "").trim().let { t ->
      if (t.contains(", ")) t.substringAfter(", ") else t
    }.trim()
    return runCatching { core().parse(body)?.time }.getOrNull()
  }

  fun tomorrowEvening(now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance(zone).apply {
      timeInMillis = now
      add(Calendar.DAY_OF_YEAR, 1)
      set(Calendar.HOUR_OF_DAY, 23)
      set(Calendar.MINUTE, 59)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  fun urgency(dueText: String, fallback: String, swahili: Boolean = false, now: Long = System.currentTimeMillis()): String {
    val due = parse(dueText) ?: return fallback
    val diff = due - now
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
      diff < 0 -> if (swahili) "Muda umepita" else "Overdue"
      minutes < 60 -> if (swahili) "Dakika ${minutes.coerceAtLeast(1)} zimebaki" else "Due in ${minutes.coerceAtLeast(1)} min"
      hours < 24 -> if (swahili) "Saa $hours zimebaki" else "Due in $hours hours"
      days < 7 -> if (swahili) "Siku ${days.coerceAtLeast(1)} zimebaki" else "Due in ${days.coerceAtLeast(1)} days"
      days < 14 -> if (swahili) "Wiki ijayo" else "Next week"
      else -> if (swahili) "Inakuja" else "Upcoming"
    }
  }

  fun isOverdue(dueText: String, now: Long = System.currentTimeMillis()): Boolean =
    parse(dueText)?.let { it < now } ?: false
}
