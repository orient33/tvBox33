package com.github.tvbox.newbox.domain.usecase

import android.content.Context
import com.github.tvbox.osc.util.Logger
import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.parser.SpiderResultParser
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.domain.BaseUseCase
import com.github.tvbox.newbox.domain.PlayerResult
import com.github.tvbox.newbox.player.VideoSniffer
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GetPlayerUrlUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val spiderFactory: SpiderFactory,
    private val subscriptionRepository: SubscriptionRepository,
    private val parser: SpiderResultParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseUseCase<GetPlayerUrlUseCase.Params, PlayerResult> {

    companion object {
        private const val TAG = "NewBox-Player"
        private const val PLAYER_TIMEOUT_MS = 15_000L
        private const val SNIFF_TIMEOUT_MS = 20_000L
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class Params(
        val flag: String,
        val playUrl: String,
        val sourceKey: String,
        val vipFlags: List<String> = emptyList(),
    )

    override suspend operator fun invoke(params: Params): PlayerResult = withContext(ioDispatcher) {
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
        Logger.d(TAG, "playerContent START source=${source.key}/${source.name}, flag=${params.flag}, playUrl=${params.playUrl}")
        val resultJson = try {
            withTimeout(PLAYER_TIMEOUT_MS) {
                spider.playerContent(params.flag, params.playUrl, params.vipFlags)
            }
        } catch (e: TimeoutCancellationException) {
            Logger.e(TAG, "playerContent timed out after ${PLAYER_TIMEOUT_MS}ms source=${source.key}/${source.name}, flag=${params.flag}")
            throw IllegalStateException("解析播放地址超时，该源可能不可用")
        } catch (e: Exception) {
            Logger.e(TAG, "playerContent failed source=${source.key}/${source.name}, flag=${params.flag}, playUrl=${params.playUrl}", e)
            throw e
        }
        val result = try {
            json.decodeFromString<com.github.tvbox.newbox.spider.api.result.PlayerContentResult>(resultJson)
        } catch (e: Exception) {
            Logger.e(TAG, "playerContent JSON parse failed source=${source.key}/${source.name}, flag=${params.flag}, playUrl=${params.playUrl}, error=${e.javaClass.simpleName}: ${e.message}", e)
            Logger.e(TAG, "playerContent raw JSON (first 500 chars): ${resultJson.take(500)}")
            throw IllegalStateException("解析播放地址失败: ${e.message}", e)
        }
        val playerResult = parser.parsePlayerContent(result)
        Logger.d(
            TAG,
            "playerContent RESULT source=${source.key}/${source.name}, parse=${result.parse}, " +
                "playUrl=${result.playUrl}, url=${playerResult.url}, needSniff=${playerResult.needSniff}",
        )

        if (playerResult.needSniff) {
            val sniffUrl = if (result.url.isNotBlank()) {
                result.playUrl + playerResult.url
            } else {
                playerResult.url
            }
            Logger.d(TAG, "parse==1, sniffing video URL from $sniffUrl")
            val sniffer = VideoSniffer(appContext)
            val sniffedUrl = withContext(Dispatchers.Main) {
                withTimeoutOrNull(SNIFF_TIMEOUT_MS) {
                    sniffer.sniff(sniffUrl, playerResult.headers)
                }
            }
            if (sniffedUrl != null) {
                Logger.d(TAG, "sniffed video URL: $sniffedUrl")
                playerResult.copy(url = sniffedUrl, needSniff = false)
            } else {
                Logger.e(TAG, "sniff failed, no video URL found")
                throw IllegalStateException("嗅探播放地址失败，未找到视频流")
            }
        } else {
            playerResult
        }
    }

    private suspend fun <T> first(flow: kotlinx.coroutines.flow.Flow<T>): T = flow.first()
}
