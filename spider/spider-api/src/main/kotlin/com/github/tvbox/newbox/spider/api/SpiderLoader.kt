package com.github.tvbox.newbox.spider.api

interface SpiderLoader {
    suspend fun load(config: SpiderSourceConfig): Spider
    fun isSupported(type: SourceType): Boolean
    fun proxyInvoke(params: Map<String, String>): Array<Any?>? = null
}

interface SpiderFactory {
    fun createLoader(type: SourceType): SpiderLoader
    fun clearCache()
}
