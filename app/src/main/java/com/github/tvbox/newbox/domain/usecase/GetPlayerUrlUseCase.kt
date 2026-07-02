package com.github.tvbox.newbox.domain.usecase

import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.parser.SpiderResultParser
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.domain.BaseUseCase
import com.github.tvbox.newbox.domain.PlayerResult
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GetPlayerUrlUseCase @Inject constructor(
    private val spiderFactory: SpiderFactory,
    private val subscriptionRepository: SubscriptionRepository,
    private val parser: SpiderResultParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseUseCase<GetPlayerUrlUseCase.Params, PlayerResult> {

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
        val resultJson = spider.playerContent(params.flag, params.playUrl, params.vipFlags)
        val result = json.decodeFromString<com.github.tvbox.newbox.spider.api.result.PlayerContentResult>(resultJson)
        parser.parsePlayerContent(result)
    }

    private suspend fun <T> first(flow: kotlinx.coroutines.flow.Flow<T>): T = flow.first()
}
