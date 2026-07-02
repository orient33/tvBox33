package com.github.tvbox.newbox.spider.api

interface SpiderLoader {
    suspend fun load(config: SpiderSourceConfig): Spider
    fun isSupported(type: SourceType): Boolean
}

interface SpiderFactory {
    fun createLoader(type: SourceType): SpiderLoader
    fun clearCache()
}
