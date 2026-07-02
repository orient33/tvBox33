package com.github.tvbox.newbox.data.parser

import com.github.tvbox.newbox.domain.Category
import com.github.tvbox.newbox.domain.Episode
import com.github.tvbox.newbox.domain.FilterGroup
import com.github.tvbox.newbox.domain.FilterItem
import com.github.tvbox.newbox.domain.HomeContent
import com.github.tvbox.newbox.domain.PlayerResult
import com.github.tvbox.newbox.domain.SubtitleTrack
import com.github.tvbox.newbox.domain.VodDetail
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.spider.api.result.CategoryContentResult
import com.github.tvbox.newbox.spider.api.result.DetailContentResult
import com.github.tvbox.newbox.spider.api.result.HomeContentResult
import com.github.tvbox.newbox.spider.api.result.PlayerContentResult
import com.github.tvbox.newbox.spider.api.result.SearchContentResult
import com.github.tvbox.newbox.spider.api.result.VodDetailResult
import com.github.tvbox.newbox.spider.api.result.VodItemResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpiderResultParser @Inject constructor() {

    fun parseHomeContent(result: HomeContentResult, sourceKey: String = ""): HomeContent = HomeContent(
        categories = result.classes.map { Category(it.typeId.toString(), it.typeName) },
        videos = result.list.map { it.toVodItem(sourceKey) },
        filters = result.filters.mapValues { (_, groups) ->
            groups.map { group ->
                FilterGroup(
                    key = group.key,
                    name = group.name,
                    items = group.value.map { item ->
                        FilterItem(key = item.v, name = item.n, value = item.v)
                    },
                )
            }
        },
    )

    fun parseCategoryContent(result: CategoryContentResult, sourceKey: String = ""): List<VodItem> =
        result.list.map { it.toVodItem(sourceKey) }

    fun parseDetailContent(result: DetailContentResult, sourceKey: String = ""): VodDetail? =
        result.list.firstOrNull()?.toVodDetail(sourceKey)

    fun parseSearchContent(result: SearchContentResult, sourceKey: String = ""): List<VodItem> =
        result.list.map { it.toVodItem(sourceKey) }

    fun parsePlayerContent(result: PlayerContentResult): PlayerResult = PlayerResult(
        url = result.url.ifBlank { result.playUrl },
        headers = result.header,
        subtitles = emptyList(),
    )

    private fun VodItemResult.toVodItem(sourceKey: String) = VodItem(
        id = vodId.toString(),
        name = vodName,
        pic = vodPic,
        note = vodRemarks,
        type = typeName,
        year = vodYear,
        area = vodArea,
        sourceKey = sourceKey,
    )

    private fun VodDetailResult.toVodDetail(sourceKey: String): VodDetail {
        val flags = vodPlayFrom.split("$$$").filter { it.isNotBlank() }
        val urls = vodPlayUrl.split("$$$")

        val seriesMap = mutableMapOf<String, List<Episode>>()
        flags.forEachIndexed { index, flag ->
            val episodes = urls.getOrNull(index)
                ?.split("#")
                ?.map { ep ->
                    val parts = ep.split("$", limit = 2)
                    Episode(name = parts.getOrElse(0) { "" }, url = parts.getOrElse(1) { "" })
                }
                ?.filter { it.name.isNotBlank() }
                ?: emptyList()
            seriesMap[flag] = episodes
        }

        return VodDetail(
            id = vodId.toString(),
            name = vodName,
            pic = vodPic,
            type = typeName,
            year = vodYear,
            area = vodArea,
            actor = vodActor,
            director = vodDirector,
            description = vodContent,
            seriesFlags = flags,
            seriesMap = seriesMap,
            sourceKey = sourceKey,
        )
    }
}
