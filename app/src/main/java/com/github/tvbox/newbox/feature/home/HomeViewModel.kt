package com.github.tvbox.newbox.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.domain.HomeContent
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.domain.usecase.GetCategoryContentUseCase
import com.github.tvbox.newbox.domain.usecase.GetHomeContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val getCategoryContentUseCase: GetCategoryContentUseCase,
) : ViewModel() {

    companion object { private const val TAG = "NewBox-Home" }

    private data class LoadRequest(
        val source: SourceConfig?,
        val categoryId: String?,
        val filters: Map<String, String>,
    )

    private val retryTrigger = MutableStateFlow(0)
    private val selectedCategoryIdFlow = MutableStateFlow<String?>(null)
    private val selectedFiltersFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    private val uiStateFlow = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    private val isLoadingMoreFlow = MutableStateFlow(false)
    private val isReloadingFlow = MutableStateFlow(false)

    val selectedCategoryId: StateFlow<String?> = selectedCategoryIdFlow
    val selectedFilters: StateFlow<Map<String, String>> = selectedFiltersFlow
    val uiState: StateFlow<HomeUiState> = uiStateFlow
    val isLoadingMore: StateFlow<Boolean> = isLoadingMoreFlow
    val isReloading: StateFlow<Boolean> = isReloadingFlow

    val sources: StateFlow<List<SourceConfig>> = subscriptionRepository.sources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSource: StateFlow<SourceConfig?> = subscriptionRepository.currentSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            combine(
                currentSource,
                selectedCategoryIdFlow,
                selectedFiltersFlow,
                retryTrigger,
            ) { source, categoryId, filters, _ ->
                LoadRequest(source, categoryId, filters)
            }.collectLatest { request ->
                loadFirstPage(request.source, request.categoryId, request.filters)
            }
        }
    }

    private suspend fun loadFirstPage(source: SourceConfig?, categoryId: String?, filters: Map<String, String>) {
        if (source == null) {
            uiStateFlow.value = HomeUiState.Error("No source selected")
            return
        }
        isLoadingMoreFlow.value = false
        // 保留 Success 状态以维持 Tab 栏可见，用 isReloading 标记卡片区域显示 loading
        if (uiStateFlow.value is HomeUiState.Success) {
            isReloadingFlow.value = true
        } else {
            uiStateFlow.value = HomeUiState.Loading
        }
        try {
            val content = getHomeContentUseCase(
                GetHomeContentUseCase.Params(
                    sourceConfig = source,
                    categoryId = categoryId,
                    page = "1",
                    extend = filters,
                ),
            )
            uiStateFlow.value = HomeUiState.Success(content)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Home load failed: ${e.message}", e)
            uiStateFlow.value = HomeUiState.Error(e.message ?: "Unknown error")
        } finally {
            isReloadingFlow.value = false
        }
    }

    fun selectSource(key: String) {
        viewModelScope.launch {
            uiStateFlow.value = HomeUiState.Loading
            selectedCategoryIdFlow.value = null
            selectedFiltersFlow.value = emptyMap()
            subscriptionRepository.setCurrentSource(key)
        }
    }

    fun selectCategory(categoryId: String?) {
        selectedFiltersFlow.value = emptyMap()
        selectedCategoryIdFlow.value = categoryId
    }

    fun selectFilter(groupKey: String, itemValue: String) {
        val currentFilters = selectedFiltersFlow.value
        selectedFiltersFlow.value = if (currentFilters[groupKey] == itemValue || itemValue.isBlank()) {
            currentFilters - groupKey
        } else {
            currentFilters + (groupKey to itemValue)
        }
    }

    fun loadMore() {
        val categoryId = selectedCategoryIdFlow.value ?: return
        val source = currentSource.value ?: return
        val filters = selectedFiltersFlow.value
        val state = uiStateFlow.value as? HomeUiState.Success ?: return
        val currentPage = state.homeContent.page.toIntOrNull() ?: return
        val pageCount = state.homeContent.pageCount.toIntOrNull() ?: return
        if (currentPage >= pageCount || isLoadingMoreFlow.value) return

        viewModelScope.launch {
            isLoadingMoreFlow.value = true
            try {
                val nextPage = (currentPage + 1).toString()
                val result = getCategoryContentUseCase(
                    GetCategoryContentUseCase.Params(
                        sourceConfig = source,
                        tid = categoryId,
                        pg = nextPage,
                        filter = true,
                        extend = filters,
                    ),
                )
                if (selectedCategoryIdFlow.value != categoryId || selectedFiltersFlow.value != filters) return@launch
                val latestState = uiStateFlow.value as? HomeUiState.Success ?: return@launch
                uiStateFlow.value = HomeUiState.Success(
                    latestState.homeContent.copy(
                        videos = latestState.homeContent.videos + result.videos,
                        page = result.page.ifBlank { nextPage },
                        pageCount = result.pageCount.ifBlank { latestState.homeContent.pageCount },
                        total = result.total,
                    ),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Load more failed: ${e.message}", e)
            } finally {
                isLoadingMoreFlow.value = false
            }
        }
    }

    fun retry() {
        retryTrigger.value += 1
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val homeContent: HomeContent) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
