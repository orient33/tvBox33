package com.github.tvbox.newbox.spider.api

import android.content.Context

/** No-op Spider fallback — mirrors original SpiderNull. Returns empty results instead of crashing. */
class NullSpider : Spider {
    override suspend fun init(context: Context, extend: String) {}
    override suspend fun homeContent(filter: Boolean): String = """{"categories":[],"list":[]}"""
    override suspend fun homeVideoContent(): String = """{"list":[]}"""
    override suspend fun categoryContent(tid: String, pg: String, filter: Boolean, extend: Map<String, String>): String =
        """{"list":[],"page":"1","pagecount":"1","total":"0"}"""
    override suspend fun detailContent(ids: List<String>): String = """{"list":[]}"""
    override suspend fun searchContent(key: String, quick: Boolean, pg: String): String = """{"list":[]}"""
    override suspend fun playerContent(flag: String, id: String, vipFlags: List<String>): String =
        """{"url":"","parse":0}"""
    override suspend fun proxyLocal(params: Map<String, String>): Array<Any?> = emptyArray()
    override suspend fun proxy(params: Map<String, String>): Array<Any?> = emptyArray()
    override fun cancelByTag(tag: String) {}
    override fun destroy() {}
}
