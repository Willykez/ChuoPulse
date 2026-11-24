package com.willykez.campconnect.data.docs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object TimetableSource {

  private val SHEETS = setOf("xlsx", "ods", "csv", "tsv")
  private val WORDS = setOf("docx", "odt")

  fun displayName(context: Context, uri: Uri): String =
    runCatching {
      context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && i >= 0) c.getString(i) else null
      }
    }.getOrNull().orEmpty().ifBlank { "timetable" }

  suspend fun textOf(context: Context, uri: Uri, fileName: String, mimeType: String?): String = withContext(Dispatchers.IO) {
    val ext = DocumentKind.extensionOf(fileName)
    val temp = File.createTempFile("timetable", "." + ext.filter { it.isLetterOrDigit() }.ifBlank { "bin" }, context.cacheDir)
    try {
      val input = context.contentResolver.openInputStream(uri) ?: throw IOException("cannot open")
      input.use { src -> temp.outputStream().use { dst -> src.copyTo(dst) } }
      val structured = runCatching {
        when (ext) {
          in SHEETS -> TimetableText.fromSheets(OfficeReader.readSheets(temp, ext))
          in WORDS -> TimetableText.fromBlocks(OfficeReader.readWord(temp, ext))
          else -> ""
        }
      }.getOrDefault("")
      val text = structured.ifBlank {
        DocumentTextExtractor.extract(temp, fileName, mimeType, minChars = 20).sections.joinToString("\n").take(TimetableText.MAX_CHARS)
      }
      text
    } finally {
      temp.delete()
    }
  }
}
