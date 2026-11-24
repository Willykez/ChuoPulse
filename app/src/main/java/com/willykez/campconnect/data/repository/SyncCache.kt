package com.willykez.campconnect.data.repository

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

class SyncCache(context: Context, owner: String) {
  private val dir = File(context.filesDir, "sync/$owner").apply { mkdirs() }

  private fun file(key: String): File {
    val name = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    return File(dir, name)
  }

  fun put(key: String, rows: JSONArray) {
    runCatching {
      val tmp = File(dir, "tmp")
      tmp.writeText(rows.toString())
      if (!tmp.renameTo(file(key))) { file(key).writeText(rows.toString()); tmp.delete() }
    }
  }

  fun get(key: String): JSONArray? =
    runCatching { file(key).takeIf { it.exists() }?.readText()?.let { JSONArray(it) } }.getOrNull()

  fun markSynced(at: Long) {
    runCatching { File(dir, "synced_at").writeText(at.toString()) }
  }

  fun syncedAt(): Long = runCatching { File(dir, "synced_at").readText().trim().toLong() }.getOrDefault(0L)

  companion object {
    const val DIRECTORY = "_directory"

    fun clearPersonal(context: Context) {
      File(context.filesDir, "sync").listFiles()?.forEach { if (it.name != DIRECTORY) it.deleteRecursively() }
    }
  }
}
