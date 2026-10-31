package com.willykez.campconnect.data.local

import android.content.Context
import com.willykez.campconnect.data.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Local-first document library. Cloud writes are optional until a valid user session is supplied. */
class ProcessedDocumentRepository(
  context: Context,
  private val supabase: SupabaseClient = SupabaseClient()
) {
  private val dao = AppDatabase.getInstance(context).processedDocumentDao()

  fun observeDocuments(): Flow<List<ProcessedDocumentEntity>> = dao.getAllDocuments()
  fun observeByCourse(courseCode: String): Flow<List<ProcessedDocumentEntity>> = dao.getDocumentsByCourse(courseCode)
  fun search(query: String): Flow<List<ProcessedDocumentEntity>> =
    if (query.isBlank()) observeDocuments() else dao.searchDocuments(query.trim())
  suspend fun getById(id: String) = dao.getDocumentById(id)
  suspend fun deleteLocal(id: String) = withContext(Dispatchers.IO) { dao.deleteDocumentById(id) }
  suspend fun deleteAllLocal() = withContext(Dispatchers.IO) { dao.deleteAllDocuments() }

  /** Saves locally first so the item is available offline even if the network is unavailable. */
  suspend fun save(document: ProcessedDocumentEntity, accessToken: String? = null, uploaderId: String? = null): CloudSyncResult {
    withContext(Dispatchers.IO) { dao.insertDocument(document) }
    if (accessToken.isNullOrBlank() || uploaderId.isNullOrBlank() || !supabase.isConfigured) return CloudSyncResult.LOCAL_ONLY
    val row = JSONObject()
      .put("id", document.id)
      .put("uploader_id", uploaderId)
      .put("course_code", document.courseCode)
      .put("title", document.title)
      .put("file_name", document.fileName)
      .put("file_size", document.fileSize)
      .put("mime_type", document.mimeType)
      .put("course_title", document.courseTitle)
      .put("university_name", document.university)
      .put("uploader_name", document.uploaderName)
      .put("document_timestamp", document.timestamp)
      .put("extracted_summary", document.extractedSummary)
      .put("key_topics", document.keyTopics)
      .put("learning_objectives", document.learningObjectives)
      .put("study_tips", document.studyTips)
      .put("word_count", document.wordCount)
      .put("page_count", document.pageCount)
      .put("reading_time_minutes", document.readingTimeMinutes)
      .put("extraction_status", document.extractionStatus)
      .put("category", document.category)
      .put("tags", document.tags)
    val response = supabase.upsert("processed_documents", JSONArray().put(row), accessToken)
    return if (response.isSuccessful) CloudSyncResult.SYNCED else CloudSyncResult.LOCAL_ONLY
  }

  suspend fun update(document: ProcessedDocumentEntity, accessToken: String? = null, uploaderId: String? = null): CloudSyncResult = save(document, accessToken, uploaderId)
}

enum class CloudSyncResult { SYNCED, LOCAL_ONLY }
