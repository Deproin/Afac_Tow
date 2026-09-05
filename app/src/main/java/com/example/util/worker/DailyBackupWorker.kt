package com.example.util.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.util.BackupManager
import com.example.util.GoogleDriveManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("DailyBackupWorker", "Starting daily automatic backup...")
            
            val dbFile = BackupManager.getPreparedDbFile(context)
            if (dbFile == null || !dbFile.exists()) {
                Log.e("DailyBackupWorker", "Database file not found!")
                return@withContext Result.failure()
            }

            // 1. Create a local backup copy in App Storage
            val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) backupDir.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val autoBackupFile = File(backupDir, "afaq_auto_backup_$timeStamp.db")

            FileInputStream(dbFile).use { input ->
                FileOutputStream(autoBackupFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("DailyBackupWorker", "Local auto backup created: ${autoBackupFile.absolutePath}")

            // 2. Upload to Google Drive if signed in
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                val driveManager = GoogleDriveManager(context)
                val driveService = driveManager.getDriveService(account)
                
                val result = driveManager.uploadBackup(driveService, autoBackupFile) { progress ->
                    Log.d("DailyBackupWorker", "Google Drive Upload Progress: $progress%")
                }
                
                if (result.first) {
                    Log.d("DailyBackupWorker", "Uploaded auto backup to Google Drive successfully!")
                } else {
                    Log.e("DailyBackupWorker", "Failed to upload to Google Drive: ${result.second}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("DailyBackupWorker", "Daily backup failed: ${e.message}")
            Result.retry()
        }
    }
}
