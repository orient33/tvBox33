package com.github.tvbox.newbox.domain.usecase

import com.github.tvbox.osc.util.Logger
import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.parser.SpiderResultParser
import com.github.tvbox.newbox.domain.BaseUseCase
import com.github.tvbox.newbox.domain.SearchResult
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SearchUseCase @Inject constructor(
    private val spiderFactory: SpiderFactory,
    private val parser: SpiderResultParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BaseUseCase<SearchUseCase.Params, List<SearchResult>> {

    companion object { private const val TAG = "NewBox-Search" }

    private val json = Json { ignoreUnknownKeys = true }

    data class Params(
        val keyword: String,
        val sources: List<SourceConfig>,
        val quick: Boolean = false,
    )

    override suspend operator fun invoke(params: Params): List<SearchResult> =
        withContext(ioDispatcher) {
            searchProgressively(params).collectToList().filter { it.vodItems.isNotEmpty() }
        }

    fun searchProgressively(params: Params): Flow<SearchResult> = channelFlow {
        params.sources.filter { it.searchable }.forEach { source ->
            launch(ioDispatcher) {
                send(searchSource(source, params.keyword, params.quick))
            }
        }
    }

    private suspend fun searchSource(source: SourceConfig, keyword: String, quick: Boolean): SearchResult {
        return try {
            val spider = spiderFactory
                .createLoader(source.type.toSpiderType())
                .load(SpiderSourceConfig(
                    key = source.key, name = source.name, api = source.api,
                    type = source.type.code, ext = source.ext, jar = source.jar,
                    spider = source.spider,
                    playerUrl = source.playerUrl, playerType = source.playerType,
                ))
            val resultJson = spider.searchContent(keyword, quick)
            val normalizedJson = resultJson.trim()
            if (normalizedJson.isBlank() || !normalizedJson.startsWith("{")) {
                return SearchResult(source.key, source.name, emptyList())
            }
            val result = json.decodeFromString<com.github.tvbox.newbox.spider.api.result.SearchContentResult>(normalizedJson)
            val vodItems = parser.parseSearchContent(result, source.key)
            SearchResult(
                sourceKey = source.key,
                sourceName = source.name,
                vodItems = vodItems,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "sourceFail: key=${source.key}, name=${source.name}, error=${e.javaClass.simpleName}: ${e.message}", e)
            SearchResult(source.key, source.name, emptyList())
        }
    }

    private suspend fun Flow<SearchResult>.collectToList(): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        collect { results += it }
        return results
    }
}
