package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.Serializable

@Serializable
data class CategoryContentResult(
    val page: String = "",
    val pagecount: String = "",
    val total: String = "",
    val list: List<VodItemResult> = emptyList(),
)
