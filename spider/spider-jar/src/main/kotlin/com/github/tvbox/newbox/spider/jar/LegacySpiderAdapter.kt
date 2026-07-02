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
        try {
            val appClass = Class.forName("com.github.tvbox.osc.base.App")
            val getInstance = appClass.getDeclaredMethod("getInstance")
            val app = getInstance.invoke(null)
            Log.d(TAG, "homeContent: App.getInstance()=$app class=${app?.javaClass?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "homeContent: App.getInstance() reflection failed", e)
        }
        try {
            val initClass = Class.forName("com.github.catvod.spider.Init")
            val contextMethod = initClass.getDeclaredMethod("context")
            val ctx = contextMethod.invoke(null)
            Log.d(TAG, "homeContent: Init.context()=$ctx class=${ctx?.javaClass?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "homeContent: Init.context() reflection failed", e)
        }
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
        return legacySpider.detailContent(ids)
    }

    override suspend fun searchContent(key: String, quick: Boolean, pg: String): String {
        return legacySpider.searchContent(key, quick, pg)
    }

    override suspend fun playerContent(
        flag: String,
        id: String,
        vipFlags: List<String>,
    ): String {
        return legacySpider.playerContent(flag, id, vipFlags)
    }

    override suspend fun proxyLocal(params: Map<String, String>): String {
        return legacySpider.proxyLocal(HashMap(params))
    }

    override suspend fun proxy(params: Map<String, String>): String {
        return legacySpider.proxy(HashMap(params))
    }

    override fun cancelByTag(tag: String) {
        legacySpider.cancelByTag(tag)
    }

    override fun destroy() {
        legacySpider.destroy()
    }
}
