package com.github.tvbox.newbox.domain.usecase

import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.parser.SpiderResultParser
import com.github.tvbox.newbox.domain.BaseUseCase
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GetCategoryContentUseCase @Inject constructor(
    private val spiderFactory: SpiderFactory,
    private val parser: SpiderResultParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseUseCase<GetCategoryContentUseCase.Params, GetCategoryContentUseCase.Result> {

    private val json = Json { ignoreUnknownKeys = true }

    data class Params(
        val sourceConfig: SourceConfig,
        val tid: String,
        val pg: String = "1",
        val filter: Boolean = false,
        val extend: Map<String, String> = emptyMap(),
    )

    data class Result(
        val videos: List<VodItem>,
        val page: String,
        val pageCount: String,
        val total: String,
    )

    override suspend operator fun invoke(params: Params): Result = withContext(ioDispatcher) {
        val spider = spiderFactory
            .createLoader(params.sourceConfig.type.toSpiderType())
            .load(SpiderSourceConfig(
                key = params.sourceConfig.key, name = params.sourceConfig.name, api = params.sourceConfig.api,
                type = params.sourceConfig.type.code, ext = params.sourceConfig.ext, jar = params.sourceConfig.jar,
                spider = params.sourceConfig.spider,
                playerUrl = params.sourceConfig.playerUrl, playerType = params.sourceConfig.playerType,
            ))
        val resultJson = spider.categoryContent(params.tid, params.pg, params.filter, params.extend)
        val result = json.decodeFromString<com.github.tvbox.newbox.spider.api.result.CategoryContentResult>(resultJson)
        val videos = parser.parseCategoryContent(result, params.sourceConfig.key)
        Result(videos = videos, page = result.page, pageCount = result.pagecount, total = result.total)
    }
}
