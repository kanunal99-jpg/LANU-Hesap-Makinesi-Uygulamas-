package com.example.security

import android.content.Context
import android.content.SharedPreferences
import com.example.database.AppDatabase
import com.example.database.AuditLogEntity
import java.security.MessageDigest

class SecretManager(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("lanu_secure_prefs", Context.MODE_PRIVATE)
  private val auditLogDao = AppDatabase.getDatabase(context).auditLogDao()

  private val targetSequence = "2011."
  private val inputBuffer = StringBuilder()

  fun feedInput(char: Char): Boolean {
    inputBuffer.append(char)
    val current = inputBuffer.toString()
    if (current == targetSequence) {
      return true
    }
    if (current.length > targetSequence.length) {
      return false
    }
    return false
  }

  fun resetBuffer() {
    inputBuffer.clear()
  }

  fun setMasterPassword(password: String) {
    val hash = sha256(password)
    prefs.edit().putString("master_hash", hash).apply()
  }

  fun verifyMasterPassword(password: String): Boolean {
    val savedHash = prefs.getString("master_hash", sha256("admin2011")) ?: sha256("admin2011")
    return sha256(password) == savedHash
  }

  fun logOperation(operation: String, result: String) {
    val encryptedPayload = sha256("$operation = $result | ${System.currentTimeMillis()}")
    val entity = AuditLogEntity(
      operation = operation,
      result = result,
      encryptedPayload = encryptedPayload
    )
    auditLogDao.insertLog(entity)
  }

  fun getLogs(password: String): List<String> {
    if (!verifyMasterPassword(password)) return listOf("Yetkisiz Erişim! Şifre yanlış.")
    val dbLogs = auditLogDao.getAllLogs()
    return dbLogs.map { log ->
      "[Şifreli Kayıt Room] Zaman: ${log.timestamp} | ${log.operation} = ${log.result} | Hash: ${log.encryptedPayload.take(12)}..."
    }
  }

  private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
