package com.github.tvbox.newbox.domain.usecase

import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.parser.SpiderResultParser
import com.github.tvbox.newbox.domain.BaseUseCase
import com.github.tvbox.newbox.domain.SearchResult
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SearchUseCase @Inject constructor(
    private val spiderFactory: SpiderFactory,
    private val parser: SpiderResultParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseUseCase<SearchUseCase.Params, List<SearchResult>> {

    private val json = Json { ignoreUnknownKeys = true }

    data class Params(
        val keyword: String,
        val sources: List<SourceConfig>,
        val quick: Boolean = false,
    )

    override suspend operator fun invoke(params: Params): List<SearchResult> =
        withContext(ioDispatcher) {
            params.sources.filter { it.searchable }.map { source ->
                async {
                    try {
                        val spider = spiderFactory
                            .createLoader(source.type.toSpiderType())
                            .load(SpiderSourceConfig(
                                key = source.key, name = source.name, api = source.api,
                                type = source.type.code, ext = source.ext, jar = source.jar,
                                spider = source.spider,
                                playerUrl = source.playerUrl, playerType = source.playerType,
                            ))
                        val resultJson = spider.searchContent(params.keyword, params.quick)
                        val result = json.decodeFromString<com.github.tvbox.newbox.spider.api.result.SearchContentResult>(resultJson)
                        SearchResult(
                            sourceKey = source.key,
                            sourceName = source.name,
                            vodItems = parser.parseSearchContent(result, source.key),
                        )
                    } catch (_: Exception) {
                        SearchResult(source.key, source.name, emptyList())
                    }
                }
            }.awaitAll().filter { it.vodItems.isNotEmpty() }
        }
}
