package com.github.tvbox.newbox.server

import com.github.tvbox.osc.util.Logger
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream

class SpiderProxyServer(
    port: Int,
    private val spiderFactory: SpiderFactory,
    private val subscriptionRepository: SubscriptionRepository,
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "NewBox-Proxy"
        const val DEFAULT_PORT = 8964

        @Volatile
        @JvmStatic
        var activePort: Int = DEFAULT_PORT
            private set
    }

    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
        SpiderProxyServer.activePort = getListeningPort()
        Logger.d(TAG, "SpiderProxyServer started on port ${SpiderProxyServer.activePort}")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val params = session.parms.toMutableMap()
        params.putAll(session.headers)
        params["request-headers"] = session.headers.entries.joinToString(",") { "${it.key}:${it.value}" }

        return when {
            uri == "/proxy" -> handleProxy(params)
            uri == "/dns-query" -> handleDnsQuery(params)
            else -> {
                Logger.w(TAG, "UNHANDLED: ${session.method} $uri")
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }
    }

    private fun handleProxy(params: Map<String, String>): Response {
        val doAction = params["do"]
        if (doAction == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing 'do' param")
        }

        if (doAction == "ck") {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
        }

        return runBlocking {
            tryProxyViaFactory(params)
        }
    }

    private suspend fun tryProxyViaFactory(params: Map<String, String>): Response {
        for (sourceType in listOf(
            com.github.tvbox.newbox.spider.api.SourceType.JAR,
            com.github.tvbox.newbox.spider.api.SourceType.SPIDER,
            com.github.tvbox.newbox.spider.api.SourceType.JS,
        )) {
            try {
                val loader = spiderFactory.createLoader(sourceType)
                val result = loader.proxyInvoke(params)
                if (result != null && result.isNotEmpty()) {
                    return buildProxyResponse(result)
                }
            } catch (e: Exception) {
                Logger.w(TAG, "tryProxyViaFactory: $sourceType failed: ${e.message}")
            }
        }
        Logger.e(TAG, "tryProxyViaFactory: no loader handled proxy")
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No loader handled proxy")
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildProxyResponse(result: Array<Any?>): Response {
        if (result.isEmpty()) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
        }

        val code = (result[0] as? Number)?.toInt() ?: 200
        val mime = result[1] as? String ?: MIME_PLAINTEXT
        val stream = result[2] as? InputStream ?: ByteArrayInputStream(ByteArray(0))

        val status = Response.Status.lookup(code)
        val response = newChunkedResponse(status, mime, stream)

        if (result.size > 3 && result[3] != null) {
            try {
                val headers = result[3] as? Map<String, String>
                headers?.forEach { (k, v) -> response.addHeader(k, v) }
            } catch (_: Exception) { }
        }

        return response
    }

    private fun handleDnsQuery(params: Map<String, String>): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/dns-message", ByteArrayInputStream(ByteArray(0)), 0)
    }
}
