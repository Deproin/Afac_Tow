package com.example.util

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.Collections
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

data class DriveBackupFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: Long
)

class GoogleDriveManager(private val context: Context) {

    private val FOLDER_NAME = "Afaq Accounting Backups"
    private val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

    fun getSignInClient(): GoogleSignInClient {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, signInOptions)
    }

    fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Afaq Accounting").build()
    }

    suspend fun uploadBackup(driveService: Drive, dbFile: java.io.File, progressListener: (Int) -> Unit): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            progressListener(10)
            val folderId = getOrCreateBackupFolder(driveService) ?: return@withContext Pair(false, "فشل إنشاء مجلد النسخ الاحتياطية في درايف")
            progressListener(30)

            val fileMetadata = File()
            fileMetadata.name = BackupManager.generateBackupFileName()
            fileMetadata.parents = listOf(folderId)

            val mediaContent = FileContent("application/x-sqlite3", dbFile)

            progressListener(50)
            val request = driveService.files().create(fileMetadata, mediaContent)
            
            request.execute()
            progressListener(100)
            Pair(true, "تم الرفع بنجاح")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.localizedMessage ?: "حدث خطأ غير معروف")
        }
    }

    suspend fun listBackups(driveService: Drive): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        try {
            val folderId = getOrCreateBackupFolder(driveService) ?: return@withContext emptyList()
            
            val query = "'${folderId}' in parents and trashed = false"
            val result: FileList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, size, createdTime)")
                .execute()

            result.files?.mapNotNull { file ->
                DriveBackupFile(
                    id = file.id,
                    name = file.name,
                    size = file.getSize() ?: 0L,
                    createdTime = file.createdTime?.value ?: 0L
                )
            }?.sortedByDescending { it.createdTime } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun downloadBackup(driveService: Drive, fileId: String, destFile: java.io.File, progressListener: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            progressListener(10)
            val outputStream = FileOutputStream(destFile)
            progressListener(30)
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            progressListener(100)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun getOrCreateBackupFolder(driveService: Drive): String? {
        // Search for the folder
        val query = "mimeType='${FOLDER_MIME_TYPE}' and name='${FOLDER_NAME}' and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val files = result.files
        if (!files.isNullOrEmpty()) {
            return files[0].id
        }

        // Create the folder if it doesn't exist
        val fileMetadata = File()
        fileMetadata.name = FOLDER_NAME
        fileMetadata.mimeType = FOLDER_MIME_TYPE

        val folder = driveService.files().create(fileMetadata)
            .setFields("id")
            .execute()

        return folder.id
    }
}
