package com.github.tvbox.newbox.spider.jar

import android.app.Application
import android.content.Context
import android.util.Log
import com.github.tvbox.newbox.spider.api.Spider
import com.github.tvbox.newbox.spider.api.SpiderLoadException
import com.github.tvbox.newbox.spider.api.SpiderLoader
import com.github.tvbox.newbox.spider.api.SpiderSourceConfig
import com.github.tvbox.newbox.spider.api.SourceType
import dalvik.system.DexClassLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class JarSpiderLoader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) : SpiderLoader {

    companion object { private const val TAG = "NewBox-JarSpi" }

    private val classLoaders = ConcurrentHashMap<String, DexClassLoader>()
    private val spiders = ConcurrentHashMap<String, Spider>()

    private val appInstance: Application by lazy {
        try {
            val appClass = Class.forName("com.github.tvbox.osc.base.App")
            val getInstance = appClass.getDeclaredMethod("getInstance")
            getInstance.invoke(null) as Application
        } catch (e: Throwable) {
            Log.w(TAG, "App.getInstance() reflection failed, falling back to context", e)
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
        com.github.catvod.Init.set(appInstance)
        com.github.catvod.spider.Init.init(appInstance)
        com.github.catvod.spider.Init.setLoader(classLoaders["main"])
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
            if (legacy == null) {
                throw SpiderLoadException("类不是Spider: $clsKey")
            }

            val adapted = LegacySpiderAdapter(legacy)
            val ext = config.ext ?: ""
            adapted.init(appInstance, ext)
            Log.d(TAG, "load: success key=${config.key} clsKey=$clsKey")
            adapted
        } catch (e: SpiderLoadException) {
            throw e
        } catch (e: Throwable) {
            val cause = e.cause ?: e
            Log.e(TAG, "load: failed key=${config.key} clsKey=$clsKey: ${cause.message}", e)
            throw SpiderLoadException("源 ${config.name} 加载失败: ${cause.message ?: e.javaClass.simpleName}", e)
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
        val dexOutputDir = File(appInstance.filesDir, "dex_output").also { it.mkdirs() }
        val nativeLibDir = extractNativeLibs(jarFile)
        val filteredParent = FilteringClassLoader(
            appInstance.classLoader,
            setOf("com.github.catvod.spider.Init")
        )
        val classLoader = try {
            DexClassLoader(
                jarFile.absolutePath,
                dexOutputDir.absolutePath,
                nativeLibDir.absolutePath,
                filteredParent,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "createDexClassLoader: SecurityException for ${jarFile.absolutePath}: ${e.message}")
            tryFallbackClassLoader(jarFile, dexOutputDir, nativeLibDir, filteredParent)
        }
        if (classLoader != null) {
            initJarSpider(classLoader)
        }
        return classLoader
    }

    private fun initJarSpider(classLoader: DexClassLoader) {
        initJarClass(classLoader, "com.github.catvod.spider.Init", "init")
        initJarClass(classLoader, "com.github.catvod.Init", "set")
        initDynamicDexClasses(classLoader)
    }

    private fun initDynamicDexClasses(classLoader: DexClassLoader) {
        val dynamicLoader = try {
            val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
            val loaderMethod = initClass.getMethod("loader")
            loaderMethod.invoke(null) as? DexClassLoader
        } catch (e: Throwable) {
            Log.w(TAG, "initDynamicDexClasses: failed to get Init.loader(): ${e.message}")
            null
        }
        if (dynamicLoader == null) {
            Log.w(TAG, "initDynamicDexClasses: Init.loader() returned null, skipping")
            return
        }
        Log.d(TAG, "initDynamicDexClasses: got dynamic DexClassLoader ${dynamicLoader.javaClass.name}")
        initJarClass(dynamicLoader, "com.github.catvod.spider.Init", "init")
        initJarClass(dynamicLoader, "com.github.catvod.Init", "set")
    }

    private fun initJarClass(classLoader: DexClassLoader, className: String, methodName: String) {
        try {
            val initClass = classLoader.loadClass(className)
            val initMethod = initClass.getMethod(methodName, android.content.Context::class.java)
            initMethod.invoke(null, appInstance)
            Log.d(TAG, "initJarClass: $className.$methodName(context) called successfully")
        } catch (e: NoSuchMethodException) {
            Log.d(TAG, "initJarClass: no $className.$methodName(Context) in JAR, skipping")
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "initJarClass: no $className in JAR, skipping")
        } catch (e: Throwable) {
            Log.w(TAG, "initJarClass: $className.$methodName() failed: ${e.message}")
        }
    }

    private fun extractNativeLibs(jarFile: File): File {
        val libDir = File(appInstance.filesDir, "spider_libs/${jarFile.nameWithoutExtension}").also { it.mkdirs() }
        val soDir = File(appInstance.filesDir, "so").also { it.mkdirs() }
        try {
            val zip = ZipFile(jarFile)
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if ((name.startsWith("assets/") || name.startsWith("lib/")) &&
                    (name.endsWith(".so") || name.endsWith(".dat"))) {
                    val baseName = name.substringAfterLast("/")
                    val outFile = File(libDir, baseName)
                    if (!outFile.exists()) {
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        outFile.setExecutable(true, false)
                        outFile.setReadable(true, false)
                        Log.d(TAG, "extractNativeLibs: extracted $baseName to ${libDir.name}")
                    }
                    val soName = if (baseName.startsWith("lib")) baseName else "lib$baseName"
                    val soFile = File(soDir, soName)
                    if (!soFile.exists()) {
                        zip.getInputStream(entry).use { input ->
                            soFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        soFile.setExecutable(true, false)
                        soFile.setReadable(true, false)
                        Log.d(TAG, "extractNativeLibs: extracted $soName to so/")
                    }
                }
            }
            zip.close()
        } catch (e: Exception) {
            Log.w(TAG, "extractNativeLibs: failed: ${e.message}")
        }
        return libDir
    }

    private fun tryFallbackClassLoader(jarFile: File, dexOutputDir: File, nativeLibDir: File, parent: ClassLoader): DexClassLoader? {
        val roCopy = File(appInstance.filesDir, "ro_${jarFile.name}")
        jarFile.inputStream().use { input -> roCopy.outputStream().use { output -> input.copyTo(output) } }
        roCopy.setReadOnly()
        return try {
            DexClassLoader(
                roCopy.absolutePath,
                dexOutputDir.absolutePath,
                nativeLibDir.absolutePath,
                parent,
            )
        } catch (e: Exception) {
            Log.e(TAG, "tryFallbackClassLoader: also failed: ${e.message}")
            null
        }
    }

    private fun downloadJar(url: String, md5: String, isImgJar: Boolean): File? {
        val jarCacheDir = File(appInstance.filesDir, "spider_jars").also { it.mkdirs() }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val local = File(url)
            return if (local.exists()) copyToReadOnly(local, File(jarCacheDir, local.name)) else null
        }

        val jarFile = File(jarCacheDir, "${md5Of(url)}.jar")
        if (jarFile.exists()) return jarFile

        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val tmpFile = File(jarCacheDir, "${md5Of(url)}.tmp")
                tmpFile.parentFile?.mkdirs()
                tmpFile.outputStream().use { output ->
                    if (isImgJar) {
                        val html = body.string()
                        val imgBytes = extractJarFromImg(html)
                        output.write(imgBytes)
                    } else {
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                }
                tmpFile.renameTo(jarFile)
            }
            jarFile.setReadOnly()
            jarFile
        } catch (_: Exception) {
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
        return lastSegment
            .removePrefix("csp_")
            .substringBefore(".jar")
            .substringBefore("?")
    }

    private fun extractJarFromImg(html: String): ByteArray {
        val base64 = html.substringAfter("base64,")
            .substringBefore("\"")
            .substringBefore("'")
        return android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
    }

    private fun md5Of(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun fileMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class JarKey(
        val key: String,
        val url: String,
        val md5: String,
        val isImgJar: Boolean = false,
    )
}
