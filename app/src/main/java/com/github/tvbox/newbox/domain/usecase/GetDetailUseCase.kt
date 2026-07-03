package com.github.tvbox.newbox.domain.usecase

import android.util.Log
import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.parser.SpiderResultParser
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.domain.BaseUseCase
import com.github.tvbox.newbox.domain.VodDetail
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GetDetailUseCase @Inject constructor(
    private val spiderFactory: SpiderFactory,
    private val subscriptionRepository: SubscriptionRepository,
    private val parser: SpiderResultParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseUseCase<GetDetailUseCase.Params, VodDetail> {

    companion object { 
        private const val TAG = "NewBox-Detail"
        private const val DETAIL_TIMEOUT_MS = 15_000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class Params(
        val vodId: String,
        val sourceKey: String,
    )

    override suspend operator fun invoke(params: Params): VodDetail = withContext(ioDispatcher) {
        val source = first(subscriptionRepository.sources)
            .find { it.key == params.sourceKey }
            ?: throw IllegalArgumentException("Source not found: ${params.sourceKey}")

        val spider = spiderFactory
            .createLoader(source.type.toSpiderType())
            .load(SpiderSourceConfig(
                key = source.key, name = source.name, api = source.api,
                type = source.type.code, ext = source.ext, jar = source.jar,
                spider = source.spider,
                playerUrl = source.playerUrl, playerType = source.playerType,
            ))
        val resultJson = try {
            withTimeout(DETAIL_TIMEOUT_MS) {
                spider.detailContent(listOf(params.vodId))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "detailContent timed out after ${DETAIL_TIMEOUT_MS}ms source=${source.key}/${source.name}, vodId=${params.vodId}")
            throw IllegalStateException("详情加载超时，该源可能不可用")
        } catch (e: Exception) {
            Log.e(TAG, "detailContent failed source=${source.key}/${source.name}, vodId=${params.vodId}", e)
            throw e
        }
        try {
            val result = json.decodeFromString<com.github.tvbox.newbox.spider.api.result.DetailContentResult>(resultJson)
            parser.parseDetailContent(result, params.sourceKey)
                ?: throw IllegalStateException("Detail not found for: ${params.vodId}")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Detail decode/parse failed source=${source.key}/${source.name}, vodId=${params.vodId}, jsonLength=${resultJson.length}, json=${resultJson.logSnippet()}",
                e,
            )
            throw e
        }
    }

    private suspend fun <T> first(flow: kotlinx.coroutines.flow.Flow<T>): T = flow.first()

    private fun String.logSnippet(): String = take(2000).replace('\n', ' ').replace('\r', ' ')
}
