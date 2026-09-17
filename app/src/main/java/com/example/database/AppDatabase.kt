package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AuditLogEntity::class, SecretMessageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun auditLogDao(): AuditLogDao
  abstract fun secretMessageDao(): SecretMessageDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "lanu_encrypted_database.db"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
        INSTANCE = instance
        instance
      }
    }
  }
}
