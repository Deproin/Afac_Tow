package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

sealed class LicenseStatus {
    data class Active(val customerName: String = "عميل آفاق", val expiryDate: String = "غير محدود") : LicenseStatus()
    data class FreeTrial(val daysRemaining: Int = 7) : LicenseStatus()
    object Expired : LicenseStatus()
    object LimitReached : LicenseStatus()
    data class Error(val errText: String = "خطأ في التناظر") : LicenseStatus()

    val message: String get() = when(this) {
        is Active -> "الترخيص نشط وموثق"
        is FreeTrial -> "باقة تجريبية (متبقي $daysRemaining يوم)"
        is Expired -> "انتهت فترة التجربة المجانية"
        is LimitReached -> "تم الوصول للحد الأقصى للعمليات التجريبية"
        is Error -> errText
    }
}

/**
 * Manages app licensing, trial limits (7 days / 100 operations), 
 * server licenses, and feature restrictions for non-synced accounts.
 */
class LicenseManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("afaq_license_prefs", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_INSTALL_DATE)) {
            prefs.edit().putLong(KEY_INSTALL_DATE, System.currentTimeMillis()).apply()
        }
    }

    companion object {
        private const val KEY_INSTALL_DATE = "install_date"
        private const val KEY_OP_COUNT = "operation_count"
        private const val KEY_SYNC_CODE = "company_sync_code"
        private const val MAX_TRIAL_OPERATIONS = 100
        private const val MAX_TRIAL_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }

    val serverUrl: String = "https://afaq-license-server.onrender.com"

    init {
        val saved = prefs.getString("server_url", "")
        if (!saved.isNullOrEmpty() && (!saved.startsWith("http://") && !saved.startsWith("https://"))) {
            prefs.edit().remove("server_url").apply()
        }
    }

    fun isSyncedWithCompany(): Boolean {
        return getCompanySyncCode().isNotEmpty()
    }

    fun getCompanySyncCode(): String {
        return prefs.getString(KEY_SYNC_CODE, "") ?: ""
    }

    fun setCompanySyncCode(code: String) {
        prefs.edit().putString(KEY_SYNC_CODE, code).apply()
    }

    fun getInstallationDate(): Long {
        return prefs.getLong(KEY_INSTALL_DATE, System.currentTimeMillis())
    }

    fun getOperationCount(): Int {
        return prefs.getInt(KEY_OP_COUNT, 0)
    }

    fun incrementOperationCount() {
        if (!isSyncedWithCompany()) {
            val count = getOperationCount() + 1
            prefs.edit().putInt(KEY_OP_COUNT, count).apply()
        }
    }

    fun isDaysExpired(): Boolean {
        if (isSyncedWithCompany()) return false
        val elapsed = System.currentTimeMillis() - getInstallationDate()
        return elapsed > MAX_TRIAL_DAYS_MS
    }

    fun isOperationsExpired(): Boolean {
        if (isSyncedWithCompany()) return false
        return getOperationCount() >= MAX_TRIAL_OPERATIONS
    }

    fun canPerformOperation(): Boolean {
        if (prefs.getBoolean("is_server_suspended", false)) return false
        if (isSyncedWithCompany()) return true
        return !isDaysExpired() && !isOperationsExpired()
    }

    fun canManageUsers(): Boolean {
        if (prefs.getBoolean("is_server_suspended", false)) return false
        return isSyncedWithCompany()
    }

    fun getRemainingDays(): Int {
        if (prefs.getBoolean("is_server_suspended", false)) return 0
        if (isSyncedWithCompany()) return 999
        val elapsed = System.currentTimeMillis() - getInstallationDate()
        val remainingMs = MAX_TRIAL_DAYS_MS - elapsed
        if (remainingMs <= 0) return 0
        return (remainingMs / (1000 * 60 * 60 * 24)).toInt() + 1
    }

    fun getRemainingOperations(): Int {
        if (prefs.getBoolean("is_server_suspended", false)) return 0
        if (isSyncedWithCompany()) return 999999
        val rem = MAX_TRIAL_OPERATIONS - getOperationCount()
        return if (rem < 0) 0 else rem
    }

    fun isLicenseActive(): Boolean {
        return prefs.getBoolean("is_license_active", false)
    }

    fun evaluateStatus(opsCount: Int = 0): LicenseStatus {
        if (prefs.getBoolean("is_server_suspended", false)) return LicenseStatus.Expired
        if (isLicenseActive()) {
            val customerName = prefs.getString("customer_name", "عميل آفاق") ?: "عميل آفاق"
            val expiryDate = prefs.getString("expiry_date", "غير محدود") ?: "غير محدود"
            return LicenseStatus.Active(customerName, expiryDate)
        }
        val currentOps = if (opsCount > 0) opsCount else getOperationCount()
        if (isDaysExpired()) return LicenseStatus.Expired
        if (currentOps >= MAX_TRIAL_OPERATIONS) return LicenseStatus.LimitReached
        return LicenseStatus.FreeTrial(getRemainingDays())
    }

    fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "DEVICE_ID_AFAQ"
        } catch (e: Exception) {
            "DEVICE_ID_AFAQ"
        }
    }

    fun getRegisteredIdentifier(): String {
        return prefs.getString("registered_license_key", "") ?: ""
    }

    suspend fun checkServerLicense(): LicenseStatus = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val licenseKey = getRegisteredIdentifier()
        if (licenseKey.isEmpty()) return@withContext evaluateStatus()

        try {
            val url = java.net.URL("$serverUrl/api/license/check")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val jsonInputString = """
                {
                    "deviceId": "${getDeviceId()}",
                    "identifier": "$licenseKey"
                }
            """.trimIndent()

            conn.outputStream.use { os ->
                val input = jsonInputString.toByteArray(charset("utf-8"))
                os.write(input, 0, input.size)
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(responseText)
                val isValid = json.optBoolean("isValid", false)
                val customerName = json.optString("customerName", "عميل آفاق")
                val expiryDate = json.optString("expiryDate", "غير محدود")

                if (isValid) {
                    prefs.edit()
                        .putBoolean("is_server_suspended", false)
                        .putBoolean("is_license_active", true)
                        .putString("customer_name", customerName)
                        .putString("expiry_date", expiryDate)
                        .apply()
                    return@withContext LicenseStatus.Active(customerName, expiryDate)
                } else {
                    prefs.edit()
                        .putBoolean("is_server_suspended", true)
                        .putBoolean("is_license_active", false)
                        .apply()
                    return@withContext LicenseStatus.Expired
                }
            } else {
                return@withContext evaluateStatus()
            }
        } catch (e: Exception) {
            return@withContext evaluateStatus()
        }
    }

    suspend fun activateSubscription(key: String): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (key.trim().isEmpty()) {
            return@withContext Pair(false, "يرجى كتابة كود الاشتراك.")
        }
        try {
            val url = java.net.URL("$serverUrl/api/license/activate")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val jsonInputString = """
                {
                    "identifier": "${key.trim()}",
                    "deviceId": "${getDeviceId()}",
                    "deviceName": "${android.os.Build.MODEL ?: "Android Phone"}"
                }
            """.trimIndent()

            conn.outputStream.use { os ->
                val input = jsonInputString.toByteArray(charset("utf-8"))
                os.write(input, 0, input.size)
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }

            val json = org.json.JSONObject(responseText)
            val success = json.optBoolean("success", false)
            val message = json.optString("message", "فشل التفعيل")

            if (success) {
                val sub = json.optJSONObject("subscription")
                val customerName = sub?.optString("customerName", "عميل آفاق") ?: "عميل آفاق"
                val expiryDate = sub?.optString("expiryDate", "غير محدود") ?: "غير محدود"
                
                val currentCode = getCompanySyncCode()
                val codeToSave = if (currentCode.isNotEmpty()) currentCode else key.trim()

                prefs.edit()
                    .putBoolean("is_license_active", true)
                    .putBoolean("is_server_suspended", false)
                    .putString(KEY_SYNC_CODE, codeToSave)
                    .putString("registered_license_key", key.trim())
                    .putString("customer_name", customerName)
                    .putString("expiry_date", expiryDate)
                    .apply()

                return@withContext Pair(true, message)
            } else {
                return@withContext Pair(false, message)
            }
        } catch (e: Exception) {
            return@withContext Pair(false, "تعذر الاتصال بسيرفر التراخيص: ${e.message}")
        }
    }
}

fun sha256(input: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
