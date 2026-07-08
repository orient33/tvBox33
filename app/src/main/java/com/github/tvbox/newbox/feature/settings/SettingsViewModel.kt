package com.github.tvbox.newbox.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.data.repository.ProbeResult
import com.github.tvbox.newbox.data.repository.RouteEntry
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.data.repository.WarehouseEntry
import com.github.tvbox.newbox.data.store.SettingsStore
import com.github.tvbox.newbox.data.store.WarehouseData
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.osc.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val sourceCounts: StateFlow<Map<String, Int>> = subscriptionRepository.sourceCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val currentSource: StateFlow<SourceConfig?> = subscriptionRepository.currentSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentSubscriptionUrl: StateFlow<String?> = subscriptionRepository.currentSubscriptionUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val subscriptionUrls: StateFlow<List<String>> = settingsStore.subscriptionUrls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptionTitles: StateFlow<Map<String, String>> = settingsStore.subscriptionTitles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val subscriptionWarehouses: StateFlow<Map<String, List<WarehouseData>>> = settingsStore.subscriptionWarehouses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val currentWarehouse: StateFlow<Map<String, Int>> = settingsStore.currentWarehouse
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _multiRouteResult = MutableStateFlow<ProbeResult.MultiRoute?>(null)
    val multiRouteResult: StateFlow<ProbeResult.MultiRoute?> = _multiRouteResult.asStateFlow()

    private val _warehouseChoices = MutableStateFlow<ProbeResult.MultiWarehouse?>(null)
    val warehouseChoices: StateFlow<ProbeResult.MultiWarehouse?> = _warehouseChoices.asStateFlow()

    fun addSubscription(url: String) {
        viewModelScope.launch {
            try {
                val result = subscriptionRepository.probeSubscription(url)
                when (result) {
                    is ProbeResult.SingleConfig -> {
                        subscriptionRepository.loadSubscription(result.url)
                        _error.value = null
                    }
                    is ProbeResult.MultiRoute -> {
                        settingsStore.addSubscriptionUrl(url)
                        settingsStore.setWarehouses(url, result.routes)
                        val defaultIndex = 0
                        settingsStore.setCurrentWarehouse(url, defaultIndex)
                        subscriptionRepository.loadWarehouse(url, result.routes[defaultIndex].url)
                        _multiRouteResult.value = result
                        _error.value = null
                    }
                    is ProbeResult.MultiWarehouse -> {
                        _warehouseChoices.value = result
                    }
                }
            } catch (e: Exception) {
                Logger.i("df", "fail add ", e)
                _error.value = e.message ?: "加载失败"
            }
        }
    }

    fun loadSubscriptionDirect(url: String) {
        viewModelScope.launch {
            try {
                subscriptionRepository.loadSubscription(url)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
            }
        }
    }

    fun selectWarehouse(subscriptionUrl: String, warehouseIndex: Int) {
        viewModelScope.launch {
            try {
                val warehouses = subscriptionWarehouses.value[subscriptionUrl] ?: return@launch
                if (warehouseIndex < 0 || warehouseIndex >= warehouses.size) return@launch
                settingsStore.setCurrentWarehouse(subscriptionUrl, warehouseIndex)
                subscriptionRepository.loadWarehouse(subscriptionUrl, warehouses[warehouseIndex].url)
                subscriptionRepository.selectSubscription(subscriptionUrl)
            } catch (e: Exception) {
                Logger.e("SettingsVM", "selectWarehouse failed: ${e.message}", e)
                _error.value = e.message ?: "切换仓失败"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearMultiRouteResult() {
        _multiRouteResult.value = null
    }

    fun clearWarehouseChoices() {
        _warehouseChoices.value = null
    }

    fun removeSubscription(url: String) {
        viewModelScope.launch {
            subscriptionRepository.removeSubscription(url)
        }
    }

    fun selectSubscription(url: String) {
        viewModelScope.launch {
            subscriptionRepository.selectSubscription(url)
        }
    }

    fun setSubscriptionTitle(url: String, title: String) {
        viewModelScope.launch {
            settingsStore.setSubscriptionTitle(url, title)
        }
    }
}
