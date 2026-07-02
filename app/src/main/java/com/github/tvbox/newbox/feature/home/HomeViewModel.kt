package com.github.tvbox.newbox.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.common.AppResult
import com.github.tvbox.newbox.common.asAppResult
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.domain.HomeContent
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.domain.usecase.GetHomeContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val getHomeContentUseCase: GetHomeContentUseCase,
) : ViewModel() {

    companion object { private const val TAG = "NewBox-Home" }

    private val retryTrigger = MutableStateFlow(0)

    val sources: StateFlow<List<SourceConfig>> = subscriptionRepository.sources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSource: StateFlow<SourceConfig?> = subscriptionRepository.currentSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState: StateFlow<HomeUiState> = subscriptionRepository.currentSource
        .flatMapLatest { source ->
            if (source == null) {
                Log.w(TAG, "No source selected")
                flowOf(AppResult.Error(IllegalStateException("No source selected")))
            } else {
                Log.d(TAG, "Loading home: source=${source.name} key=${source.key} api=${source.api}")
                retryTrigger.flatMapLatest {
                    kotlinx.coroutines.flow.flow {
                        try {
                            val content = getHomeContentUseCase(GetHomeContentUseCase.Params(source))
                            Log.d(TAG, "Home loaded: ${content.videos.size} videos, ${content.categories.size} categories")
                            emit(content)
                        } catch (e: Exception) {
                            Log.e(TAG, "Home load failed: ${e.message}", e)
                            throw e
                        }
                    }.asAppResult(viewModelScope)
                }
            }
        }
        .map { result ->
            when (result) {
                is AppResult.Loading -> HomeUiState.Loading
                is AppResult.Success -> HomeUiState.Success(result.data)
                is AppResult.Error -> HomeUiState.Error(result.exception.message ?: "Unknown error")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.Loading)

    fun selectSource(key: String) {
        viewModelScope.launch {
            subscriptionRepository.setCurrentSource(key)
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
