package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretMessageDao {
  @Insert
  fun insertMessage(message: SecretMessageEntity): Long

  @Query("SELECT * FROM secret_messages WHERE channelId = :channelId ORDER BY timestamp ASC")
  fun getMessagesForChannel(channelId: String): List<SecretMessageEntity>

  @Query("SELECT * FROM secret_messages WHERE channelId = :channelId ORDER BY timestamp ASC")
  fun getMessagesFlow(channelId: String): Flow<List<SecretMessageEntity>>

  @Query("DELETE FROM secret_messages WHERE channelId = :channelId")
  fun clearMessages(channelId: String)
}
