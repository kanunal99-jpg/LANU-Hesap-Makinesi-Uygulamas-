package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.database.AppDatabase
import com.example.database.AuditLogEntity
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 * SecretManager manages UI access triggering via '2011.'
 * It also secures local database audit logs using robust AES-GCM (AEAD) encryption
 * with keys safely persisted in private shared preferences.
 */
class SecretManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lanu_secure_prefs", Context.MODE_PRIVATE)
    private val auditLogDao = AppDatabase.getDatabase(context).auditLogDao()

    private val targetSequence = "2011."
    private val inputBuffer = StringBuilder()

    init {
        // Automatically provision local 256-bit log encryption key if not exists
        if (prefs.getString("log_crypto_key", null) == null) {
            val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val keyBase64 = Base64.encodeToString(rawKey, Base64.NO_WRAP)
            prefs.edit().putString("log_crypto_key", keyBase64).apply()
        }
    }

    private fun getLogKey(): SecretKeySpec? {
        return try {
            val keyBase64 = prefs.getString("log_crypto_key", null) ?: return null
            val rawKey = Base64.decode(keyBase64, Base64.NO_WRAP)
            SecretKeySpec(rawKey, "AES")
        } catch (e: Exception) {
            Log.e("SecretManager", "Failed retrieving log key: ${e.message}")
            null
        }
    }

    fun feedInput(char: Char): Boolean {
        inputBuffer.append(char)
        return inputBuffer.toString() == targetSequence
    }

    fun resetBuffer() {
        inputBuffer.clear()
    }

    /**
     * Secures the audit log with true AES-GCM encryption.
     */
    fun logOperation(operation: String, result: String) {
        try {
            val keySpec = getLogKey() ?: return
            val plainPayload = "$operation = $result | Zaman: ${System.currentTimeMillis()}"
            val encrypted = AEADEngine.encrypt(plainPayload, keySpec)

            val entity = AuditLogEntity(
                operation = operation,
                result = result,
                encryptedPayload = encrypted
            )
            auditLogDao.insertLog(entity)
        } catch (e: Exception) {
            Log.e("SecretManager", "Failed encrypting operation log: ${e.message}")
        }
    }

    /**
     * Decrypts the secure logs for display on the Audit Logs tab.
     */
    fun getLogs(): List<String> {
        return try {
            val keySpec = getLogKey() ?: return listOf("Şifreleme anahtarı bulunamadı.")
            val dbLogs = auditLogDao.getAllLogs()
            dbLogs.map { log ->
                try {
                    val decrypted = AEADEngine.decrypt(log.encryptedPayload, keySpec)
                    "[Şifreli Kayıt Room] $decrypted"
                } catch (e: Exception) {
                    "[Şifreli Kayıt Room] Şifre Çözme Hatası (Bozuk Veri)"
                }
            }
        } catch (e: Exception) {
            Log.e("SecretManager", "Failed gathering secure logs: ${e.message}")
            emptyList()
        }
    }
}
