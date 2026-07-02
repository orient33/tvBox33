package com.github.tvbox.newbox.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.data.store.SettingsStore
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

    val subscriptionUrls: StateFlow<List<String>> = settingsStore.subscriptionUrls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadSubscription(url: String) {
        viewModelScope.launch {
            try {
                subscriptionRepository.loadSubscription(url)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "加载失败"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun removeSubscription(url: String) {
        viewModelScope.launch {
            subscriptionRepository.removeSubscription(url)
        }
    }
}
