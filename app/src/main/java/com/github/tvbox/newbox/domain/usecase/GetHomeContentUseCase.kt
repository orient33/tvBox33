package com.github.tvbox.newbox.domain.usecase

import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.parser.SpiderResultParser
import com.github.tvbox.newbox.domain.BaseUseCase
import com.github.tvbox.newbox.domain.HomeContent
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import com.github.tvbox.newbox.spider.api.result.CategoryContentResult
import com.github.tvbox.newbox.spider.api.result.HomeContentResult
import android.util.Log
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

    data class Params(
        val sourceConfig: SourceConfig,
        val filter: Boolean = true,
        val categoryId: String? = null,
        val page: String = "1",
        val extend: Map<String, String> = emptyMap(),
    )

    override suspend operator fun invoke(params: Params): HomeContent = withContext(ioDispatcher) {
        val spider = loadSpider(params.sourceConfig)
        val resultJson = spider.homeContent(params.filter)
        val result = decodeJson<HomeContentResult>(
            resultJson,
            "首页分类",
            params.sourceConfig,
        )
        val homeContent = parser.parseHomeContent(
            result.withHomeVodFallback { spider.homeVideoContentOrBlank() },
            params.sourceConfig.key,
        )
        val categoryId = params.categoryId
        if (categoryId == null) {
            homeContent
        } else {
            val categoryJson = spider.categoryContent(categoryId, params.page, params.filter, params.extend)
            val categoryResult = decodeJson<CategoryContentResult>(
                categoryJson,
                "分类列表",
                params.sourceConfig,
            )
            homeContent.copy(
                videos = parser.parseCategoryContent(categoryResult, params.sourceConfig.key),
                page = categoryResult.page.ifBlank { params.page },
                pageCount = categoryResult.pagecount.ifBlank { params.page },
                total = categoryResult.total,
            )
        }
    }

    private suspend fun loadSpider(config: SourceConfig) = spiderFactory
        .createLoader(config.type.toSpiderType())
        .load(SpiderSourceConfig(
            key = config.key, name = config.name, api = config.api,
            type = config.type.code, ext = config.ext, jar = config.jar,
            spider = config.spider,
            playerUrl = config.playerUrl, playerType = config.playerType,
        ))

    private suspend fun com.github.tvbox.newbox.spider.api.Spider.homeVideoContentOrBlank(): String = try {
        homeVideoContent()
    } catch (_: Exception) {
        ""
    }

    private suspend fun HomeContentResult.withHomeVodFallback(loadHomeVod: suspend () -> String): HomeContentResult {
        if (list.isNotEmpty()) return this
        val homeVodJson = loadHomeVod()
        Log.d("NewBox-Home", "homeVod fallback: ${homeVodJson.length} chars")
        if (homeVodJson.isBlank()) return this
        val normalizedJson = homeVodJson.trim()
        if (!normalizedJson.startsWith("{")) return this
        return runCatching {
            val homeVod = json.decodeFromString<CategoryContentResult>(normalizedJson)
            Log.d("NewBox-Home", "homeVod fallback parsed: ${homeVod.list.size} videos")
            copy(list = homeVod.list)
        }.getOrDefault(this)
    }

    private inline fun <reified T> decodeJson(rawJson: String, label: String, source: SourceConfig): T {
        val normalizedJson = rawJson.trim()
        if (normalizedJson.isBlank()) {
            throw IllegalStateException("${label}响应为空: ${source.name}")
        }
        if (!normalizedJson.startsWith("{")) {
            throw IllegalStateException("${label}响应不是JSON对象: ${source.name}, ${normalizedJson.take(120)}")
        }
        return try {
            json.decodeFromString<T>(normalizedJson)
        } catch (e: Exception) {
            throw IllegalStateException("${label}JSON解析失败: ${source.name}, ${e.message}", e)
        }
    }
}
