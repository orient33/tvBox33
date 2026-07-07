package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

@Serializable
data class HomeContentResult(
    @Serializable(with = FlexibleClassItemListSerializer::class)
    @SerialName("class") val classes: List<ClassItem> = emptyList(),
    @Serializable(with = FlexibleVodItemListSerializer::class)
    val list: List<VodItemResult> = emptyList(),
    @Serializable(with = FlexibleFilterMapSerializer::class)
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

private object FlexibleClassItemListSerializer : KSerializer<List<ClassItem>> {
    private val delegate = ListSerializer(ClassItem.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<ClassItem>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<ClassItem> = decodeList(decoder, delegate)
}

object FlexibleVodItemListSerializer : KSerializer<List<VodItemResult>> {
    private val delegate = ListSerializer(VodItemResult.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<VodItemResult>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<VodItemResult> = decodeList(decoder, delegate)
}

private object FlexibleFilterMapSerializer : KSerializer<Map<String, List<FilterGroupResult>>> {
    private val groupListSerializer = ListSerializer(FilterGroupResult.serializer())
    private val delegate = MapSerializer(String.serializer(), groupListSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, List<FilterGroupResult>>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, List<FilterGroupResult>> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> emptyMap()
            is JsonObject -> element.mapValues { (_, value) ->
                when (value) {
                    JsonNull -> emptyList()
                    is JsonArray -> jsonDecoder.json.decodeFromJsonElement(groupListSerializer, value)
                    is JsonObject -> listOf(jsonDecoder.json.decodeFromJsonElement(FilterGroupResult.serializer(), value))
                    else -> emptyList()
                }
            }
            else -> emptyMap()
        }
    }
}

private fun <T> decodeList(decoder: Decoder, serializer: KSerializer<List<T>>): List<T> {
    val jsonDecoder = decoder as? JsonDecoder ?: return serializer.deserialize(decoder)
    return when (val element = jsonDecoder.decodeJsonElement()) {
        JsonNull -> emptyList()
        is JsonArray -> jsonDecoder.json.decodeFromJsonElement(serializer, element)
        else -> emptyList()
    }
}
