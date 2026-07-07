package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.Serializable

@Serializable
data class CategoryContentResult(
    @Serializable(with = FlexibleStringSerializer::class)
    val page: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val pagecount: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val total: String = "",
    @Serializable(with = FlexibleVodItemListSerializer::class)
    val list: List<VodItemResult> = emptyList(),
)
