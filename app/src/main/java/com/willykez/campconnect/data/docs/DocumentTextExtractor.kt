package com.willykez.campconnect.data.docs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The words a file contains, split into pages or slides where the format has them. */
data class ExtractedDocument(
  val sections: List<String>,
  /** Real number of pages or slides in the file. */
  val pageCount: Int,
  /** How many pages were actually read (large scanned files are capped). */
  val pagesRead: Int = sections.size
) {
  val text: String get() = sections.joinToString("\n\n")
}

/** A problem the person can understand and act on, never a stack trace. */
class ExtractionException(message: String) : Exception(message)

/**
 * Reads the text out of files so they can be summarised. Everything happens on the phone.
 *  - Word, PowerPoint, Excel and OpenDocument files are zip archives of XML: read directly.
 *  - PDFs are drawn page by page and read with on-device text recognition, so scanned and
 *    typed PDFs both work.
 *  - Photos and screenshots are read with the same on-device text recognition.
 */
object DocumentTextExtractor {
  const val MAX_OCR_PAGES = 20
  private const val MAX_CHARS = 300_000
  private const val MIN_CHARS = 40
  private const val IMAGE_MAX_SIDE = 2400

  suspend fun extract(file: File, fileName: String, mimeType: String?): ExtractedDocument =
    withContext(Dispatchers.IO) {
      val ext = DocumentKind.extensionOf(fileName)
      val kind = DocumentKind.of(fileName, mimeType)
      val raw = when {
        ext in DocumentKind.legacyOffice -> throw ExtractionException(
          "Old .$ext files can't be read. Save it as .${ext}x or as a PDF and add it again."
        )
        kind == DocumentKind.PDF -> pdf(file)
        kind == DocumentKind.IMAGE -> image(file)
        ext == "docx" -> ExtractedDocument(listOf(zipText(file, "word/document.xml", "w:p")), 1)
        ext == "pptx" -> pptx(file)
        ext == "xlsx" -> xlsx(file)
        ext == "odt" || ext == "ods" -> ExtractedDocument(listOf(zipText(file, "content.xml", "text:p", "text:h")), 1)
        ext == "odp" -> odp(file)
        ext == "rtf" -> single(rtf(readTextFile(file)))
        ext == "html" || ext == "htm" -> single(html(readTextFile(file)))
        kind == DocumentKind.TEXT || ext == "csv" || ext == "tsv" -> single(readTextFile(file))
        else -> throw ExtractionException(
          "This file type can't be analysed. Use a PDF, photo, Word, PowerPoint, Excel or text file."
        )
      }
      val cleaned = raw.sections.map(::tidy).filter { it.isNotBlank() }
      if (cleaned.sumOf { it.length } < MIN_CHARS) {
        throw ExtractionException(
          if (kind == DocumentKind.IMAGE || kind == DocumentKind.PDF)
            "No readable text was found. Use a clear, well-lit picture or a sharper scan of printed text."
          else "This file has too little text to summarise."
        )
      }
      raw.copy(sections = capTotal(cleaned), pagesRead = cleaned.size)
    }

  // ---------- Office / OpenDocument ----------

  private fun single(text: String) = ExtractedDocument(listOf(text), 1)

  private fun zipText(file: File, entry: String, vararg breakTags: String): String =
    openZip(file) { zip ->
      val e = zip.getEntry(entry) ?: throw ExtractionException("This file looks damaged: it has no readable content.")
      xmlText(zip.getInputStream(e).bufferedReader().use { it.readText() }, *breakTags)
    }

  private fun pptx(file: File): ExtractedDocument = openZip(file) { zip ->
    val slides = zip.entries().asSequence()
      .filter { Regex("""ppt/slides/slide\d+\.xml""").matches(it.name) }
      .sortedBy { Regex("""\d+""").find(it.name.substringAfterLast('/'))!!.value.toInt() }
      .toList()
    if (slides.isEmpty()) throw ExtractionException("This presentation has no slides.")
    ExtractedDocument(
      slides.map { xmlText(zip.getInputStream(it).bufferedReader().use { r -> r.readText() }, "a:p") },
      slides.size
    )
  }

  private fun xlsx(file: File): ExtractedDocument = openZip(file) { zip ->
    val shared = zip.getEntry("xl/sharedStrings.xml")
      ?: throw ExtractionException("This spreadsheet has no text cells to analyse.")
    val xml = zip.getInputStream(shared).bufferedReader().use { it.readText() }
    val cells = xml.split("</si>").map { xmlText(it).trim() }.filter { it.isNotBlank() }
    val sheets = zip.entries().asSequence().count { Regex("""xl/worksheets/sheet\d+\.xml""").matches(it.name) }
    ExtractedDocument(listOf(cells.joinToString("\n")), sheets.coerceAtLeast(1))
  }

  private fun odp(file: File): ExtractedDocument = openZip(file) { zip ->
    val e = zip.getEntry("content.xml") ?: throw ExtractionException("This file looks damaged: it has no readable content.")
    val xml = zip.getInputStream(e).bufferedReader().use { it.readText() }
    val slides = xml.split("</draw:page>").map { xmlText(it, "text:p", "text:h") }.filter { it.isNotBlank() }
    ExtractedDocument(slides.ifEmpty { listOf(xmlText(xml, "text:p", "text:h")) }, slides.size.coerceAtLeast(1))
  }

  private fun <T> openZip(file: File, block: (ZipFile) -> T): T = try {
    ZipFile(file).use(block)
  } catch (e: ExtractionException) {
    throw e
  } catch (e: IOException) {
    throw ExtractionException("This file looks damaged or is not a real document.")
  }

  private fun xmlText(xml: String, vararg breakTags: String): String {
    var s = xml.replace("<w:br/>", "\n").replace("<text:line-break/>", "\n")
    breakTags.forEach { s = s.replace("</$it>", "\n") }
    return decode(s.replace(Regex("<[^>]*>"), ""))
  }

  private fun decode(s: String): String = s
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

  private fun rtf(s: String): String = s
    .replace(Regex("""\\'[0-9a-fA-F]{2}"""), " ")
    .replace(Regex("""\{\\\*[^}]*\}"""), "")
    .replace(Regex("""\\par[d]?"""), "\n")
    .replace(Regex("""\\[a-zA-Z]+-?\d* ?"""), "")
    .replace(Regex("[{}]"), "")

  private fun html(s: String): String = decode(
    s.replace(Regex("(?is)<(script|style).*?</\\1>"), " ")
      .replace(Regex("(?i)</(p|div|li|h[1-6]|tr|br)>"), "\n")
      .replace(Regex("<[^>]*>"), " ")
  )

  private fun readTextFile(file: File): String {
    if (file.length() > 8L * 1024 * 1024) throw ExtractionException("This text file is too large (limit 8 MB).")
    return file.readText(Charsets.UTF_8)
  }

  // ---------- PDF and photos (on-device text recognition) ----------

  private suspend fun pdf(file: File): ExtractedDocument {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    var descriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
      descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
      renderer = PdfRenderer(descriptor)
      val total = renderer.pageCount
      val pages = ArrayList<String>()
      for (i in 0 until minOf(total, MAX_OCR_PAGES)) {
        val bitmap = renderPage(renderer, i)
        try {
          pages += recognise(recognizer, bitmap, 0)
        } finally {
          bitmap.recycle()
        }
      }
      return ExtractedDocument(pages, total, pages.size)
    } catch (e: SecurityException) {
      throw ExtractionException("This PDF is password protected. Remove the password and add it again.")
    } catch (e: IOException) {
      throw ExtractionException("This PDF could not be opened. It may be damaged.")
    } finally {
      runCatching { renderer?.close() }
      runCatching { descriptor?.close() }
      recognizer.close()
    }
  }

  private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap {
    val page = renderer.openPage(index)
    try {
      val width = 1600
      val height = ((page.height.toFloat() / page.width) * width).toInt().coerceIn(200, 2800)
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(Color.WHITE) // PDFs draw on a transparent page; text recognition needs white
      page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      return bitmap
    } finally {
      page.close()
    }
  }

  private suspend fun image(file: File): ExtractedDocument {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > IMAGE_MAX_SIDE) sample *= 2
    val bitmap = BitmapFactory.decodeFile(
      file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: throw ExtractionException("This picture could not be opened.")
    val rotation = runCatching {
      when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
      }
    }.getOrDefault(0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
      return ExtractedDocument(listOf(recognise(recognizer, bitmap, rotation)), 1)
    } finally {
      bitmap.recycle()
      recognizer.close()
    }
  }

  private suspend fun recognise(recognizer: TextRecognizer, bitmap: Bitmap, rotation: Int): String =
    recognizer.process(InputImage.fromBitmap(bitmap, rotation)).await().text

  private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
  }

  // ---------- Clean-up ----------

  private fun tidy(s: String): String = s
    .replace("\u00A0", " ")
    .lines()
    .joinToString("\n") { it.replace(Regex("[ \t]+"), " ").trim() }
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

  private fun capTotal(sections: List<String>): List<String> {
    var budget = MAX_CHARS
    val out = ArrayList<String>()
    for (s in sections) {
      if (budget <= 0) break
      out += if (s.length > budget) s.take(budget) else s
      budget -= s.length
    }
    return out
  }
}
