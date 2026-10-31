package com.willykez.campconnect.ui.util

import com.willykez.campconnect.data.model.AppLanguage

/** "14:30" -> "14:30" (24-hour) or "2:30 PM" (12-hour). Anything unparsable is returned as is. */
fun formatClock(hhmm: String, is24Hour: Boolean): String {
  if (is24Hour) return hhmm
  val parts = hhmm.trim().split(":")
  val hour = parts.getOrNull(0)?.toIntOrNull() ?: return hhmm
  val minute = parts.getOrNull(1)?.take(2)?.toIntOrNull() ?: return hhmm
  val suffix = if (hour >= 12) "PM" else "AM"
  val h12 = when (val h = hour % 12) {
    0 -> 12
    else -> h
  }
  return "%d:%02d %s".format(h12, minute, suffix)
}

/**
 * The honest freshness line shown at the top of lists.
 * Online and fresh -> "Synced just now". Offline -> "Last synced 2 hours ago, offline".
 */
fun syncStatusText(isOffline: Boolean, lastSyncedAt: Long, now: Long, language: AppLanguage): String {
  val sw = language == AppLanguage.SWAHILI
  if (lastSyncedAt <= 0L) {
    return if (sw) "Haijasasishwa bado" else "Not synced yet"
  }
  val minutes = ((now - lastSyncedAt) / 60_000L).coerceAtLeast(0L)
  val hours = minutes / 60
  val days = hours / 24
  val ago = when {
    minutes < 1 -> null
    minutes < 60 -> if (sw) "dakika $minutes zilizopita" else "$minutes min ago"
    hours < 24 -> if (sw) "saa $hours zilizopita" else "$hours ${if (hours == 1L) "hour" else "hours"} ago"
    else -> if (sw) "siku $days zilizopita" else "$days ${if (days == 1L) "day" else "days"} ago"
  }
  return if (isOffline) {
    if (sw) "Imesasishwa mara ya mwisho ${ago ?: "sasa hivi"}, nje ya mtandao"
    else "Last synced ${ago ?: "just now"}, offline"
  } else {
    if (sw) "Imesasishwa ${ago ?: "sasa hivi"}" else "Synced ${ago ?: "just now"}"
  }
}
