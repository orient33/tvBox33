package com.github.tvbox.newbox.data.repository

import com.github.tvbox.newbox.data.local.dao.VodCollectDao
import com.github.tvbox.newbox.data.local.entity.VodCollect
import com.github.tvbox.newbox.domain.VodDetail
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface CollectRepository {
    val allCollects: Flow<List<VodCollect>>
    fun isCollected(vodId: String): Flow<Boolean>
    suspend fun toggleCollect(detail: VodDetail, sourceName: String = "")
    suspend fun deleteCollect(vodId: String)
}

@Singleton
class DefaultCollectRepository @Inject constructor(
    private val dao: VodCollectDao,
) : CollectRepository {

    override val allCollects: Flow<List<VodCollect>> = dao.getAll()

    override fun isCollected(vodId: String): Flow<Boolean> =
        kotlinx.coroutines.flow.flow {
            // Emit initial then re-emit on every collect list change
            allCollects.collect { list ->
                emit(list.any { it.vodId == vodId })
            }
        }

    override suspend fun toggleCollect(detail: VodDetail, sourceName: String) {
        val existing = dao.getById(detail.id)
        if (existing != null) {
            dao.deleteById(detail.id)
        } else {
            dao.insert(
                VodCollect(
                    vodId = detail.id,
                    vodName = detail.name,
                    vodPic = detail.pic,
                    sourceKey = detail.sourceKey,
                    sourceName = sourceName,
                ),
            )
        }
    }

    override suspend fun deleteCollect(vodId: String) {
        dao.deleteById(vodId)
    }
}
