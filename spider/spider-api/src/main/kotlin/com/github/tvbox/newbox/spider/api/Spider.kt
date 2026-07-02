package com.github.tvbox.newbox.spider.api

import android.content.Context

interface Spider {
    suspend fun init(context: Context, extend: String) {}
    suspend fun homeContent(filter: Boolean): String
    suspend fun homeVideoContent(): String = ""
    suspend fun categoryContent(tid: String, pg: String, filter: Boolean, extend: Map<String, String>): String
    suspend fun detailContent(ids: List<String>): String
    suspend fun searchContent(key: String, quick: Boolean, pg: String = "1"): String
    suspend fun playerContent(flag: String, id: String, vipFlags: List<String>): String
    suspend fun proxyLocal(params: Map<String, String>): String = ""
    suspend fun proxy(params: Map<String, String>): String = ""
    fun cancelByTag(tag: String) {}
    fun destroy() {}
}
