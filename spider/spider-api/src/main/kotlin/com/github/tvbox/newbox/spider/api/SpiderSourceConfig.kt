package com.github.tvbox.newbox.spider.api

import kotlinx.serialization.Serializable

@Serializable
data class SpiderSourceConfig(
    val key: String,
    val name: String,
    val api: String,
    val type: Int = 0,
    val ext: String? = null,
    val jar: String? = null,
    val spider: String = "",
    val playerUrl: String = "",
    val playerType: Int = 0,
)

enum class SourceType(val code: Int) {
    JAR(0),       // type=0: csp_* class, needs DexClassLoader + .jar
    HTTP_API(1),  // type=1: Standard CMS HTTP API (api.php/provide/vod/)
    SPIDER(3),    // type=3: JAR+JS hybrid spider (csp_* or drpy2)
    T4(4);        // type=4: Server-side T4 spider

    companion object {
        fun fromCode(code: Int): SourceType = when (code) {
            0 -> JAR
            1 -> HTTP_API
            3 -> SPIDER
            4 -> T4
            else -> JAR
        }

        fun fromApi(api: String): SourceType = when {
            api.endsWith(".js") || api.contains(".js?") -> SPIDER
            api.endsWith(".py") || api.contains(".py?") -> SPIDER
            api.startsWith("http://") || api.startsWith("https://") -> HTTP_API
            else -> JAR
        }
    }
}
