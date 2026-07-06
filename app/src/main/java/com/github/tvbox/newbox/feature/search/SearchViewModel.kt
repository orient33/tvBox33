package com.github.tvbox.newbox.feature.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.data.store.SettingsStore
import com.github.tvbox.newbox.domain.SearchResult
import com.github.tvbox.newbox.domain.usecase.SearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val searchUseCase: SearchUseCase,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    companion object { private const val TAG = "NewBox-Search" }

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val listView: StateFlow<Boolean> = settingsStore.searchListView
        .let { flow ->
            val state = MutableStateFlow(false)
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

    val searchHistory: StateFlow<List<String>> = settingsStore.searchHistory
        .let { flow ->
            val state = MutableStateFlow<List<String>>(emptyList())
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

    fun toggleListView() {
        viewModelScope.launch {
            settingsStore.setSearchListView(!listView.value)
        }
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch { settingsStore.removeSearchHistory(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { settingsStore.clearSearchHistory() }
    }

    fun search(keyword: String, recordHistory: Boolean = true) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            if (recordHistory) settingsStore.addSearchHistory(keyword)
            try {
                val sources = subscriptionRepository.sources.first()
                val totalSources = sources.count { it.searchable }
                val results = mutableListOf<SearchResult>()
                var completedSources = 0
                _uiState.value = SearchUiState.Searching(
                    results = emptyList(),
                    keyword = keyword,
                    completedSources = 0,
                    totalSources = totalSources,
                )
                searchUseCase.searchProgressively(SearchUseCase.Params(keyword, sources)).collect { result ->
                    completedSources++
                    if (result.vodItems.isNotEmpty()) {
                        results += result
                    }
                    _uiState.value = SearchUiState.Searching(
                        results = results.toList(),
                        keyword = keyword,
                        completedSources = completedSources,
                        totalSources = totalSources,
                    )
                }
                _uiState.value = SearchUiState.Success(results, keyword, totalSources)
            } catch (e: Exception) {
                Log.e(TAG, "searchFail: keyword=$keyword, error=${e.javaClass.simpleName}: ${e.message}", e)
                _uiState.value = SearchUiState.Error(e.message ?: "Search failed")
            }
        }
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Searching(
        val results: List<SearchResult>,
        val keyword: String,
        val completedSources: Int,
        val totalSources: Int,
    ) : SearchUiState
    data class Success(val results: List<SearchResult>, val keyword: String, val totalSources: Int) : SearchUiState
    data class Error(val message: String) : SearchUiState
}
