package com.github.tvbox.newbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.tvbox.newbox.data.local.entity.VodRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface VodRecordDao {
    @Query("SELECT * FROM vod_record ORDER BY lastPlayTime DESC")
    fun getAll(): Flow<List<VodRecord>>

    @Query("SELECT * FROM vod_record WHERE vodId = :vodId LIMIT 1")
    suspend fun getById(vodId: String): VodRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: VodRecord)

    @Query("DELETE FROM vod_record WHERE vodId = :vodId")
    suspend fun deleteById(vodId: String)
}
