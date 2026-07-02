package com.github.tvbox.newbox.domain

data class SourceConfig(
    val key: String,
    val name: String,
    val api: String,
    val type: SourceType,
    val searchable: Boolean = true,
    val quickSearch: Boolean = false,
    val filterable: Boolean = false,
    val playerUrl: String = "",
    val ext: String? = null,
    val jar: String? = null,
    val spider: String = "",
    val categories: List<String> = emptyList(),
    val playerType: Int = 0,
    val clickSelector: String? = null,
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
    }

    fun toSpiderType(): com.github.tvbox.newbox.spider.api.SourceType =
        com.github.tvbox.newbox.spider.api.SourceType.fromCode(code)
}
