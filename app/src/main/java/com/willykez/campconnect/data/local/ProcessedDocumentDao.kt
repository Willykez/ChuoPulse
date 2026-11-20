package com.willykez.campconnect.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessedDocumentDao {
  @Query("SELECT * FROM processed_documents ORDER BY timestamp DESC")
  fun getAllDocuments(): Flow<List<ProcessedDocumentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDocument(document: ProcessedDocumentEntity)

  @Query("DELETE FROM processed_documents WHERE id = :id")
  suspend fun deleteDocumentById(id: String)

  @Query("DELETE FROM processed_documents WHERE id LIKE 'seed-%'")
  suspend fun deleteSeedDocuments()

  @Query("UPDATE processed_documents SET extractionStatus = 'FAILED', extractedSummary = 'Analysis was interrupted. Tap Retry.' WHERE extractionStatus = 'PROCESSING'")
  suspend fun markInterrupted()
}
