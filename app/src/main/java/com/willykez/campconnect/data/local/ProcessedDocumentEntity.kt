package com.willykez.campconnect.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "processed_documents",
  indices = [Index("courseCode"), Index("timestamp")]
)
data class ProcessedDocumentEntity(
  @PrimaryKey val id: String,
  val title: String,
  val fileName: String,
  val fileSize: Long,
  val mimeType: String,
  val courseCode: String,
  val courseTitle: String,
  val university: String,
  val uploaderName: String,
  val timestamp: Long,
  val extractedSummary: String,
  /** Comma-separated display terms to keep this entity migration-friendly. */
  val keyTopics: String,
  /** Newline-separated objectives. */
  val learningObjectives: String,
  /** Newline-separated study tips. */
  val studyTips: String,
  val wordCount: Int,
  val pageCount: Int,
  val readingTimeMinutes: Int,
  val extractionStatus: String,
  /** Optional content URI used by the built-in viewer when the source file is still available. */
  val sourceUri: String? = null,
  val category: String = "Notes",
  val tags: String = ""
)
