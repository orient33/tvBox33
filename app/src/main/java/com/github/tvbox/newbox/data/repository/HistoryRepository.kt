package com.github.tvbox.newbox.data.repository

import com.github.tvbox.newbox.data.local.dao.VodRecordDao
import com.github.tvbox.newbox.data.local.entity.VodRecord
import com.github.tvbox.newbox.domain.VodDetail
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface HistoryRepository {
    val allRecords: Flow<List<VodRecord>>
    suspend fun recordHistory(
        detail: VodDetail,
        flag: String,
        episodeIndex: Int,
        progress: Long,
        sourceName: String,
    )
    suspend fun getRecord(vodId: String): VodRecord?
    suspend fun deleteRecord(vodId: String)
}

@Singleton
class DefaultHistoryRepository @Inject constructor(
    private val dao: VodRecordDao,
) : HistoryRepository {

    override val allRecords: Flow<List<VodRecord>> = dao.getAll()

    override suspend fun recordHistory(
        detail: VodDetail,
        flag: String,
        episodeIndex: Int,
        progress: Long,
        sourceName: String,
    ) {
        val now = System.currentTimeMillis()
        dao.insert(
            VodRecord(
                vodId = detail.id,
                vodName = detail.name,
                vodPic = detail.pic,
                sourceKey = detail.sourceKey,
                sourceName = sourceName,
                lastPlayFlag = flag,
                lastPlayIndex = episodeIndex,
                lastPlayProgress = progress,
                lastPlayTime = now,
                updateTime = now,
            ),
        )
    }

    override suspend fun getRecord(vodId: String): VodRecord? = dao.getById(vodId)

    override suspend fun deleteRecord(vodId: String) {
        dao.deleteById(vodId)
    }
}
