package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    suspend fun exportDatabaseToUri(context: Context, destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Checkpoint WAL data to main DB file before copying
            val db = AppDatabase.getDatabase(context)
            try {
                val dbHelper = db.openHelper.writableDatabase
                dbHelper.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val dbFile = context.getDatabasePath("afaq_accounting_db")
            if (!dbFile.exists()) return@withContext false

            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                FileInputStream(dbFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getPreparedDbFile(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            try {
                val dbHelper = db.openHelper.writableDatabase
                dbHelper.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val dbFile = context.getDatabasePath("afaq_accounting_db")
            if (dbFile.exists()) dbFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    suspend fun importDatabaseFromUri(context: Context, srcUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Close existing connection to prevent cache overwrites
            AppDatabase.getDatabase(context).close()

            val dbFile = context.getDatabasePath("afaq_accounting_db")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            // Delete WAL & SHM files to avoid stale journal conflicts
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            context.contentResolver.openInputStream(srcUri)?.use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importDatabaseFromFile(context: Context, srcFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // Close existing connection to prevent cache overwrites
            AppDatabase.getDatabase(context).close()

            val dbFile = context.getDatabasePath("afaq_accounting_db")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            FileInputStream(srcFile).use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    fun generateBackupFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        return "afaq_backup_$timeStamp.db"
    }
}
