package com.willykez.campconnect.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(entities = [ProcessedDocumentEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun processedDocumentDao(): ProcessedDocumentDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
      INSTANCE ?: Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "campusconnect_local.db"
      ).addCallback(object : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
          super.onOpen(db)
          // Removes sample entries left on phones that installed an earlier build.
          CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            getInstance(context).processedDocumentDao().deleteSeedDocuments()
          }
        }
      }).build().also { INSTANCE = it }
    }
  }
}
