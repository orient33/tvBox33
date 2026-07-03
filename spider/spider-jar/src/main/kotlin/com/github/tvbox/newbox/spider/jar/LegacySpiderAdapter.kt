package com.github.tvbox.newbox.spider.jar

import android.content.Context
import android.util.Log
import com.github.tvbox.newbox.spider.api.Spider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LegacySpiderAdapter(
    private val legacySpider: com.github.catvod.crawler.Spider,
) : Spider {

    companion object { private const val TAG = "NewBox-Legacy" }

    private suspend fun <T> runBlockingCall(block: () -> T): T {
        val future = CompletableFuture.supplyAsync(block)
        return suspendCancellableCoroutine { cont ->
            future.handle { result, error ->
                if (error != null) cont.resumeWithException(error)
                else cont.resume(result)
                null
            }
            cont.invokeOnCancellation { future.cancel(true) }
        }
    }

    override suspend fun init(context: Context, extend: String) {
        runBlockingCall { legacySpider.init(context, extend) }
    }

    override suspend fun homeContent(filter: Boolean): String {
        return runBlockingCall { legacySpider.homeContent(filter) }
    }

    override suspend fun homeVideoContent(): String {
        return runBlockingCall { legacySpider.homeVideoContent() }
    }

    override suspend fun categoryContent(
        tid: String,
        pg: String,
        filter: Boolean,
        extend: Map<String, String>,
    ): String {
        return runBlockingCall {
            legacySpider.categoryContent(tid, pg, filter, HashMap(extend as Map<String, String>))
        }
    }

    override suspend fun detailContent(ids: List<String>): String {
        return try {
            val result = runBlockingCall { legacySpider.detailContent(ids) }
            if (result.isBlank()) {
                Log.w(TAG, "detailContent returned blank for ids=$ids")
                throw IllegalStateException("详情数据为空，该源可能不可用")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "detailContent: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    override suspend fun searchContent(key: String, quick: Boolean, pg: String): String {
        return try {
            runBlockingCall {
                if (pg == "1") {
                    legacySpider.searchContent(key, quick)
                } else {
                    legacySpider.searchContent(key, quick, pg)
                }
            }
        } catch (e: NoSuchMethodError) {
            runBlockingCall { legacySpider.searchContent(key, quick) }
        }
    }

    override suspend fun playerContent(
        flag: String,
        id: String,
        vipFlags: List<String>,
    ): String {
        return runBlockingCall { legacySpider.playerContent(flag, id, vipFlags) }
    }

    override suspend fun proxyLocal(params: Map<String, String>): Array<Any?> {
        return try {
            val result = runBlockingCall { legacySpider.proxyLocal(HashMap(params)) }
            if (result != null && result.isNotEmpty()) result else emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "proxyLocal: exception=${e.message}", e)
            emptyArray()
        }
    }

    override suspend fun proxy(params: Map<String, String>): Array<Any?> {
        return try {
            val result = runBlockingCall { legacySpider.proxy(HashMap(params)) }
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
