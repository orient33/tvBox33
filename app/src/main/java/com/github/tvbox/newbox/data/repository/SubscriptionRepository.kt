package com.github.tvbox.newbox.data.repository

import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.domain.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

interface SubscriptionRepository {
    val sources: Flow<List<SourceConfig>>
    val currentSource: Flow<SourceConfig?>
    val currentSubscriptionUrl: StateFlow<String?>
    val sourceCounts: StateFlow<Map<String, Int>>
    val sourcesLoaded: StateFlow<Boolean>
    suspend fun loadSubscription(url: String)
    suspend fun loadWarehouse(parentUrl: String, warehouseUrl: String)
    suspend fun removeSubscription(url: String)
    suspend fun setCurrentSource(key: String)
    suspend fun selectSubscription(url: String)
    suspend fun probeSubscription(url: String): ProbeResult
}

sealed class ProbeResult {
    data class SingleConfig(val url: String) : ProbeResult()
    data class MultiRoute(val routes: List<RouteEntry>) : ProbeResult()
    data class MultiWarehouse(val warehouses: List<WarehouseEntry>) : ProbeResult()
}

data class RouteEntry(val name: String, val url: String)
data class WarehouseEntry(val name: String, val url: String)

@Serializable
data class SubscriptionJson(
    val spider: String = "",
    val sites: List<SiteJson> = emptyList(),
    val lives: List<LiveJson> = emptyList(),
    val parses: List<ParseJson> = emptyList(),
)

@Serializable
data class SiteJson(
    val key: String = "",
    val name: String = "",
    val type: Int = 0,
    val api: String = "",
    val searchable: Int = 1,
    val quickSearch: Int = 0,
    val filterable: Int = 0,
    val playerUrl: String = "",
    val ext: String? = null,
    val jar: String? = null,
    val categories: String = "",
    val playerType: Int = 0,
    val click: String = "",
)

@Serializable
data class LiveJson(
    val name: String = "",
    val type: Int = 0,
    val url: String = "",
    val epgUrl: String = "",
    val playerType: Int = 0,
)

@Serializable
data class ParseJson(
    val name: String = "",
    val type: Int = 0,
    val url: String = "",
)

fun JsonObject.toSiteJson(): SiteJson {
    val ext = get("ext")
    val extStr = when (ext) {
        is JsonObject, is JsonArray -> ext.toString()
        is JsonPrimitive -> if (ext == JsonNull) null else ext.content
        else -> null
    }
    val cats = get("categories")
    val catStr = when (cats) {
        is JsonArray -> cats.jsonArray.mapNotNull {
            (it as? JsonPrimitive)?.takeIf { p -> p != JsonNull }?.content
        }.joinToString(",")
        is JsonPrimitive -> if (cats == JsonNull) "" else cats.content
        else -> ""
    }
    return SiteJson(
        key = primString("key"),
        name = primString("name"),
        type = primInt("type"),
        api = primString("api"),
        searchable = primIntOrDefault("searchable", 1),
        quickSearch = primIntOrDefault("quickSearch", 0),
        filterable = primIntOrDefault("filterable", 0),
        playerUrl = primStringOrDefault("playUrl", ""),
        ext = extStr,
        jar = primStringOrNull("jar"),
        categories = catStr,
        playerType = primIntOrDefault("playerType", 0),
        click = primStringOrDefault("click", ""),
    )
}

fun SiteJson.toSourceConfig(globalSpider: String = "", baseUrl: String = ""): SourceConfig = SourceConfig(
    key = key,
    name = name,
    api = api,
    type = SourceType.fromSite(type, api),
    searchable = searchable == 1,
    quickSearch = quickSearch == 1,
    filterable = filterable == 1,
    playerUrl = playerUrl.ensureScheme(baseUrl),
    ext = ext,
    jar = jar?.ensureScheme(baseUrl),
    spider = globalSpider.ensureScheme(baseUrl),
    categories = categories.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    playerType = playerType,
    clickSelector = click.ifBlank { null },
)

private fun SourceType.Companion.fromSite(type: Int, api: String): SourceType = when {
    api.endsWith(".js") || api.contains(".js?") -> SourceType.JS
    else -> SourceType.fromCode(type)
}

private fun JsonObject.primString(key: String): String {
    val v = this[key] ?: return ""
    return if (v is JsonPrimitive && v != JsonNull) v.content else ""
}

private fun JsonObject.primStringOrNull(key: String): String? {
    val v = this[key] ?: return null
    return if (v is JsonPrimitive && v != JsonNull) v.content else null
}

private fun JsonObject.primStringOrDefault(key: String, default: String): String {
    val v = this[key] ?: return default
    return if (v is JsonPrimitive && v != JsonNull) v.content else default
}

private fun JsonObject.primInt(key: String): Int {
    val v = this[key] ?: return 0
    return if (v is JsonPrimitive && v != JsonNull) v.content.toIntOrNull() ?: 0 else 0
}

private fun JsonObject.primIntOrDefault(key: String, default: Int): Int {
    val v = this[key] ?: return default
    return if (v is JsonPrimitive && v != JsonNull) v.content.toIntOrNull() ?: default else default
}

private fun String.ensureScheme(baseUrl: String = ""): String {
    if (isBlank()) return ""
    val parts = split(";md5;")
    val rawUrl = parts[0]
    val md5 = parts.getOrElse(1) { null }

    val urlAfterPrefix = rawUrl.removePrefix("img+")
    val isImg = rawUrl.startsWith("img+")

    val fixedUrl = when {
        urlAfterPrefix.startsWith("http://") || urlAfterPrefix.startsWith("https://") -> urlAfterPrefix
        urlAfterPrefix.startsWith("//") -> "https:$urlAfterPrefix"
        urlAfterPrefix.startsWith("./") || urlAfterPrefix.startsWith("../") -> {
            if (baseUrl.isNotBlank()) resolveRelative(baseUrl, urlAfterPrefix) else "http://$urlAfterPrefix"
        }
        else -> "http://$urlAfterPrefix"
    }

    val result = if (isImg) "img+$fixedUrl" else fixedUrl
    return if (md5 != null) "$result;md5;$md5" else result
}

private fun resolveRelative(baseUrl: String, relative: String): String {
    val base = baseUrl.substringBeforeLast("/") + "/"
    return base + relative.removePrefix("./")
}
