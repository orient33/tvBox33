package com.github.tvbox.newbox.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.data.local.entity.VodRecord
import com.github.tvbox.newbox.data.repository.HistoryRepository
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
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val records: StateFlow<List<VodRecord>> = historyRepository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val listView: StateFlow<Boolean> = settingsStore.historyListView
        .let { flow ->
            val state = MutableStateFlow(false)
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

    fun toggleListView() {
        viewModelScope.launch {
            settingsStore.setHistoryListView(!listView.value)
        }
    }

    fun deleteRecord(vodId: String) {
        viewModelScope.launch {
            historyRepository.deleteRecord(vodId)
        }
    }
}
