package com.github.tvbox.newbox.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.tvbox.newbox.data.repository.RouteEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
data class WarehouseData(val name: String, val url: String)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_SUBSCRIPTION_URLS = stringSetPreferencesKey("subscription_urls")
        private val KEY_CURRENT_SOURCE = stringPreferencesKey("current_source_key")
        private val KEY_SUBSCRIPTION_TITLES = stringPreferencesKey("subscription_titles")
        private val KEY_SUBSCRIPTION_WAREHOUSES = stringPreferencesKey("subscription_warehouses")
        private val KEY_CURRENT_WAREHOUSE = stringPreferencesKey("current_warehouse")
        private val KEY_SEARCH_LIST_VIEW = booleanPreferencesKey("search_list_view")
        private val KEY_FAVORITE_LIST_VIEW = booleanPreferencesKey("favorite_list_view")
        private val KEY_HISTORY_LIST_VIEW = booleanPreferencesKey("history_list_view")
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val subscriptionUrls: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUBSCRIPTION_URLS]?.toList() ?: emptyList()
    }

    val currentSourceKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_SOURCE]
    }

    val subscriptionTitles: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_SUBSCRIPTION_TITLES] ?: return@map emptyMap()
        runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    val subscriptionWarehouses: Flow<Map<String, List<WarehouseData>>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_SUBSCRIPTION_WAREHOUSES] ?: return@map emptyMap()
        runCatching { json.decodeFromString<Map<String, List<WarehouseData>>>(raw) }.getOrDefault(emptyMap())
    }

    val currentWarehouse: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CURRENT_WAREHOUSE] ?: return@map emptyMap()
        runCatching { json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }

    val searchListView: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEARCH_LIST_VIEW] ?: false
    }

    val favoriteListView: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FAVORITE_LIST_VIEW] ?: false
    }

    val historyListView: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HISTORY_LIST_VIEW] ?: false
    }

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_SEARCH_HISTORY] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun addSubscriptionUrl(url: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_SUBSCRIPTION_URLS]?.toMutableSet() ?: mutableSetOf()
            existing.add(url)
            prefs[KEY_SUBSCRIPTION_URLS] = existing
        }
    }

    suspend fun removeSubscriptionUrl(url: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_SUBSCRIPTION_URLS]?.toMutableSet() ?: return@edit
            existing.remove(url)
            prefs[KEY_SUBSCRIPTION_URLS] = existing
        }
        removeSubscriptionTitle(url)
        removeWarehouses(url)
    }

    suspend fun setCurrentSource(key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_SOURCE] = key
        }
    }

    suspend fun setSearchListView(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SEARCH_LIST_VIEW] = enabled
        }
    }

    suspend fun setFavoriteListView(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FAVORITE_LIST_VIEW] = enabled
        }
    }

    suspend fun setHistoryListView(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HISTORY_LIST_VIEW] = enabled
        }
    }

    suspend fun addSearchHistory(keyword: String) {
        if (keyword.isBlank()) return
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_SEARCH_HISTORY]
            val list = raw?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = (list - keyword).toMutableList()
            updated.add(0, keyword)
            if (updated.size > 100) updated.subList(100, updated.size).clear()
            prefs[KEY_SEARCH_HISTORY] = json.encodeToString(kotlinx.serialization.serializer(), updated)
        }
    }

    suspend fun removeSearchHistory(keyword: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_SEARCH_HISTORY] ?: return@edit
            val list = runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
            val updated = list - keyword
            prefs[KEY_SEARCH_HISTORY] = json.encodeToString(kotlinx.serialization.serializer(), updated)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SEARCH_HISTORY)
        }
    }

    suspend fun setSubscriptionTitle(url: String, title: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_SUBSCRIPTION_TITLES]
            val map = raw?.let {
                runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            val updated = map.toMutableMap()
            if (title.isBlank()) updated.remove(url) else updated[url] = title
            prefs[KEY_SUBSCRIPTION_TITLES] = json.encodeToString(
                kotlinx.serialization.serializer(), updated
            )
        }
    }

    suspend fun setWarehouses(url: String, warehouses: List<RouteEntry>) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_SUBSCRIPTION_WAREHOUSES]
            val map = raw?.let {
                runCatching { json.decodeFromString<Map<String, List<WarehouseData>>>(it) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            val updated = map.toMutableMap()
            updated[url] = warehouses.map { WarehouseData(it.name, it.url) }
            prefs[KEY_SUBSCRIPTION_WAREHOUSES] = json.encodeToString(
                kotlinx.serialization.serializer(), updated
            )
        }
    }

    suspend fun setCurrentWarehouse(url: String, index: Int) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_CURRENT_WAREHOUSE]
            val map = raw?.let {
                runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            val updated = map.toMutableMap()
            if (index < 0) updated.remove(url) else updated[url] = index
            prefs[KEY_CURRENT_WAREHOUSE] = json.encodeToString(
                kotlinx.serialization.serializer(), updated
            )
        }
    }

    private suspend fun removeSubscriptionTitle(url: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_SUBSCRIPTION_TITLES] ?: return@edit
            val map = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
            val updated = map.toMutableMap()
            updated.remove(url)
            prefs[KEY_SUBSCRIPTION_TITLES] = json.encodeToString(
                kotlinx.serialization.serializer(), updated
            )
        }
    }

    private suspend fun removeWarehouses(url: String) {
        context.dataStore.edit { prefs ->
            val rawWh = prefs[KEY_SUBSCRIPTION_WAREHOUSES]
            if (rawWh != null) {
                val whMap = runCatching { json.decodeFromString<Map<String, List<WarehouseData>>>(rawWh) }.getOrDefault(emptyMap())
                val whUpdated = whMap.toMutableMap()
                whUpdated.remove(url)
                prefs[KEY_SUBSCRIPTION_WAREHOUSES] = json.encodeToString(
                    kotlinx.serialization.serializer(), whUpdated
                )
            }
            val rawCw = prefs[KEY_CURRENT_WAREHOUSE]
            if (rawCw != null) {
                val cwMap = runCatching { json.decodeFromString<Map<String, Int>>(rawCw) }.getOrDefault(emptyMap())
                val cwUpdated = cwMap.toMutableMap()
                cwUpdated.remove(url)
                prefs[KEY_CURRENT_WAREHOUSE] = json.encodeToString(
                    kotlinx.serialization.serializer(), cwUpdated
                )
            }
        }
    }
}
