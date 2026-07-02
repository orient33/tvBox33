package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.Serializable

@Serializable
data class SearchContentResult(
    val list: List<VodItemResult> = emptyList(),
)
