package com.github.tvbox.newbox.domain.usecase

import android.util.Log
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
        val searchableSources = params.sources.filter { it.searchable }
        val skippedSources = params.sources.filterNot { it.searchable }
        Log.d(
            TAG,
            "searchProgressively: keyword=${params.keyword}, total=${params.sources.size}, searchable=${searchableSources.size}, skipped=${skippedSources.size}, quick=${params.quick}",
        )
        if (skippedSources.isNotEmpty()) {
            Log.d(TAG, "searchProgressively: skippedNonSearchable=${skippedSources.joinToString { it.key + '/' + it.name }}")
        }
        searchableSources.forEach { source ->
            launch(ioDispatcher) {
                send(searchSource(source, params.keyword, params.quick))
            }
        }
    }

    private suspend fun searchSource(source: SourceConfig, keyword: String, quick: Boolean): SearchResult {
        val startMs = System.currentTimeMillis()
        Log.d(
            TAG,
            "sourceStart: key=${source.key}, name=${source.name}, type=${source.type}, searchable=${source.searchable}, quickSearch=${source.quickSearch}, api=${source.api.take(120)}, jar=${source.jar.orEmpty().take(80)}, spider=${source.spider.take(80)}",
        )
        return try {
            val spider = spiderFactory
                .createLoader(source.type.toSpiderType())
                .load(SpiderSourceConfig(
                    key = source.key, name = source.name, api = source.api,
                    type = source.type.code, ext = source.ext, jar = source.jar,
                    spider = source.spider,
                    playerUrl = source.playerUrl, playerType = source.playerType,
                ))
            Log.d(TAG, "sourceLoaded: key=${source.key}, spider=${spider.javaClass.name}, elapsed=${System.currentTimeMillis() - startMs}ms")
            val resultJson = spider.searchContent(keyword, quick)
            Log.d(
                TAG,
                "sourceRaw: key=${source.key}, length=${resultJson.length}, snippet=${resultJson.logSnippet()}",
            )
            val normalizedJson = resultJson.trim()
            if (normalizedJson.isBlank()) {
                Log.d(TAG, "sourceEmpty: key=${source.key}, name=${source.name}, reason=blankRaw, elapsed=${System.currentTimeMillis() - startMs}ms")
                return SearchResult(source.key, source.name, emptyList())
            }
            if (!normalizedJson.startsWith("{")) {
                Log.d(
                    TAG,
                    "sourceEmpty: key=${source.key}, name=${source.name}, reason=nonObjectRaw, first=${normalizedJson.take(40)}, elapsed=${System.currentTimeMillis() - startMs}ms",
                )
                return SearchResult(source.key, source.name, emptyList())
            }
            val result = json.decodeFromString<com.github.tvbox.newbox.spider.api.result.SearchContentResult>(normalizedJson)
            val vodItems = parser.parseSearchContent(result, source.key)
            val elapsed = System.currentTimeMillis() - startMs
            if (vodItems.isEmpty()) {
                Log.d(TAG, "sourceEmpty: key=${source.key}, name=${source.name}, rawList=${result.list.size}, elapsed=${elapsed}ms")
            } else {
                Log.d(TAG, "sourceSuccess: key=${source.key}, name=${source.name}, rawList=${result.list.size}, parsed=${vodItems.size}, elapsed=${elapsed}ms")
            }
            SearchResult(
                sourceKey = source.key,
                sourceName = source.name,
                vodItems = vodItems,
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "sourceFail: key=${source.key}, name=${source.name}, type=${source.type}, api=${source.api.take(120)}, elapsed=${System.currentTimeMillis() - startMs}ms, error=${e.javaClass.simpleName}: ${e.message}",
                e,
            )
            SearchResult(source.key, source.name, emptyList())
        }
    }

    private suspend fun Flow<SearchResult>.collectToList(): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        collect { results += it }
        return results
    }

    private fun String.logSnippet(): String = take(800).replace('\n', ' ').replace('\r', ' ')
}
