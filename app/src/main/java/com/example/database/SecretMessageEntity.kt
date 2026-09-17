package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secret_messages")
data class SecretMessageEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0L,
  val channelId: String,
  val senderName: String,
  val phoneNumber: String,
  val message: String,
  val timestamp: Long = System.currentTimeMillis(),
  val isMe: Boolean = false
)
