package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class DetailContentResult(
    val list: List<VodDetailResult> = emptyList(),
)

@Serializable
data class VodDetailResult(
    @SerialName("vod_id") val vodId: String = "",
    @SerialName("vod_name") val vodName: String = "",
    @SerialName("vod_pic") val vodPic: String = "",
    @SerialName("type_id") val typeId: String = "",
    @SerialName("type_name") val typeName: String = "",
    @SerialName("vod_year") val vodYear: String = "",
    @SerialName("vod_area") val vodArea: String = "",
    @SerialName("vod_actor") val vodActor: String = "",
    @SerialName("vod_director") val vodDirector: String = "",
    @SerialName("vod_content") val vodContent: String = "",
    @SerialName("vod_play_from") val vodPlayFrom: String = "",
    @SerialName("vod_play_url") val vodPlayUrl: String = "",
    @SerialName("vod_remarks") val vodRemarks: String = "",
)
