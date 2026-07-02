package com.github.tvbox.osc.base

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import com.github.tvbox.newbox.server.SpiderProxyServer
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.osc.server.RemoteServer
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun spiderFactory(): SpiderFactory
        fun subscriptionRepository(): SubscriptionRepository
    }

    private var proxyServer: SpiderProxyServer? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startProxyServer()
    }

    private fun startProxyServer() {
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        val spiderFactory = entryPoint.spiderFactory()
        val subscriptionRepository = entryPoint.subscriptionRepository()
        var port = SpiderProxyServer.DEFAULT_PORT
        while (port < 9999) {
            try {
                val server = SpiderProxyServer(port, spiderFactory, subscriptionRepository)
                server.startServer()
                proxyServer = server
                RemoteServer.serverPort = port
                return
            } catch (_: Exception) {
                port++
            }
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 15; M2102J2SC Build/AP2A.240905.003)")
                    .header("Referer", "https://www.douban.com/")
                    .build()
                chain.proceed(request)
            }
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(client))
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    companion object {
        private var instance: App? = null

        @JvmStatic
        fun getInstance(): App = instance
            ?: throw IllegalStateException("App not initialized")
    }
}
