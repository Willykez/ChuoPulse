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
  val keyTopics: String,
  val learningObjectives: String,
  val studyTips: String,
  val wordCount: Int,
  val pageCount: Int,
  val readingTimeMinutes: Int,
  val extractionStatus: String,
  val sourceUri: String? = null,
  val category: String = "Notes",
  val tags: String = ""
)
