package com.github.tvbox.newbox.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.tvbox.newbox.data.local.entity.VodCollect
import kotlinx.coroutines.flow.Flow

@Dao
interface VodCollectDao {
    @Query("SELECT * FROM vod_collect ORDER BY collectTime DESC")
    fun getAll(): Flow<List<VodCollect>>

    @Query("SELECT * FROM vod_collect WHERE vodId = :vodId LIMIT 1")
    suspend fun getById(vodId: String): VodCollect?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collect: VodCollect)

    @Delete
    suspend fun delete(collect: VodCollect)

    @Query("DELETE FROM vod_collect WHERE vodId = :vodId")
    suspend fun deleteById(vodId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM vod_collect WHERE vodId = :vodId)")
    suspend fun isCollected(vodId: String): Boolean
}
