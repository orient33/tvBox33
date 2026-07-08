package com.github.tvbox.newbox.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import com.github.tvbox.osc.util.Logger
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class VideoSniffer(private val context: Context) {

    companion object {
        private const val TAG = "NewBox-Sniffer"
        private const val DEFAULT_TIMEOUT_MS = 20_000L

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
    }

    suspend fun sniff(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            var resumed = false
            val foundUrls = ConcurrentHashMap.newKeySet<String>()

            val webView = WebView(context)

            try {
                configWebView(webView, headers)

                webView.webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: android.net.http.SslError?,
                    ) {
                        handler?.proceed()
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        Logger.d(TAG, "onPageStarted: $url")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        Logger.d(TAG, "onPageFinished: $url")
                    }

                    @SuppressLint("NewApi")
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        if (reqUrl.endsWith("/favicon.ico")) return null

                        if (isVideoFormat(reqUrl) && !foundUrls.contains(reqUrl)) {
                            foundUrls.add(reqUrl)
                            Logger.d(TAG, "FOUND video url: $reqUrl")
                            if (!resumed) {
                                resumed = true
                                val cookie = CookieManager.getInstance().getCookie(reqUrl)
                                val finalHeaders = headers.toMutableMap()
                                if (!cookie.isNullOrBlank()) {
                                    finalHeaders["Cookie"] = cookie
                                }
                                if (cont.isActive) {
                                    cont.resume(reqUrl)
                                }
                            }
                        }
                        return null
                    }
                }

                if (headers.isNotEmpty()) {
                    val extraHeaders = headers.filterKeys {
                        !it.equals("user-agent", ignoreCase = true)
                    }
                    webView.loadUrl(url, extraHeaders)
                } else {
                    webView.loadUrl(url)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "sniff error", e)
                if (!resumed && cont.isActive) {
                    resumed = true
                    cont.resume(null)
                }
            }

            cont.invokeOnCancellation {
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.removeAllViews()
                    webView.destroy()
                } catch (_: Exception) {}
            }
        }
    }

    private fun isVideoFormat(url: String): Boolean {
        if (url.contains("url=http") || url.contains(".html")) return false
        return VIDEO_REGEX.containsMatchIn(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configWebView(webView: WebView, headers: Map<String, String>) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.blockNetworkImage = true
        settings.useWideViewPort = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.defaultTextEncodingName = "utf-8"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        headers["User-Agent"]?.let { settings.userAgentString = it }

        webView.webChromeClient = WebChromeClient()
        webView.setBackgroundColor(0xFF000000.toInt())
        webView.isFocusable = false
        webView.isFocusableInTouchMode = false
    }
}
