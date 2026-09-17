package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
  @Insert
  fun insertLog(log: AuditLogEntity)

  @Query("SELECT * FROM encrypted_audit_logs ORDER BY timestamp DESC")
  fun getAllLogs(): List<AuditLogEntity>

  @Query("SELECT * FROM encrypted_audit_logs ORDER BY timestamp DESC")
  fun getAllLogsFlow(): Flow<List<AuditLogEntity>>

  @Query("DELETE FROM encrypted_audit_logs")
  fun clearLogs()
}
