package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HomeContentResult(
    @SerialName("class") val classes: List<ClassItem> = emptyList(),
    val list: List<VodItemResult> = emptyList(),
    val filters: Map<String, List<FilterGroupResult>> = emptyMap(),
)

@Serializable
data class ClassItem(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("type_id") val typeId: String = "",
    @SerialName("type_name") val typeName: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("type_pid") val typePid: String = "",
)

@Serializable
data class VodItemResult(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("vod_id") val vodId: String = "",
    @SerialName("vod_name") val vodName: String = "",
    @SerialName("vod_pic") val vodPic: String = "",
    @SerialName("vod_remarks") val vodRemarks: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("type_id") val typeId: String = "",
    @SerialName("type_name") val typeName: String = "",
    @SerialName("vod_year") val vodYear: String = "",
    @SerialName("vod_area") val vodArea: String = "",
    @SerialName("vod_actor") val vodActor: String = "",
    @SerialName("vod_director") val vodDirector: String = "",
    @SerialName("vod_content") val vodContent: String = "",
    @SerialName("vod_play_from") val vodPlayFrom: String = "",
    @SerialName("vod_play_url") val vodPlayUrl: String = "",
)

@Serializable
data class FilterGroupResult(
    val key: String = "",
    val name: String = "",
    val value: List<FilterItemResult> = emptyList(),
)

@Serializable
data class FilterItemResult(
    val n: String = "",
    val v: String = "",
)
