package com.github.tvbox.newbox.data.repository

import com.github.tvbox.osc.util.Logger
import com.github.tvbox.newbox.common.ConfigDecoder
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
        allowTrailingComma = true
    }

    private val _sources = MutableStateFlow<List<SourceConfig>>(emptyList())
    override val sources: StateFlow<List<SourceConfig>> = _sources.asStateFlow()

    private val _sourcesByUrl = mutableMapOf<String, List<SourceConfig>>()

    private val _sourceCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val sourceCounts: StateFlow<Map<String, Int>> = _sourceCounts.asStateFlow()

    private val _sourcesLoaded = MutableStateFlow(false)
    override val sourcesLoaded: StateFlow<Boolean> = _sourcesLoaded.asStateFlow()

    override val blockedSourceKeys: Flow<Set<String>> = settingsStore.blockedSourceKeys

    override val currentSubscriptionUrl: StateFlow<String?> = combine(
        _sources,
        settingsStore.currentSourceKey,
    ) { sources, key ->
        val source = if (key != null) sources.find { it.key == key } else sources.firstOrNull()
        source?.let { s -> _sourcesByUrl.entries.find { it.value.any { it.key == s.key } }?.key }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            val saved = settingsStore.subscriptionUrls.first().filter { it.isNotBlank() }
            val warehousesMap = settingsStore.subscriptionWarehouses.first()
            val currentWarehouseMap = settingsStore.currentWarehouse.first()
            if (saved.isEmpty()) {
                Logger.d(TAG, "init: no saved subscriptions, loading default: $DEFAULT_SUBSCRIPTION_URL")
//                try { loadSubscription(DEFAULT_SUBSCRIPTION_URL) } catch (e: Exception) {
//                    Logger.e(TAG, "init: failed to load default subscription: ${e.message}")
//                }
            } else {
                saved.forEach { url ->
                    Logger.d(TAG, "init: reloading saved subscription: $url")
                    try {
                        val warehouses = warehousesMap[url]
                        if (warehouses != null && warehouses.isNotEmpty()) {
                            val idx = currentWarehouseMap[url] ?: 0
                            val safeIdx = idx.coerceIn(0, warehouses.size - 1)
                            loadWarehouse(url, warehouses[safeIdx].url)
                        } else {
                            loadSubscription(url)
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "init: failed to reload $url: ${e.message}")
                    }
                }
            }
            _sourcesLoaded.value = true
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
            Logger.w(TAG, "loadSubscription: blank URL, skipping url=$url")
            return
        }
        Logger.d(TAG, "loadSubscription: url=$url")

        val parsed = ConfigDecoder.parseSubscriptionUrl(url)
        settingsStore.addSubscriptionUrl(url)

        val body = fetchUrl(parsed.configUrl)
        Logger.d(TAG, "loadSubscription: fetched ${body.length} chars")

        val decoded = ConfigDecoder.decode(body, parsed.configKey)
        val fixed = ConfigDecoder.fixPaths(parsed.configUrl, decoded)

        val root = try {
            json.parseToJsonElement(fixed).jsonObject
        } catch (e: Exception) {
            Logger.e(TAG, "loadSubscription: invalid JSON from $url: ${e.message}")
            return
        }
        val spiderEl = root["spider"]
        val globalSpider = if (spiderEl is JsonPrimitive && spiderEl != JsonNull) spiderEl.content else ""
        Logger.d(TAG, "loadSubscription: globalSpider=$globalSpider")

        val sites = root["sites"]?.jsonArray?.map { it.jsonObject.toSiteJson() } ?: emptyList()
        Logger.d(TAG, "loadSubscription: parsed ${sites.size} sites")
        val configs = sites
            .filter { it.api.isNotBlank() }
            .map { it.toSourceConfig(globalSpider = globalSpider, baseUrl = parsed.configUrl) }
            .deduplicateByLast { it.key }
        Logger.d(TAG, "loadSubscription: ${configs.size} valid configs, first=${configs.firstOrNull()?.name}")
        spiderFactory.clearCache()
        _sourcesByUrl[url] = configs
        rebuildSources()
        Logger.d(TAG, "loadSubscription: _sources updated, total=${_sources.value.size} configs")
        val currentKey = settingsStore.currentSourceKey.first()
        Logger.d(TAG, "loadSubscription: currentSourceKey=$currentKey")
        if (configs.isNotEmpty() && shouldUseFirstSource(configs, currentKey)) {
            val first = configs.first()
            settingsStore.setCurrentSource(first.key)
            Logger.d(TAG, "loadSubscription: auto-selected source: ${first.key} (${first.type})")
        }
    }

    override suspend fun probeSubscription(url: String): ProbeResult {
        val parsed = ConfigDecoder.parseSubscriptionUrl(url)
        val body = fetchUrl(parsed.configUrl)
        val decoded = ConfigDecoder.decode(body, parsed.configKey)
        val fixed = ConfigDecoder.fixPaths(parsed.configUrl, decoded)
        val root = try {
            json.parseToJsonElement(fixed).jsonObject
        } catch (e: Exception) {
            Logger.e(TAG, "probeSubscription: invalid JSON from $url: ${e.message}")
            throw Exception("无效的订阅内容: ${e.message}")
        }

        val urlsEl = root["urls"]
        if (urlsEl is JsonArray && urlsEl.size > 0) {
            val first = urlsEl[0]
            if (first is JsonObject && first.contains("url") && first.contains("name")) {
                val routes = urlsEl.mapNotNull { el ->
                    if (el is JsonObject) {
                        val name = (el["name"] as? JsonPrimitive)?.content?.trim()
                            ?.replace(Regex("<|>|《|》|-"), "") ?: return@mapNotNull null
                        val routeUrl = (el["url"] as? JsonPrimitive)?.content?.trim() ?: return@mapNotNull null
                        RouteEntry(name, routeUrl)
                    } else null
                }
                if (routes.isNotEmpty()) return ProbeResult.MultiRoute(routes)
            }
        }

        val storeHouseEl = root["storeHouse"]
        if (storeHouseEl is JsonArray && storeHouseEl.size > 0) {
            val first = storeHouseEl[0]
            if (first is JsonObject && first.contains("sourceName") && first.contains("sourceUrl")) {
                val warehouses = storeHouseEl.mapNotNull { el ->
                    if (el is JsonObject) {
                        val name = (el["sourceName"] as? JsonPrimitive)?.content?.trim()
                            ?.replace(Regex("<|>|《|》|-"), "") ?: return@mapNotNull null
                        val whUrl = (el["sourceUrl"] as? JsonPrimitive)?.content?.trim() ?: return@mapNotNull null
                        WarehouseEntry(name, whUrl)
                    } else null
                }
                if (warehouses.isNotEmpty()) return ProbeResult.MultiWarehouse(warehouses)
            }
        }

        return ProbeResult.SingleConfig(url)
    }

    override suspend fun setCurrentSource(key: String) {
        settingsStore.setCurrentSource(key)
    }

    override suspend fun setSourceBlocked(key: String, blocked: Boolean) {
        settingsStore.setSourceBlocked(key, blocked)
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

    override suspend fun selectSubscription(url: String) {
        val configs = _sourcesByUrl[url] ?: return
        val first = configs.firstOrNull() ?: return
        settingsStore.setCurrentSource(first.key)
    }

    override suspend fun loadWarehouse(parentUrl: String, warehouseUrl: String) {
        if (warehouseUrl.isBlank()) return
        Logger.d(TAG, "loadWarehouse: parentUrl=$parentUrl warehouseUrl=$warehouseUrl")

        val parsed = ConfigDecoder.parseSubscriptionUrl(warehouseUrl)
        Logger.d(TAG, "loadWarehouse: parsed configUrl=${parsed.configUrl} configKey=${parsed.configKey}")

        val body = fetchUrl(parsed.configUrl)
        Logger.d(TAG, "loadWarehouse: body length=${body.length}, preview=${body.take(200)}")

        val decoded = ConfigDecoder.decode(body, parsed.configKey)
        Logger.d(TAG, "loadWarehouse: decoded length=${decoded.length}, preview=${decoded.take(200)}")

        val fixed = ConfigDecoder.fixPaths(parsed.configUrl, decoded)
        Logger.d(TAG, "loadWarehouse: fixed length=${fixed.length}, preview=${fixed.take(200)}")

        val root = try {
            json.parseToJsonElement(fixed).jsonObject
        } catch (e: Exception) {
            Logger.e(TAG, "loadWarehouse: invalid JSON from $warehouseUrl: ${e.message}")
            Logger.e(TAG, "loadWarehouse: fixed content (first 500): ${fixed.take(500)}")
            return
        }

        val spiderEl = root["spider"]
        val globalSpider = if (spiderEl is JsonPrimitive && spiderEl != JsonNull) spiderEl.content else ""

        val sites = root["sites"]?.jsonArray?.map { it.jsonObject.toSiteJson() } ?: emptyList()
        val configs = sites
            .filter { it.api.isNotBlank() }
            .map { it.toSourceConfig(globalSpider = globalSpider, baseUrl = parsed.configUrl) }
            .deduplicateByLast { it.key }
        Logger.d(TAG, "loadWarehouse: ${configs.size} configs under parent=$parentUrl")

        spiderFactory.clearCache()
        _sourcesByUrl[parentUrl] = configs
        rebuildSources()

        val currentKey = settingsStore.currentSourceKey.first()
        if (configs.isNotEmpty() && shouldUseFirstSource(configs, currentKey)) {
            settingsStore.setCurrentSource(configs.first().key)
        }
    }

    private fun shouldUseFirstSource(configs: List<SourceConfig>, currentKey: String?): Boolean {
        if (currentKey == null) return true
        val current = configs.firstOrNull { it.key == currentKey } ?: return true
        val first = configs.firstOrNull() ?: return false
        return current.type == SourceType.HTTP_API && first.key != current.key
    }

    private fun rebuildSources() {
        _sources.value = _sourcesByUrl.values.flatten().deduplicateByLast { it.key }
        _sourceCounts.value = _sourcesByUrl.mapValues { it.value.size }
    }

    private fun <T, K> List<T>.deduplicateByLast(keySelector: (T) -> K): List<T> {
        val map = linkedMapOf<K, T>()
        for (item in this) map[keySelector(item)] = item
        return map.values.toList()
    }

    private suspend fun fetchUrl(url: String): String = withContext(ioDispatcher) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return@withContext ""
        val request = Request.Builder().url(trimmed).build()
        val response = okHttpClient.newCall(request).execute()
        Logger.d(TAG, "fetchUrl: $trimmed → HTTP ${response.code}")
        if (response.isSuccessful) {
            response.body?.string() ?: ""
        } else {
            throw Exception("HTTP ${response.code}")
        }
    }
}
