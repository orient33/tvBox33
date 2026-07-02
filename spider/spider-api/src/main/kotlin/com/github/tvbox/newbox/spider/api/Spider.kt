package com.github.tvbox.newbox.spider.api

import android.content.Context
import java.io.InputStream

interface Spider {
    suspend fun init(context: Context, extend: String) {}
    suspend fun homeContent(filter: Boolean): String
    suspend fun homeVideoContent(): String = ""
    suspend fun categoryContent(tid: String, pg: String, filter: Boolean, extend: Map<String, String>): String
    suspend fun detailContent(ids: List<String>): String
    suspend fun searchContent(key: String, quick: Boolean, pg: String = "1"): String
    suspend fun playerContent(flag: String, id: String, vipFlags: List<String>): String

    /**
     * Proxy local request. Returns Object[] matching original TVBox Spider convention:
     * [0] = Int (HTTP status code)
     * [1] = String (MIME type)
     * [2] = InputStream? (response body)
     * [3] = Map<String, String>? (extra headers, optional)
     */
    suspend fun proxyLocal(params: Map<String, String>): Array<Any?> = emptyArray()
    suspend fun proxy(params: Map<String, String>): Array<Any?> = proxyLocal(params)

    fun cancelByTag(tag: String) {}
    fun destroy() {}
}
