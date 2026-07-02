package com.github.tvbox.newbox.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_SUBSCRIPTION_URLS = stringSetPreferencesKey("subscription_urls")
        private val KEY_CURRENT_SOURCE = stringPreferencesKey("current_source_key")
    }

    val subscriptionUrls: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUBSCRIPTION_URLS]?.toList() ?: emptyList()
    }

    val currentSourceKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_SOURCE]
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
    }

    suspend fun setCurrentSource(key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_SOURCE] = key
        }
    }
}
