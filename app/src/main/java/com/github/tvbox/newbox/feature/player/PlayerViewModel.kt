package com.github.tvbox.newbox.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.domain.PlayerResult
import com.github.tvbox.newbox.domain.usecase.GetPlayerUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val getPlayerUrlUseCase: GetPlayerUrlUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun play(flag: String, playUrl: String, sourceKey: String) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            try {
                val result = getPlayerUrlUseCase(
                    GetPlayerUrlUseCase.Params(flag, playUrl, sourceKey)
                )
                _uiState.value = PlayerUiState.Ready(result)
            } catch (e: Exception) {
                _uiState.value = PlayerUiState.Error(e.message ?: "Failed to get play URL")
            }
        }
    }

    fun release() {
        _uiState.value = PlayerUiState.Idle
    }
}

sealed interface PlayerUiState {
    data object Idle : PlayerUiState
    data object Loading : PlayerUiState
    data class Ready(val playerResult: PlayerResult) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
