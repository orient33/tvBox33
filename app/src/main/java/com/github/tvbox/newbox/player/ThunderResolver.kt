package com.github.tvbox.newbox.player

import android.content.Context
import android.text.TextUtils
import com.github.tvbox.osc.util.Logger
import com.github.tvbox.newbox.domain.Episode
import com.github.tvbox.newbox.domain.VodDetail
import com.xunlei.downloadlib.XLDownloadManager
import com.xunlei.downloadlib.XLTaskHelper
import com.xunlei.downloadlib.android.XLUtil
import com.xunlei.downloadlib.parameter.TorrentFileInfo
import java.io.File
import java.util.Locale
import java.util.Random

object ThunderResolver {
    private const val TAG = "NewBox-Thunder"
    private const val TORRENT_PREFIX = "tvbox-torrent:"
    private const val OTHER_PREFIX = "tvbox-oth:"
    private const val MIN_MEDIA_SIZE = 30L * 1024L * 1024L

    private var initialized = false
    private var cacheRoot = ""
    private var currentTask = 0L
    private val torrentFiles = mutableListOf<TorrentFileInfo>()
    private val otherUrls = mutableListOf<String>()

    @Synchronized
    fun expandDetail(context: Context, detail: VodDetail): VodDetail {
        if (detail.seriesMap.values.flatten().none { isSupportUrl(it.url) }) return detail
        init(context.applicationContext)
        stop(clear = true)
        torrentFiles.clear()
        otherUrls.clear()

        val expanded = detail.seriesMap.mapValues { (_, episodes) ->
            episodes.flatMap { episode ->
                if (isSupportUrl(episode.url)) parseEpisode(episode) else listOf(episode)
            }.ifEmpty { episodes }
        }
        return detail.copy(seriesMap = expanded)
    }

    @Synchronized
    fun resolvePlayUrl(context: Context, url: String): String? {
        init(context.applicationContext)
        return when {
            url.startsWith(TORRENT_PREFIX) -> resolveTorrent(url.removePrefix(TORRENT_PREFIX).toIntOrNull() ?: return null)
            url.startsWith(OTHER_PREFIX) -> resolveOther(url.removePrefix(OTHER_PREFIX).toIntOrNull() ?: return null)
            isEd2k(url) || isFtp(url) -> resolveNetworkTask(url)
            else -> null
        }
    }

    fun isSupportUrl(url: String): Boolean = isMagnet(url) || isThunder(url) || isTorrent(url) || isEd2k(url) || isFtp(url)

    fun needsResolve(url: String): Boolean =
        url.startsWith(TORRENT_PREFIX) || url.startsWith(OTHER_PREFIX) || isEd2k(url) || isFtp(url)

    @Synchronized
    fun stop(clear: Boolean) {
        if (currentTask > 0L) {
            runCatching { XLTaskHelper.instance().stopTask(currentTask) }
            currentTask = 0L
        }
        if (clear && cacheRoot.isNotBlank()) {
            runCatching { File(cacheRoot).deleteRecursively() }
            File(cacheRoot).mkdirs()
        }
    }

    private fun init(context: Context) {
        if (initialized) return
        val prefs = context.getSharedPreferences("rand_thunder_id", Context.MODE_PRIVATE)
        val imei = prefs.getString("imei", null) ?: randomImei().also { prefs.edit().putString("imei", it).apply() }
        val mac = prefs.getString("mac", null) ?: randomMac().also { prefs.edit().putString("mac", it).apply() }
        XLUtil.mIMEI = imei
        XLUtil.isGetIMEI = true
        XLUtil.mMAC = mac
        XLUtil.isGetMAC = true
        val cd3 = "cee25055f125a2fde0"
        val base64Decode = "axzNjAwMQ^^yb==0^852^083dbcff^"
        val cd = base64Decode.substring(1) + cd3.substring(0, cd3.length - 1)
        XLTaskHelper.init(context, cd, "21.01.07.800002")
        cacheRoot = File(context.cacheDir, "thunder").absolutePath
        File(cacheRoot).mkdirs()
        initialized = true
    }

    private fun parseEpisode(episode: Episode): List<Episode> {
        val url = normalizeThunderUrl(episode.url)
        return if (isMagnet(url) || isTorrent(url) || isThunder(url)) {
            parseTorrentLikeUrl(url).ifEmpty { listOf(episode) }
        } else if (isEd2k(url) || isFtp(url)) {
            val index = otherUrls.size
            otherUrls += url
            listOf(Episode(name = XLTaskHelper.instance().getFileName(url).ifBlank { episode.name }, url = "$OTHER_PREFIX$index"))
        } else {
            listOf(episode)
        }
    }

    private fun parseTorrentLikeUrl(url: String): List<Episode> {
        val helper = XLTaskHelper.instance()
        val fileName = helper.getFileName(url).ifBlank { "magnet_${System.currentTimeMillis()}.torrent" }
        val cache = File(cacheRoot, fileName)
        currentTask = try {
            if (isMagnet(url)) helper.addMagentTask(url, cacheRoot, fileName) else helper.addThunderTask(url, cacheRoot, fileName)
        } catch (e: Exception) {
            Logger.e(TAG, "add magnet/torrent task failed: ${e.message}", e)
            0L
        }
        if (currentTask <= 0L) return emptyList()

        repeat(100) {
            val taskInfo = runCatching { helper.getTaskInfo(currentTask) }.getOrNull()
            when (taskInfo?.mTaskStatus) {
                2 -> {
                    val torrentInfo = runCatching { helper.getTorrentInfo(cache.absolutePath) }.getOrNull()
                    val files = torrentInfo?.mSubFileInfo.orEmpty()
                    if (files.isNotEmpty()) {
                        val result = files
                            .filter { isMedia(it.mFileName) && it.mFileSize > MIN_MEDIA_SIZE }
                            .map { file ->
                                file.torrentPath = cache.absolutePath
                                val index = torrentFiles.size
                                torrentFiles += file
                                Episode(name = file.mFileName, url = "$TORRENT_PREFIX$index")
                            }
                        if (result.isNotEmpty()) return result
                    }
                }
                3 -> return emptyList()
            }
            Thread.sleep(100)
        }
        return emptyList()
    }

    private fun resolveTorrent(index: Int): String? {
        val info = torrentFiles.getOrNull(index) ?: return null
        stop(clear = false)
        val torrentName = File(info.torrentPath).name
        val cache = File(cacheRoot, torrentName.substringBeforeLast('.', torrentName)).absolutePath
        currentTask = XLTaskHelper.instance().addTorrentTask(info.torrentPath, cache, info.mFileIndex)
        if (currentTask <= 0L) return null
        repeat(30) {
            val taskInfo = runCatching { XLTaskHelper.instance().getBtSubTaskInfo(currentTask, info.mFileIndex).mTaskInfo }.getOrNull()
            when (taskInfo?.mTaskStatus) {
                1, 2, 4 -> return XLTaskHelper.instance().getLoclUrl(File(cache, info.mFileName).absolutePath)
                3 -> return null
            }
            Thread.sleep(1000)
        }
        return null
    }

    private fun resolveOther(index: Int): String? {
        val url = otherUrls.getOrNull(index) ?: return null
        return resolveNetworkTask(url)
    }

    private fun resolveNetworkTask(url: String): String? {
        stop(clear = false)
        val name = XLTaskHelper.instance().getFileName(url)
        val localPath = File(File(cacheRoot, "temp"), name.substringBeforeLast('.', name)).absolutePath
        currentTask = XLTaskHelper.instance().addThunderTask(url, localPath, null)
        if (currentTask <= 0L) return null
        repeat(20) {
            getNetworkPlayUrl(localPath, name)?.let { return it }
            Thread.sleep(1000)
        }
        return null
    }

    private fun getNetworkPlayUrl(localPath: String, name: String): String? =
        if (currentTask > 0L) XLTaskHelper.instance().getLoclUrl(File(localPath, name).absolutePath) else null

    private fun normalizeThunderUrl(url: String): String =
        if (isThunder(url)) XLDownloadManager.getInstance().parserThunderUrl(url) else url

    private fun isMagnet(url: String) = url.lowercase(Locale.ROOT).startsWith("magnet:")
    private fun isThunder(url: String) = url.lowercase(Locale.ROOT).startsWith("thunder")
    private fun isTorrent(url: String) = url.lowercase(Locale.ROOT).substringBefore(';').endsWith(".torrent")
    private fun isEd2k(url: String) = url.lowercase(Locale.ROOT).startsWith("ed2k:")
    private fun isFtp(url: String) = url.lowercase(Locale.ROOT).startsWith("ftp://")

    private fun isMedia(name: String): Boolean {
        if (TextUtils.isEmpty(name)) return false
        val lower = name.lowercase(Locale.ROOT)
        return listOf(".mp4", ".mkv", ".avi", ".rmvb", ".wmv", ".mov", ".flv", ".ts", ".m3u8").any { lower.endsWith(it) }
    }

    private fun randomImei(): String = "86" + randomString("0123456789", 13)
    private fun randomMac(): String = randomString("ABCDEF0123456", 12).uppercase(Locale.ROOT)
    private fun randomString(base: String, length: Int): String {
        val random = Random()
        return buildString { repeat(length) { append(base[random.nextInt(base.length)]) } }
    }
}
