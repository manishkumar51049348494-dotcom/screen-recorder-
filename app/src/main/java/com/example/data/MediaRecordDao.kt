package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaRecordDao {
    @Query("SELECT * FROM media_records WHERE isVideo = 1 ORDER BY dateAdded DESC")
    fun getAllVideosFlow(): Flow<List<MediaRecordEntity>>

    @Query("SELECT * FROM media_records WHERE isVideo = 0 ORDER BY dateAdded DESC")
    fun getAllScreenshotsFlow(): Flow<List<MediaRecordEntity>>

    @Query("SELECT * FROM media_records WHERE id = :id")
    suspend fun getById(id: Long): MediaRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MediaRecordEntity): Long

    @Update
    suspend fun update(record: MediaRecordEntity)

    @Query("DELETE FROM media_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media_records WHERE filePath = :path")
    suspend fun deleteByPath(path: String)
}
