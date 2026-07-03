package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.Serializable

@Serializable
data class PlayerContentResult(
    val parse: Int? = null,
    val playUrl: String = "",
    val url: String = "",
    val header: Map<String, String> = emptyMap(),
    val flag: String = "",
    val UA: String = "",
    val referer: String = "",
    val jx: String = "",
)
