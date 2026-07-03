package com.github.tvbox.newbox.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
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
