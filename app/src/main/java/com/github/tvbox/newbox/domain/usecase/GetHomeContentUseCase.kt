package com.github.tvbox.newbox.domain.usecase

import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.parser.SpiderResultParser
import com.github.tvbox.newbox.domain.BaseUseCase
import com.github.tvbox.newbox.domain.HomeContent
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GetHomeContentUseCase @Inject constructor(
    private val spiderFactory: SpiderFactory,
    private val parser: SpiderResultParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseUseCase<GetHomeContentUseCase.Params, HomeContent> {

    private val json = Json { ignoreUnknownKeys = true }

    data class Params(val sourceConfig: SourceConfig, val filter: Boolean = true)

    override suspend operator fun invoke(params: Params): HomeContent = withContext(ioDispatcher) {
        val spider = loadSpider(params.sourceConfig)
        val resultJson = spider.homeContent(params.filter)
        val result = json.decodeFromString<com.github.tvbox.newbox.spider.api.result.HomeContentResult>(resultJson)
        parser.parseHomeContent(result, params.sourceConfig.key)
    }

    private suspend fun loadSpider(config: SourceConfig) = spiderFactory
        .createLoader(config.type.toSpiderType())
        .load(SpiderSourceConfig(
            key = config.key, name = config.name, api = config.api,
            type = config.type.code, ext = config.ext, jar = config.jar,
            spider = config.spider,
            playerUrl = config.playerUrl, playerType = config.playerType,
        ))
}
