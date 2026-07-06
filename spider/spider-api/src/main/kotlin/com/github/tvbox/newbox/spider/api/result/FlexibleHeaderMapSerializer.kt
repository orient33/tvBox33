package com.github.tvbox.newbox.spider.api.result

import kotlinx.serialization.KSerializer
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

@OptIn(InternalSerializationApi::class)
object FlexibleHeaderMapSerializer : KSerializer<Map<String, String>> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        serialName = "FlexibleHeaderMap",
        kind = StructureKind.MAP,
    )

    override fun serialize(encoder: Encoder, value: Map<String, String>) {
        encoder.encodeSerializableValue(serializer(), value)
    }

    override fun deserialize(decoder: Decoder): Map<String, String> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeSerializableValue(serializer())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            JsonNull -> emptyMap()
            is JsonObject -> parseJsonObject(element)
            is JsonPrimitive -> {
                val content = element.content
                if (content.isBlank()) {
                    emptyMap()
                } else {
                    runCatching {
                        val parsed = jsonDecoder.json.parseToJsonElement(content)
                        if (parsed is JsonObject) parseJsonObject(parsed) else emptyMap()
                    }.getOrDefault(emptyMap())
                }
            }
            else -> emptyMap()
        }
    }

    private fun parseJsonObject(obj: JsonObject): Map<String, String> =
        obj.entries.mapNotNull { (key, value) ->
            when (value) {
                JsonNull -> null
                is JsonPrimitive -> key to value.content
                else -> key to value.toString()
            }
        }.toMap()
}
