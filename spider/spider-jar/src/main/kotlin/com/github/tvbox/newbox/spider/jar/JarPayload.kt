package com.github.tvbox.newbox.spider.jar

import android.util.Base64
import okhttp3.Request
import java.io.File

private val imgPayloadMarker = Regex("[A-Za-z0-9]{8}\\*\\*")
private val zipMagic = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)

internal fun extractImgJarPayload(content: String): ByteArray {
    val marker = imgPayloadMarker.find(content)
    if (marker != null) {
        val payload = content.substring(marker.range.last + 1).trim()
        return Base64.decode(payload, Base64.DEFAULT)
    }

    val dataUri = content.substringAfter("base64,", missingDelimiterValue = "")
        .substringBefore("\"")
        .substringBefore("'")
        .trim()
    return if (dataUri.isNotEmpty()) Base64.decode(dataUri, Base64.DEFAULT) else ByteArray(0)
}

internal fun File.isZipJar(): Boolean {
    if (!exists() || length() < zipMagic.size) return false
    val header = ByteArray(zipMagic.size)
    inputStream().use { input ->
        if (input.read(header) != zipMagic.size) return false
    }
    return header.contentEquals(zipMagic)
}

internal fun Request.Builder.tvBoxJarHeaders(): Request.Builder =
    header("User-Agent", "okhttp/3.15")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
