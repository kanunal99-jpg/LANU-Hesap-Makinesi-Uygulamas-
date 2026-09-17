package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encrypted_audit_logs")
data class AuditLogEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val timestamp: Long = System.currentTimeMillis(),
  val operation: String,
  val result: String,
  val encryptedPayload: String
)
