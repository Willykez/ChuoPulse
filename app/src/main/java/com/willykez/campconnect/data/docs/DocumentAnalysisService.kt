package com.willykez.campconnect.data.docs

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.willykez.campconnect.data.local.AppDatabase
import com.willykez.campconnect.data.local.ProcessedDocumentEntity
import com.willykez.campconnect.data.local.ProcessedDocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Takes a file the person chose, keeps a private copy on the phone, analyses it, and saves the result
 * into the local library so it shows up in "AI Summaries" straight away.
 *
 * Work runs in the app's own scope, so leaving the screen does not cancel it. The row appears in the
 * library immediately as PROCESSING and becomes COMPLETED, or FAILED with a reason and a Retry.
 */
class DocumentAnalysisService private constructor(private val appContext: Context) {

  private val repository = ProcessedDocumentRepository(appContext)
  private val dao = AppDatabase.getInstance(appContext).processedDocumentDao()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val libraryDir: File get() = File(appContext.filesDir, "library").also { it.mkdirs() }

  /** What to analyse and where it belongs. [uri] may be a content:// or file:// address. */
  data class Request(
    val uri: Uri,
    val fileName: String,
    val mimeType: String?,
    val courseCode: String,
    val courseTitle: String,
    val university: String,
    val uploaderName: String,
    val category: String = "Notes"
  )

  init {
    // Anything still marked PROCESSING belongs to an earlier run of the app that was closed mid-way.
    scope.launch { dao.markInterrupted() }
  }

  /** Reads the display name and size Android knows for a picked file. */
  fun describe(uri: Uri): Pair<String, String?> {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    runCatching {
      appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0)?.let { name = it }
      }
    }
    val ext = DocumentKind.extensionOf(name)
    val mime = appContext.contentResolver.getType(uri)
      ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    return name to mime
  }

  /** Starts analysing and returns the id of the library entry that will hold the result. */
  fun enqueue(request: Request): String {
    val id = UUID.randomUUID().toString()
    scope.launch {
      val title = request.fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().ifBlank { request.fileName }
      var entity = ProcessedDocumentEntity(
        id = id, title = title, fileName = request.fileName, fileSize = 0L,
        mimeType = request.mimeType ?: "application/octet-stream",
        courseCode = request.courseCode, courseTitle = request.courseTitle, university = request.university,
        uploaderName = request.uploaderName, timestamp = System.currentTimeMillis(),
        extractedSummary = "Reading the document…", keyTopics = "", learningObjectives = "", studyTips = "",
        wordCount = 0, pageCount = 0, readingTimeMinutes = 0, extractionStatus = STATUS_PROCESSING,
        sourceUri = null, category = request.category,
        tags = DocumentKind.of(request.fileName, request.mimeType).label.lowercase()
      )
      repository.save(entity)
      try {
        val copy = copyToLibrary(request.uri, id, request.fileName)
        entity = entity.copy(fileSize = copy.length(), sourceUri = Uri.fromFile(copy).toString())
        repository.save(entity)
        repository.save(analyse(entity, copy))
      } catch (e: ExtractionException) {
        repository.save(entity.copy(extractionStatus = STATUS_FAILED, extractedSummary = e.message ?: "Analysis failed."))
      } catch (e: IOException) {
        repository.save(entity.copy(extractionStatus = STATUS_FAILED, extractedSummary = "The file could not be read. Check that it still exists and try again."))
      } catch (e: Exception) {
        repository.save(entity.copy(extractionStatus = STATUS_FAILED, extractedSummary = "Analysis failed. Tap Retry to try again."))
      }
    }
    return id
  }

  /** Runs the analysis again from the private copy kept on the phone. */
  fun retry(document: ProcessedDocumentEntity) {
    scope.launch {
      val file = fileOf(document)
      if (file == null || !file.exists()) {
        repository.save(document.copy(extractionStatus = STATUS_FAILED, extractedSummary = "The original file is no longer on this device. Add it again to analyse it."))
        return@launch
      }
      val working = document.copy(extractionStatus = STATUS_PROCESSING, extractedSummary = "Reading the document…")
      repository.save(working)
      try {
        repository.save(analyse(working, file))
      } catch (e: ExtractionException) {
        repository.save(working.copy(extractionStatus = STATUS_FAILED, extractedSummary = e.message ?: "Analysis failed."))
      } catch (e: Exception) {
        repository.save(working.copy(extractionStatus = STATUS_FAILED, extractedSummary = "Analysis failed. Tap Retry to try again."))
      }
    }
  }

  /** Removes the library entry and the private copy of the file. Nothing else is touched. */
  suspend fun delete(document: ProcessedDocumentEntity) {
    withContext(Dispatchers.IO) {
      fileOf(document)?.takeIf { it.exists() }?.delete()
    }
    repository.deleteLocal(document.id)
  }

  /** The private copy for an entry, or null when there is none (for example an old sample entry). */
  fun fileOf(document: ProcessedDocumentEntity): File? {
    val path = document.sourceUri?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path } ?: return null
    val file = File(path)
    // Only ever touch files inside our own library folder.
    return file.takeIf { it.canonicalPath.startsWith(libraryDir.canonicalPath + File.separator) }
  }

  private suspend fun analyse(entity: ProcessedDocumentEntity, file: File): ProcessedDocumentEntity {
    val extracted = DocumentTextExtractor.extract(file, entity.fileName, entity.mimeType)
    val result = DocumentAnalyzer.analyse(extracted.text)
    val partial = extracted.pageCount > extracted.pagesRead
    return entity.copy(
      extractedSummary = result.summary + if (partial) "\n\n(Only the first ${extracted.pagesRead} of ${extracted.pageCount} pages were read.)" else "",
      keyTopics = result.keyTopics.joinToString(", "),
      learningObjectives = result.objectives.joinToString("\n"),
      studyTips = result.studyTips.joinToString("\n"),
      wordCount = result.wordCount,
      pageCount = extracted.pageCount,
      readingTimeMinutes = result.readingMinutes,
      extractionStatus = STATUS_COMPLETED,
      timestamp = System.currentTimeMillis()
    )
  }

  private suspend fun copyToLibrary(uri: Uri, id: String, fileName: String): File = withContext(Dispatchers.IO) {
    val ext = DocumentKind.extensionOf(fileName).filter { it.isLetterOrDigit() }.take(8).ifBlank { "bin" }
    val target = File(libraryDir, "$id.$ext")
    val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
    input.use { source ->
      target.outputStream().use { sink ->
        var copied = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
          val n = source.read(buffer)
          if (n < 0) break
          copied += n
          if (copied > MAX_BYTES) {
            target.delete()
            throw ExtractionException("This file is larger than 50 MB. Use a smaller file.")
          }
          sink.write(buffer, 0, n)
        }
      }
    }
    target
  }

  companion object {
    const val STATUS_PROCESSING = "PROCESSING"
    const val STATUS_COMPLETED = "COMPLETED"
    const val STATUS_FAILED = "FAILED"
    private const val MAX_BYTES = 50L * 1024 * 1024

    @Volatile private var instance: DocumentAnalysisService? = null

    fun get(context: Context): DocumentAnalysisService = instance ?: synchronized(this) {
      instance ?: DocumentAnalysisService(context.applicationContext).also { instance = it }
    }
  }
}
