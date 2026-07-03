package com.github.tvbox.newbox.spider.jar

import android.app.Application
import android.content.Context

import android.util.Log
import com.github.tvbox.newbox.spider.api.Spider
import com.github.tvbox.newbox.spider.api.SpiderLoadException
import com.github.tvbox.newbox.spider.api.SpiderLoader
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import com.github.tvbox.newbox.spider.api.SourceType
import com.github.tvbox.newbox.spider.api.NullSpider
import dalvik.system.DexClassLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class JarSpiderLoader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) : SpiderLoader {

    companion object { private const val TAG = "NewBox-JarSpi" }

    private val classLoaders = ConcurrentHashMap<String, DexClassLoader>()
    private val spiders = ConcurrentHashMap<String, Spider>()
    private val initLock = Any()

    private val app: Application by lazy {
        try {
            val c = Class.forName("com.github.tvbox.osc.base.App")
            c.getDeclaredMethod("getInstance").invoke(null) as Application
        } catch (e: Throwable) {
            context.applicationContext as Application
        }
    }

    fun clearCache() {
        spiders.clear()
        classLoaders.clear()
    }

    override fun isSupported(type: SourceType): Boolean =
        type == SourceType.JAR || type == SourceType.SPIDER

    override suspend fun load(config: SpiderSourceConfig): Spider {
        com.github.catvod.Init.set(app)
        if (config.api.isJsApi() || config.api.isPyApi()) {
            throw SpiderLoadException("非JAR源不能由JarSpiderLoader加载: ${config.api}")
        }
        spiders[config.key]?.let {
            Log.d(TAG, "load: cache hit key=${config.key}")
            return it
        }

        val clsKey = extractClassName(config.api)
        val jarKey = resolveJarKey(config)
        Log.d(TAG, "load: key=${config.key} clsKey=$clsKey jarKey.key=${jarKey.key} jarUrl=${jarKey.url.take(60)}")

        val classLoader = getOrCreateClassLoader(jarKey, config)
        if (classLoader == null) {
            throw SpiderLoadException("无法加载JAR: ${jarKey.url.take(50)}")
        }

        val spider = try {
            val legacy = classLoader
                .loadClass("com.github.catvod.spider.$clsKey")
                .getDeclaredConstructor()
                .newInstance() as? com.github.catvod.crawler.Spider
            if (legacy == null) return NullSpider().also { Log.w(TAG, "load: class is not Spider key=${config.key} clsKey=$clsKey") }

            val adapted = LegacySpiderAdapter(legacy)
            val ext = config.ext ?: ""
            adapted.init(app, ext)
            Log.d(TAG, "load: success key=${config.key} clsKey=$clsKey")
            adapted
        } catch (e: SpiderLoadException) {
            throw e
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "load: class not found key=${config.key} clsKey=$clsKey")
            NullSpider()
        } catch (e: Throwable) {
            val cause = e.cause ?: e
            Log.e(TAG, "load: failed key=${config.key} clsKey=$clsKey: ${cause.message}", e)
            NullSpider()
        }

        spiders[config.key] = spider
        return spider
    }

    private fun resolveJarKey(config: SpiderSourceConfig): JarKey {
        val rawJar = config.jar?.takeIf { it.isNotBlank() }
            ?: config.spider?.takeIf { it.isNotBlank() }
            ?: return JarKey("main", "", "")
        val parts = rawJar.split(";md5;")
        val jarUrl = parts[0].removePrefix("img+")
        val md5 = parts.getOrElse(1) { "" }.trim()
        val key = if (rawJar == config.jar && rawJar.isNotBlank()) md5Of(jarUrl) else "main"
        val isImgJar = rawJar.startsWith("img+")
        return JarKey(key, jarUrl, md5, isImgJar)
    }

    private fun getOrCreateClassLoader(jarKey: JarKey, config: SpiderSourceConfig): DexClassLoader? {
        classLoaders[jarKey.key]?.let { return it }

        if (jarKey.key == "main") {
            if (jarKey.url.isBlank()) {
                Log.w(TAG, "getOrCreateClassLoader: main jar URL is blank")
                return null
            }
            val jarFile = downloadJar(jarKey.url, jarKey.md5, jarKey.isImgJar)
            if (jarFile == null) {
                Log.e(TAG, "getOrCreateClassLoader: downloadJar returned null for ${jarKey.url}")
                return null
            }
            Log.d(TAG, "getOrCreateClassLoader: jar downloaded to ${jarFile.absolutePath} size=${jarFile.length()}")
            return createDexClassLoader(jarFile)?.also { classLoaders["main"] = it }
        }

        val jarFile = downloadJar(jarKey.url, jarKey.md5, jarKey.isImgJar)
        if (jarFile == null) {
            Log.e(TAG, "getOrCreateClassLoader: downloadJar returned null for ${jarKey.url}")
            return null
        }
        return createDexClassLoader(jarFile)?.also { classLoaders[jarKey.key] = it }
    }

    private fun createDexClassLoader(jarFile: File): DexClassLoader? {
        if (!jarFile.exists()) return null
        val cacheDir = File(app.cacheDir, "catvod_csp").also { it.mkdirs() }
        synchronized(initLock) {
            val classLoader = try {
                DexClassLoader(
                    jarFile.absolutePath,
                    cacheDir.absolutePath,
                    null,
                    app.classLoader,
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "createDexClassLoader: SecurityException, trying fallback")
                return tryFallbackClassLoader(jarFile, cacheDir)
            }
            if (classLoader != null) {
                initJarSpider(classLoader)
            }
            return classLoader
        }
    }

    /**
     * Match original JarLoader: retry Init.init(app) up to 5 times with 200ms sleep.
     * The JAR's Init.init() internally calls DexNative.getLoader() which creates the
     * dynamic DEX (wexshinidie.guard) and initializes InitOrigin. The retry handles
     * cases where the first attempt fails due to async dex2oat compilation.
     */
    private fun initJarSpider(classLoader: DexClassLoader) {
        var success = false
        repeat(5) { attempt ->
            if (success) return@repeat
            try {
                val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
                val initMethod = initClass.getMethod("init", Context::class.java)
                initMethod.invoke(null, app)
                Log.d(TAG, "initJarSpider: Init.init(app) OK on attempt ${attempt + 1}")
                success = true
            } catch (e: Throwable) {
                val cause = e.cause ?: e
                Log.w(TAG, "initJarSpider: attempt ${attempt + 1} failed: ${cause.javaClass.simpleName}: ${cause.message}")
                if (attempt < 4) {
                    try { Thread.sleep(200) } catch (_: InterruptedException) {}
                }
            }
        }
        if (!success) {
            Log.e(TAG, "initJarSpider: Init.init(app) failed after 5 attempts")
        }

        tryInitOriginContext(classLoader)


        com.github.catvod.Init.set(app)
    }

    /**
     * The Guard spider's native code (DexNative.getLoader) creates a dynamic DEX
     * (wexshinidie.guard) and calls InitOrigin.init() via JNI — but passes null Context.
     * This causes NPE when spider code calls context.getSharedPreferences().
     * Fix: Get the dynamic DEX classloader from Init.loader(), then manually call
     * InitOrigin.init() with our real Application context.
     */
    private fun tryInitOriginContext(classLoader: DexClassLoader) {
        try {
            val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
            val loaderMethod = initClass.getMethod("loader")
            val dynamicLoader = loaderMethod.invoke(null) as? ClassLoader
            if (dynamicLoader == null) {
                Log.w(TAG, "tryInitOriginContext: Init.loader() returned null")
                return
            }
            Log.d(TAG, "tryInitOriginContext: dynamicLoader=$dynamicLoader")

            val initOriginClass = dynamicLoader.loadClass("com.github.catvod.spider.InitOrigin")
            Log.d(TAG, "tryInitOriginContext: found InitOrigin class: $initOriginClass")

            try {
                val initMethod = initOriginClass.getDeclaredMethod("init", Context::class.java)
                initMethod.invoke(null, app)
                Log.d(TAG, "tryInitOriginContext: InitOrigin.init(app) succeeded")
            } catch (e: NoSuchMethodException) {
                Log.w(TAG, "tryInitOriginContext: no init(Context) method, trying fields")
                injectContextViaFields(initOriginClass)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                Log.w(TAG, "tryInitOriginContext: init(Context) threw ${e.targetException.javaClass.simpleName}: ${e.targetException.message}, trying field injection")
                injectContextViaFields(initOriginClass)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "tryInitOriginContext: failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * If InitOrigin.init(Context) doesn't exist, try to find a static Context field
     * and set it directly. The obfuscated code stores Context somewhere — we try
     * common patterns (static field of type Context/Application).
     */
    private fun injectContextViaFields(initOriginClass: Class<*>) {
        for (field in initOriginClass.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                val fieldType = field.type
                if (Context::class.java.isAssignableFrom(fieldType)) {
                    try {
                        field.isAccessible = true
                        val oldValue = field.get(null)
                        if (oldValue == null) {
                            field.set(null, app)
                            Log.d(TAG, "injectContextViaFields: set ${field.name} = app (was null)")
                        } else {
                            Log.d(TAG, "injectContextViaFields: ${field.name} already set to $oldValue")
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "injectContextViaFields: failed to set ${field.name}: ${e.message}")
                    }
                }
            }
        }
    }

    private fun tryFallbackClassLoader(jarFile: File, cacheDir: File): DexClassLoader? {
        val roCopy = File(app.filesDir, "ro_${jarFile.name}")
        jarFile.inputStream().use { input -> roCopy.outputStream().use { output -> input.copyTo(output) } }
        roCopy.setReadOnly()
        return try {
            DexClassLoader(roCopy.absolutePath, cacheDir.absolutePath, null, app.classLoader)
        } catch (e: Exception) {
            Log.e(TAG, "tryFallbackClassLoader: also failed: ${e.message}")
            null
        }
    }

    private fun downloadJar(url: String, md5: String, isImgJar: Boolean): File? {
        val jarCacheDir = File(app.filesDir, "spider_jars").also { it.mkdirs() }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val local = File(url)
            return if (local.exists()) copyToReadOnly(local, File(jarCacheDir, local.name)) else null
        }
        val jarFile = File(jarCacheDir, "${md5Of(url)}.jar")
        if (jarFile.exists() && jarFile.isZipJar()) return jarFile
        if (jarFile.exists()) jarFile.delete()
        return try {
            val request = Request.Builder().url(url).tvBoxJarHeaders().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val tmpFile = File(jarCacheDir, "${md5Of(url)}.tmp")
                tmpFile.parentFile?.mkdirs()
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
            Log.e(TAG, "downloadJar: failed url=$url: ${e.message}")
            null
        }
    }

    private fun copyToReadOnly(src: File, dest: File): File {
        if (!dest.exists() || src.lastModified() > dest.lastModified()) {
            src.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        }
        dest.setReadOnly()
        return dest
    }

    private fun extractClassName(api: String): String {
        val trimmed = api.trim().trimEnd('/')
        val lastSegment = trimmed.substringAfterLast('/')
        return lastSegment.removePrefix("csp_").substringBefore(".jar").substringBefore("?")
    }

    private fun String.isJsApi(): Boolean = endsWith(".js") || contains(".js?")
    private fun String.isPyApi(): Boolean = endsWith(".py") || contains(".py?")

    private fun md5Of(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class JarKey(val key: String, val url: String, val md5: String, val isImgJar: Boolean = false)
}
