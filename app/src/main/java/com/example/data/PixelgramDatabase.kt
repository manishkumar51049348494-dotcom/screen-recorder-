package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MediaRecordEntity::class], version = 1, exportSchema = false)
abstract class PixelgramDatabase : RoomDatabase() {
    abstract fun mediaRecordDao(): MediaRecordDao

    companion object {
        @Volatile
        private var INSTANCE: PixelgramDatabase? = null

        fun getInstance(context: Context): PixelgramDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PixelgramDatabase::class.java,
                    "pixelgram_recorder.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
