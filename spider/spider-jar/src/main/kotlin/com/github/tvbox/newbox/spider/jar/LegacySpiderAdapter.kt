package com.github.tvbox.newbox.spider.jar

import android.content.Context
import android.util.Log
import com.github.tvbox.newbox.spider.api.Spider

class LegacySpiderAdapter(
    private val legacySpider: com.github.catvod.crawler.Spider,
) : Spider {

    companion object { private const val TAG = "NewBox-Legacy" }

    override suspend fun init(context: Context, extend: String) {
        legacySpider.init(context, extend)
    }

    override suspend fun homeContent(filter: Boolean): String {
        return legacySpider.homeContent(filter)
    }

    override suspend fun homeVideoContent(): String {
        return legacySpider.homeVideoContent()
    }

    override suspend fun categoryContent(
        tid: String,
        pg: String,
        filter: Boolean,
        extend: Map<String, String>,
    ): String {
        return legacySpider.categoryContent(tid, pg, filter, HashMap(extend as Map<String, String>))
    }

    override suspend fun detailContent(ids: List<String>): String {
        return try {
            legacySpider.detailContent(ids)
        } catch (e: Exception) {
            Log.e(TAG, "detailContent: ${e.javaClass.simpleName}: ${e.message}")
            ""
        }
    }

    override suspend fun searchContent(key: String, quick: Boolean, pg: String): String {
        return try {
            if (pg == "1") {
                legacySpider.searchContent(key, quick)
            } else {
                legacySpider.searchContent(key, quick, pg)
            }
        } catch (e: NoSuchMethodError) {
            legacySpider.searchContent(key, quick)
        }
    }

    override suspend fun playerContent(
        flag: String,
        id: String,
        vipFlags: List<String>,
    ): String {
        return legacySpider.playerContent(flag, id, vipFlags)
    }

    override suspend fun proxyLocal(params: Map<String, String>): Array<Any?> {
        return try {
            val result = legacySpider.proxyLocal(HashMap(params))
            if (result != null && result.isNotEmpty()) result else emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "proxyLocal: exception=${e.message}", e)
            emptyArray()
        }
    }

    override suspend fun proxy(params: Map<String, String>): Array<Any?> {
        return try {
            val result = legacySpider.proxy(HashMap(params))
            if (result != null && result.isNotEmpty()) result else emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "proxy: exception=${e.message}", e)
            emptyArray()
        }
    }

    override fun cancelByTag(tag: String) {
        legacySpider.cancelByTag(tag)
    }

    override fun destroy() {
        legacySpider.destroy()
    }
}
