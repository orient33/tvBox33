package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.Serializable

@Serializable
data class PlayerContentResult(
    val parse: Int? = null,
    val playUrl: String = "",
    val url: String = "",
    val header: Map<String, String> = emptyMap(),
    val flag: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val UA: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val referer: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val jx: String = "",
)
