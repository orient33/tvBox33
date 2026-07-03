package com.github.tvbox.newbox.feature.detailplayer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.domain.PlayerResult
import com.github.tvbox.newbox.domain.VodDetail
import com.github.tvbox.newbox.domain.usecase.GetDetailUseCase
import com.github.tvbox.newbox.domain.usecase.GetPlayerUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailPlayerViewModel @Inject constructor(
    private val getDetailUseCase: GetDetailUseCase,
    private val getPlayerUrlUseCase: GetPlayerUrlUseCase,
) : ViewModel() {

    companion object { private const val TAG = "NewBox-DetailPlayer" }

    private val _detailState = MutableStateFlow<DetailUiState>(DetailUiState.Idle)
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private val _playerState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _selectedFlagIndex = MutableStateFlow(0)
    val selectedFlagIndex: StateFlow<Int> = _selectedFlagIndex.asStateFlow()

    private val _selectedEpisodeIndex = MutableStateFlow<Int?>(null)
    val selectedEpisodeIndex: StateFlow<Int?> = _selectedEpisodeIndex.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    fun loadDetail(vodId: String, sourceKey: String) {
        viewModelScope.launch {
            _detailState.value = DetailUiState.Loading
            _playerState.value = PlayerUiState.Idle
            _selectedEpisodeIndex.value = null
            try {
                val detail = getDetailUseCase(GetDetailUseCase.Params(vodId, sourceKey))
                _detailState.value = DetailUiState.Success(detail)
                _selectedFlagIndex.value = 0
            } catch (e: Exception) {
                Log.e(TAG, "Detail load failed sourceKey=$sourceKey, vodId=$vodId", e)
                _detailState.value = DetailUiState.Error(e.message ?: "Failed to load detail")
            }
        }
    }

    fun selectFlag(index: Int) {
        _selectedFlagIndex.value = index
        _selectedEpisodeIndex.value = null
        _playerState.value = PlayerUiState.Idle
    }

    fun selectEpisode(episodeIndex: Int) {
        val detail = (_detailState.value as? DetailUiState.Success)?.detail ?: return
        val flag = currentFlag(detail)
        val episodes = detail.seriesMap[flag] ?: return
        if (episodeIndex !in episodes.indices) return

        _selectedEpisodeIndex.value = episodeIndex
        val episode = episodes[episodeIndex]

        viewModelScope.launch {
            _playerState.value = PlayerUiState.Loading
            try {
                val result = getPlayerUrlUseCase(
                    GetPlayerUrlUseCase.Params(flag, episode.url, detail.sourceKey)
                )
                _playerState.value = PlayerUiState.Ready(result)
            } catch (e: Exception) {
                Log.e(TAG, "Play URL resolve failed flag=$flag, url=${episode.url}", e)
                _playerState.value = PlayerUiState.Error(e.message ?: "Failed to get play URL")
            }
        }
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun releasePlayer() {
        _playerState.value = PlayerUiState.Idle
        _isFullscreen.value = false
    }

    private fun currentFlag(detail: VodDetail): String {
        val flags = detail.seriesFlags
        return if (flags.isNotEmpty() && _selectedFlagIndex.value in flags.indices) {
            flags[_selectedFlagIndex.value]
        } else {
            detail.seriesMap.keys.firstOrNull() ?: ""
        }
    }
}

sealed interface DetailUiState {
    data object Idle : DetailUiState
    data object Loading : DetailUiState
    data class Success(val detail: VodDetail) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

sealed interface PlayerUiState {
    data object Idle : PlayerUiState
    data object Loading : PlayerUiState
    data class Ready(val playerResult: PlayerResult) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}
