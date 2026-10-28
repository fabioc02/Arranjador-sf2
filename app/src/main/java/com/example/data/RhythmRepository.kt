package com.example.data

import android.content.Context
import androidx.room.Room

object RhythmRepository {
    private var db: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return db ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "rhythm_database"
            ).build()
            db = instance
            instance
        }
    }
}
