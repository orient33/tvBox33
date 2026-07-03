package com.github.tvbox.newbox.spider.jar

import android.content.Context
import android.util.Log
import com.github.tvbox.newbox.spider.api.Spider
import com.github.tvbox.newbox.spider.api.SpiderLoader
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import com.github.tvbox.newbox.spider.api.SourceType
import com.github.tvbox.osc.util.MD5
import com.github.tvbox.osc.util.js.NewBoxJsSpider
import dalvik.system.DexClassLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class JsSpiderLoader(
    context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) : SpiderLoader {
    private val app = context.applicationContext
    private val spiders = ConcurrentHashMap<String, Spider>()
    private val jsApiClasses = ConcurrentHashMap<String, Class<*>?>()

    companion object { private const val TAG = "NewBox-JsSpi" }

    fun clearCache() {
        spiders.values.forEach { it.destroy() }
        spiders.clear()
        jsApiClasses.clear()
    }

    override fun isSupported(type: SourceType): Boolean = type == SourceType.JS

    override suspend fun load(config: SpiderSourceConfig): Spider {
        com.github.catvod.Init.set(app)
        spiders[config.key]?.let {
            Log.d(TAG, "load: cache hit key=${config.key}")
            return it
        }

        Log.d(TAG, "load: key=${config.key} api=${config.api} jar=${config.jar.orEmpty().take(60)}")
        val jsApiClass = config.jar
            ?.takeIf { it.isNotBlank() }
            ?.let { loadJsApiClass(it) }
        val spider = LegacySpiderAdapter(NewBoxJsSpider(config.key, config.api, jsApiClass))
        spider.init(app, config.ext.orEmpty())
        spiders[config.key] = spider
        return spider
    }

    private fun loadJsApiClass(rawJar: String): Class<*>? {
        val parts = rawJar.split(";md5;")
        val jarUrl = parts[0].removePrefix("img+")
        val md5 = parts.getOrElse(1) { "" }.trim()
        val key = MD5.string2MD5(jarUrl)
        if (jsApiClasses.containsKey(key)) return jsApiClasses[key]

        val jarFile = downloadJar(jarUrl, md5, rawJar.startsWith("img+")) ?: return null
        return try {
            val cacheDir = File(app.cacheDir, "catvod_jsapi").also { it.mkdirs() }
            val classLoader = DexClassLoader(jarFile.absolutePath, cacheDir.absolutePath, null, app.classLoader)
            val methodClass = classLoader.loadClass("com.github.catvod.js.Method")
            Log.d(TAG, "loadJsApiClass: success jar=$jarUrl")
            jsApiClasses[key] = methodClass
            methodClass
        } catch (e: Throwable) {
            Log.w(TAG, "loadJsApiClass: failed jar=$jarUrl: ${e.message}")
            jsApiClasses[key] = null
            null
        }
    }

    private fun downloadJar(url: String, md5: String, isImgJar: Boolean): File? {
        if (url.isBlank()) return null
        val jarCacheDir = File(app.filesDir, "js_api_jars").also { it.mkdirs() }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val local = File(url)
            return if (local.exists()) local else null
        }
        val jarFile = File(jarCacheDir, "${MD5.encode(url)}.jar")
        if (jarFile.exists() && jarFile.isZipJar() && (md5.isBlank() || MD5.getFileMd5(jarFile).equals(md5, ignoreCase = true))) {
            return jarFile
        }
        if (jarFile.exists()) jarFile.delete()
        return try {
            val request = Request.Builder().url(url).tvBoxJarHeaders().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val tmpFile = File(jarCacheDir, "${MD5.encode(url)}.tmp")
                tmpFile.outputStream().use { output ->
                    if (isImgJar) {
                        val html = body.string()
                        output.write(extractImgJarPayload(html))
                    } else {
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                }
                tmpFile.renameTo(jarFile)
            }
            if (!jarFile.isZipJar()) {
                jarFile.delete()
                return null
            }
            jarFile.setReadOnly()
            jarFile
        } catch (e: Exception) {
            Log.w(TAG, "downloadJar: failed url=$url: ${e.message}")
            null
        }
    }
}
