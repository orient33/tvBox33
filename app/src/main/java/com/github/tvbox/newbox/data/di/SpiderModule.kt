package com.github.tvbox.newbox.data.di

import android.content.Context
import com.github.tvbox.newbox.spider.api.SpiderFactory
import com.github.tvbox.newbox.spider.api.SpiderLoader
import com.github.tvbox.newbox.spider.api.SourceType
import com.github.tvbox.newbox.spider.jar.JarSpiderLoader
import com.github.tvbox.newbox.spider.jar.JsSpiderLoader
import com.github.tvbox.newbox.spider.jar.T4SpiderLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpiderModule {

    @Provides
    @Singleton
    fun provideSpiderFactory(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): SpiderFactory = object : SpiderFactory {
        private val jarLoader by lazy { JarSpiderLoader(context, client) }
        private val jsLoader by lazy { JsSpiderLoader(context, client) }
        private val t4Loader by lazy { T4SpiderLoader(client) }
        private val loaders by lazy { listOf(jsLoader, jarLoader, t4Loader) }

        override fun createLoader(type: SourceType): SpiderLoader {
            return loaders.firstOrNull { it.isSupported(type) }
                ?: throw IllegalArgumentException("No SpiderLoader found for type: $type")
        }

        override fun clearCache() {
            jarLoader.clearCache()
            jsLoader.clearCache()
        }
    }
}
