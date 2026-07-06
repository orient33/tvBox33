package com.github.tvbox.newbox.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.data.local.entity.VodCollect
import com.github.tvbox.newbox.data.repository.CollectRepository
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
class FavoriteViewModel @Inject constructor(
    private val collectRepository: CollectRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val favorites: StateFlow<List<VodCollect>> = collectRepository.allCollects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listView: StateFlow<Boolean> = settingsStore.favoriteListView
        .let { flow ->
            val state = MutableStateFlow(false)
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

    fun toggleListView() {
        viewModelScope.launch {
            settingsStore.setFavoriteListView(!listView.value)
        }
    }

    fun deleteCollect(vodId: String) {
        viewModelScope.launch {
            collectRepository.deleteCollect(vodId)
        }
    }
}
