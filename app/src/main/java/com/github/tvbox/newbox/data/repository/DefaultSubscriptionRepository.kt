package com.github.tvbox.newbox.data.repository

import android.util.Log
import com.github.tvbox.newbox.common.IoDispatcher
import com.github.tvbox.newbox.data.store.SettingsStore
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.domain.SourceType
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@Singleton
class DefaultSubscriptionRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsStore: SettingsStore,
    private val spiderFactory: com.github.tvbox.newbox.spider.api.SpiderFactory,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SubscriptionRepository {

    companion object {
        private const val TAG = "NewBox-DefSub"
        private const val DEFAULT_SUBSCRIPTION_URL = "https://9280.kstore.vip/newwex.json"
    }

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _sources = MutableStateFlow<List<SourceConfig>>(emptyList())
    override val sources: StateFlow<List<SourceConfig>> = _sources.asStateFlow()

    private val _sourcesByUrl = mutableMapOf<String, List<SourceConfig>>()

    private val _sourceCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val sourceCounts: StateFlow<Map<String, Int>> = _sourceCounts.asStateFlow()

    init {
        scope.launch {
            val saved = settingsStore.subscriptionUrls.first().filter { it.isNotBlank() }
            if (saved.isEmpty()) {
                Log.d(TAG, "init: no saved subscriptions, loading default: $DEFAULT_SUBSCRIPTION_URL")
                try { loadSubscription(DEFAULT_SUBSCRIPTION_URL) } catch (e: Exception) {
                    Log.e(TAG, "init: failed to load default subscription: ${e.message}")
                }
            } else {
                saved.forEach { url ->
                    Log.d(TAG, "init: reloading saved subscription: $url")
                    try { loadSubscription(url) } catch (e: Exception) {
                        Log.e(TAG, "init: failed to reload $url: ${e.message}")
                    }
                }
            }
        }
    }

    override val currentSource: Flow<SourceConfig?> = combine(
        _sources,
        settingsStore.currentSourceKey,
    ) { sources, key ->
        if (key != null) sources.find { it.key == key } else sources.firstOrNull()
    }

    override suspend fun loadSubscription(url: String) {
        if (url.isBlank()) {
            Log.w(TAG, "loadSubscription: blank URL, skipping url=$url")
            return
        }
        Log.d(TAG, "loadSubscription: url=$url")
        settingsStore.addSubscriptionUrl(url)
        val body = fetchUrl(url)
        Log.d(TAG, "loadSubscription: fetched ${body.length} chars")
        val root = json.parseToJsonElement(body).jsonObject
        val spiderEl = root["spider"]
        val globalSpider = if (spiderEl is JsonPrimitive && spiderEl != JsonNull) spiderEl.content else ""
        Log.d(TAG, "loadSubscription: globalSpider=$globalSpider")

        val sites = root["sites"]?.jsonArray?.map { it.jsonObject.toSiteJson() } ?: emptyList()
        Log.d(TAG, "loadSubscription: parsed ${sites.size} sites")
        val configs = sites
            .filter { it.api.isNotBlank() }
            .map { it.toSourceConfig(globalSpider = globalSpider, baseUrl = url) }
        Log.d(TAG, "loadSubscription: ${configs.size} valid configs, first=${configs.firstOrNull()?.name}")
        spiderFactory.clearCache()
        _sourcesByUrl[url] = configs
        rebuildSources()
        Log.d(TAG, "loadSubscription: _sources updated, total=${_sources.value.size} configs")
        val currentKey = settingsStore.currentSourceKey.first()
        Log.d(TAG, "loadSubscription: currentSourceKey=$currentKey")
        if (configs.isNotEmpty() && currentKey == null) {
            val first = configs.firstOrNull { it.type == SourceType.HTTP_API } ?: configs.first()
            settingsStore.setCurrentSource(first.key)
            Log.d(TAG, "loadSubscription: auto-selected source: ${first.key} (${first.type})")
        }
    }

    override suspend fun setCurrentSource(key: String) {
        settingsStore.setCurrentSource(key)
    }

    override suspend fun removeSubscription(url: String) {
        settingsStore.removeSubscriptionUrl(url)
        val removed = _sourcesByUrl.remove(url)
        if (removed != null) {
            val currentKey = settingsStore.currentSourceKey.first()
            val currentRemoved = removed.any { it.key == currentKey }
            rebuildSources()
            if (currentRemoved) {
                val newKey = _sources.value.firstOrNull()?.key
                if (newKey != null) {
                    settingsStore.setCurrentSource(newKey)
                }
            }
        }
    }

    private fun rebuildSources() {
        val seen = mutableSetOf<String>()
        _sources.value = _sourcesByUrl.values.flatten().filter { seen.add(it.key) }
        _sourceCounts.value = _sourcesByUrl.mapValues { it.value.size }
    }

    private suspend fun fetchUrl(url: String): String = withContext(ioDispatcher) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return@withContext ""
        val fixedUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "http://$trimmed"
        }
        val request = Request.Builder().url(fixedUrl).build()
        val response = okHttpClient.newCall(request).execute()
        Log.d(TAG, "fetchUrl: $fixedUrl → HTTP ${response.code}")
        if (response.isSuccessful) {
            response.body?.string() ?: ""
        } else {
            throw Exception("HTTP ${response.code}")
        }
    }
}
