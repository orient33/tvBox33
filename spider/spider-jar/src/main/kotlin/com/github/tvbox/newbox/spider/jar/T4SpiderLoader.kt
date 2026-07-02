package com.github.tvbox.newbox.spider.jar

import android.content.Context
import android.util.Log
import com.github.tvbox.newbox.spider.api.Spider
import com.github.tvbox.newbox.spider.api.SpiderLoader
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import com.github.tvbox.newbox.spider.api.SourceType
import okhttp3.OkHttpClient
import okhttp3.Request

class T4SpiderLoader(
    private val client: OkHttpClient = OkHttpClient(),
) : SpiderLoader {

    companion object { private const val TAG = "NewBox-HttpApi" }

    override fun isSupported(type: SourceType): Boolean =
        type == SourceType.T4 || type == SourceType.HTTP_API

    override suspend fun load(config: SpiderSourceConfig): Spider {
        Log.d(TAG, "load: key=${config.key} api=${config.api}")
        return T4Spider(config.api, client)
    }
}

class T4Spider(
    private val apiUrl: String,
    private val client: OkHttpClient,
) : Spider {

    companion object { private const val TAG = "NewBox-HttpApi" }

    override suspend fun init(context: Context, extend: String) {}

    override suspend fun homeContent(filter: Boolean): String {
        return fetch("${apiUrl}?ac=home")
    }

    override suspend fun homeVideoContent(): String {
        return fetch("${apiUrl}?ac=home")
    }

    override suspend fun categoryContent(tid: String, pg: String, filter: Boolean, extend: Map<String, String>): String {
        val sb = StringBuilder("${apiUrl}?ac=list&tid=$tid&pg=$pg")
        extend.forEach { (k, v) -> sb.append("&$k=$v") }
        return fetch(sb.toString())
    }

    override suspend fun detailContent(ids: List<String>): String {
        return fetch("${apiUrl}?ac=detail&ids=${ids.joinToString(",")}")
    }

    override suspend fun searchContent(key: String, quick: Boolean, pg: String): String {
        return fetch("${apiUrl}?ac=search&wd=$key&pg=$pg")
    }

    override suspend fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        return fetch("${apiUrl}?ac=player&flag=$flag&id=$id")
    }

    private fun fetch(url: String): String {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    Log.d(TAG, "fetch: $url → ${body.length} chars")
                    body
                } else {
                    Log.e(TAG, "fetch: $url → HTTP ${response.code}")
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetch: $url → ${e.message}")
            ""
        }
    }
}
