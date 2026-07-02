package com.github.tvbox.newbox.server

import android.util.Log
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
    }

    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
        Log.d(TAG, "SpiderProxyServer started on port $listeningPort")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val params = session.parms.toMutableMap()
        params.putAll(session.headers)
        params["request-headers"] = session.headers.entries.joinToString(",") { "${it.key}:${it.value}" }

        Log.d(TAG, "REQUEST: ${session.method} $uri do=${params["do"]} source=${params["source"]}")

        return when {
            uri == "/proxy" -> handleProxy(params)
            uri == "/dns-query" -> handleDnsQuery(params)
            else -> {
                Log.w(TAG, "UNHANDLED: ${session.method} $uri")
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

        val sourceKey = params["source"]
        return runBlocking {
            if (sourceKey != null) {
                tryProxyWithSource(params, sourceKey)
            } else {
                tryProxyAllSources(params)
            }
        }
    }

    private suspend fun tryProxyWithSource(params: Map<String, String>, sourceKey: String): Response {
        val source = subscriptionRepository.sources.first()
            .find { it.key == sourceKey }
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Source not found: $sourceKey")

        return try {
            val spider = source.toSpider()
            val result = spider.proxyLocal(params)
            buildProxyResponse(result)
        } catch (e: Exception) {
            Log.e(TAG, "proxy error for source=$sourceKey: ${e.message}", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Proxy error")
        }
    }

    private suspend fun tryProxyAllSources(params: Map<String, String>): Response {
        val sources = subscriptionRepository.sources.first()
        for (source in sources) {
            try {
                val spider = source.toSpider()
                val result = spider.proxyLocal(params)
                if (result.isNotEmpty()) {
                    return buildProxyResponse(result)
                }
            } catch (_: Exception) { }
        }
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No source handled proxy")
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

    private suspend fun SourceConfig.toSpider() = spiderFactory
        .createLoader(type.toSpiderType())
        .load(SpiderSourceConfig(
            key = key, name = name, api = api,
            type = type.code, ext = ext ?: "", jar = jar ?: "",
            spider = spider, playerUrl = playerUrl, playerType = playerType,
        ))

    private fun handleDnsQuery(params: Map<String, String>): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/dns-message", ByteArrayInputStream(ByteArray(0)), 0)
    }
}
