package com.github.tvbox.newbox.common

import android.util.Base64

object ConfigDecoder {

    private val eightCharStarPattern = Regex("[A-Za-z0]{8}\\*\\*")
    private const val PK_SEPARATOR = ";pk;"

    data class ParsedUrl(
        val configUrl: String,
        val configKey: String?,
    )

    fun parseSubscriptionUrl(rawUrl: String): ParsedUrl {
        val trimmed = rawUrl.trim()
        if (trimmed.contains(PK_SEPARATOR)) {
            val parts = trimmed.split(PK_SEPARATOR, limit = 2)
            val key = parts[1]
            val url = when {
                parts[0].startsWith("clan") -> clanToAddress(parts[0])
                parts[0].startsWith("http") -> parts[0]
                else -> "http://${parts[0]}"
            }
            return ParsedUrl(url, key)
        }
        val url = when {
            trimmed.startsWith("clan") -> clanToAddress(trimmed)
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "http://$trimmed"
        }
        return ParsedUrl(url, null)
    }

    /**
     * Port of ApiConfig.FindResult: decrypt/decode non-standard subscription content.
     * 1. If already valid JSON → return as-is
     * 2. Strip 8-char** Base64 prefix
     * 3. Handle 2423/2324 AES-CBC encrypted format
     * 4. AES-ECB decrypt with configKey
     * 5. Strip // comments and trailing commas
     */
    fun decode(raw: String, configKey: String? = null): String {
        var content = sanitizeJson(raw)

        if (AES.isJson(content)) return content

        val match = eightCharStarPattern.find(content)
        if (match != null) {
            content = content.substring(content.indexOf(match.value) + 10)
            content = String(Base64.decode(content, Base64.DEFAULT))
        }

        if (content.startsWith("2423")) {
            val data = content.substring(content.indexOf("2324") + 4, content.length - 26)
            val decoded = String(AES.toBytes(content)).lowercase()
            val key = AES.rightPadding(
                decoded.substring(decoded.indexOf("\$#") + 2, decoded.indexOf("#$")), "0", 16
            )
            val iv = AES.rightPadding(decoded.substring(decoded.length - 13), "0", 16)
            val decrypted = AES.cbc(data, key, iv)
            if (decrypted != null) return sanitizeJson(decrypted)
        } else if (configKey != null) {
            val decrypted = AES.ecb(content, configKey)
            if (decrypted != null) return sanitizeJson(decrypted)
        }

        return sanitizeJson(content)
    }

    /**
     * Post-process decoded content:
     * 1. Replace clan:// URLs with HTTP equivalents
     * 2. Replace relative ./ paths with absolute URLs
     */
    fun fixPaths(url: String, content: String): String {
        var result = content
        if (url.startsWith("clan")) {
            val fix = clanToAddress(url).substringBefore("/file/") + "/file/"
            result = result.replace("clan://", fix)
        }
        if (result.contains("\"./")) {
            val baseUrl = when {
                url.startsWith("clan") -> clanToAddress(url)
                !url.startsWith("http") -> "http://$url"
                else -> url
            }
            result = result.replace("./", baseUrl.substringBeforeLast("/") + "/")
        }
        return result
    }

    private fun clanToAddress(lanLink: String): String {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", "http://127.0.0.1:9978/file/")
        }
        val link = lanLink.substring(7)
        val end = link.indexOf('/')
        return "http://${link.substring(0, end)}/file/${link.substring(end + 1)}"
    }

    private fun sanitizeJson(raw: String): String {
        val sb = StringBuilder(raw.length)
        var inString = false
        var escape = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (escape) {
                sb.append(c); escape = false; i++; continue
            }
            if (c == '\\' && inString) {
                sb.append(c); escape = true; i++; continue
            }
            if (c == '"') {
                inString = !inString; sb.append(c); i++; continue
            }
            if (!inString && c == '/' && i + 1 < raw.length && raw[i + 1] == '/') {
                while (i < raw.length && raw[i] != '\n') i++
                continue
            }
            if (!inString && (c == '\r' || c == '\n' || c == '\t')) {
                sb.append(' '); i++; continue
            }
            sb.append(c); i++
        }
        val collapsed = multiSpaceRegex.replace(sb, " ")
        return trailingCommaRegex.replace(collapsed, "$1")
    }

    private val trailingCommaRegex = Regex(""",\s*([}\]])""")
    private val multiSpaceRegex = Regex("""\s{2,}""")
}
