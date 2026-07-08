package com.github.tvbox.newbox.spider.jar

import android.content.Context
import com.github.tvbox.osc.util.Logger
import com.github.tvbox.newbox.spider.api.Spider
import com.github.tvbox.newbox.spider.api.SpiderLoader
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import com.github.tvbox.newbox.spider.api.SourceType
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

class T4SpiderLoader(
    private val client: OkHttpClient = OkHttpClient(),
) : SpiderLoader {

    companion object { private const val TAG = "NewBox-HttpApi" }

    override fun isSupported(type: SourceType): Boolean =
        type == SourceType.T4 || type == SourceType.HTTP_API

    override suspend fun load(config: SpiderSourceConfig): Spider {
        Logger.d(TAG, "load: key=${config.key} api=${config.api}")
        return if (SourceType.fromCode(config.type) == SourceType.T4) {
            T4Spider(config.api, client)
        } else {
            CmsHttpApiSpider(config.api, config.playerUrl, client)
        }
    }
}

/**
 * Standard CMS HTTP API used by original TVBox type=1 sources.
 */
class CmsHttpApiSpider(
    private val apiUrl: String,
    private val playerUrl: String,
    private val client: OkHttpClient,
) : Spider {

    companion object { private const val TAG = "NewBox-HttpApi" }

    override suspend fun init(context: Context, extend: String) {}

    override suspend fun homeContent(filter: Boolean): String {
        return fetch(apiUrl)
    }

    override suspend fun homeVideoContent(): String {
        return fetch(buildUrl("ac" to "detail"))
    }

    override suspend fun categoryContent(tid: String, pg: String, filter: Boolean, extend: Map<String, String>): String {
        return fetch(buildUrl(
            "ac" to "detail",
            "t" to tid,
            "pg" to pg,
            "f" to encodeFilterJson(extend),
            extraParams = extend,
        ))
    }

    override suspend fun detailContent(ids: List<String>): String {
        return fetch(buildUrl("ac" to "detail", "ids" to ids.joinToString(",")))
    }

    override suspend fun searchContent(key: String, quick: Boolean, pg: String): String {
        return fetch(buildUrl("wd" to key, "ac" to "detail", "pg" to pg))
    }

    override suspend fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        val parse = if (isVideoFormat(id) && playerUrl.isBlank()) 0 else 1
        return """{"parse":$parse,"url":${jsonQuote(id)},"playUrl":${jsonQuote(playerUrl)},"flag":${jsonQuote(flag)},"header":{}}"""
    }

    private fun isVideoFormat(url: String): Boolean {
        if (url.contains("url=http") || url.contains(".html")) return false
        return VIDEO_REGEX.containsMatchIn(url)
    }

    private fun buildUrl(vararg params: Pair<String, String>, extraParams: Map<String, String> = emptyMap()): String {
        val builder = apiUrl.toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) builder.addQueryParameter(key, value)
        }
        extraParams.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun encodeFilterJson(extend: Map<String, String>): String {
        if (extend.isEmpty()) return ""
        return extend.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonQuote(key)}:${jsonQuote(value)}"
        }
    }

    private fun jsonQuote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    private fun fetch(url: String): String {
        return fetchHttp(client, url, TAG)
    }
}

private val VIDEO_REGEX = Regex(
    "http((?!http).){12,}?\\.(m3u8|mp4|flv|avi|mkv|rm|wmv|mpg|m4a)\\?.*|" +
        "http((?!http).){12,}\\.(m3u8|mp4|flv|avi|mkv|rm|wmv|mpg|m4a)|" +
        "http((?!http).)*?video/tos*|" +
        "http((?!http).){20,}?/m3u8\\?pt=m3u8.*|" +
        "http((?!http).)*?default\\.ixigua\\.com/.*|" +
        "http((?!http).)*?dycdn-tos\\.pstatp[^\\?]*|" +
        "http.*?/player/m3u8play\\.php\\?url=.*|" +
        "http.*?/player/.*?[pP]lay\\.php\\?url=.*|" +
        "http.*?/playlist/m3u8/\\?vid=.*|" +
        "http.*?\\.php\\?type=m3u8&.*|" +
        "http.*?/download.aspx\\?.*|" +
        "http.*?/api/up_api.php\\?.*|" +
        "https.*?\\.66yk\\.cn.*|" +
        "http((?!http).)*?netease\\.com/file/.*",
)

class T4Spider(
    private val apiUrl: String,
    private val client: OkHttpClient,
) : Spider {

    companion object { private const val TAG = "NewBox-HttpApi" }

    override suspend fun init(context: Context, extend: String) {}

    override suspend fun homeContent(filter: Boolean): String {
        return fetch(buildUrl("filter" to filter.toString()))
    }

    override suspend fun homeVideoContent(): String {
        return fetch(buildUrl("filter" to "true"))
    }

    override suspend fun categoryContent(tid: String, pg: String, filter: Boolean, extend: Map<String, String>): String {
        return fetch(buildUrl(
            "ac" to "detail",
            "filter" to filter.toString(),
            "t" to tid,
            "pg" to pg,
            "ext" to encodeExt(extend),
        ))
    }

    override suspend fun detailContent(ids: List<String>): String {
        return fetch(buildUrl("ac" to "detail", "ids" to ids.joinToString(",")))
    }

    override suspend fun searchContent(key: String, quick: Boolean, pg: String): String {
        return fetch(buildUrl("wd" to key, "ac" to "detail", "quick" to quick.toString(), "pg" to pg))
    }

    override suspend fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        return fetch(buildUrl("play" to id, "flag" to flag))
    }

    private fun buildUrl(vararg params: Pair<String, String>): String {
        val builder = apiUrl.toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) builder.addQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun encodeExt(extend: Map<String, String>): String {
        val json = if (extend.isEmpty()) "{}" else extend.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonQuote(key)}:${jsonQuote(value)}"
        }
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun jsonQuote(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    private fun fetch(url: String): String {
        return fetchHttp(client, url, TAG)
    }
}

private fun fetchHttp(client: OkHttpClient, url: String, tag: String): String {
    try {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                Logger.d(tag, "fetch: $url → ${body.length} chars")
                if (body.isBlank()) throw IOException("空响应: $url")
                return body
            }
            Logger.e(tag, "fetch: $url → HTTP ${response.code}, body=${body.take(200)}")
            throw IOException("HTTP ${response.code}: $url")
        }
    } catch (e: Exception) {
        Logger.e(tag, "fetch: $url → ${e.message}", e)
        throw e
    }
}
