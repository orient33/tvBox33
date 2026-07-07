package com.github.tvbox.newbox.feature.detailplayer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.data.repository.CollectRepository
import com.github.tvbox.newbox.data.repository.HistoryRepository
import com.github.tvbox.newbox.data.repository.SubscriptionRepository
import com.github.tvbox.newbox.domain.PlayerResult
import com.github.tvbox.newbox.domain.VodDetail
import com.github.tvbox.newbox.domain.usecase.GetDetailUseCase
import com.github.tvbox.newbox.domain.usecase.GetPlayerUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailPlayerViewModel @Inject constructor(
    private val getDetailUseCase: GetDetailUseCase,
    private val getPlayerUrlUseCase: GetPlayerUrlUseCase,
    private val collectRepository: CollectRepository,
    private val historyRepository: HistoryRepository,
    private val subscriptionRepository: SubscriptionRepository,
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

    private val _resumePosition = MutableStateFlow(0L)
    val resumePosition: StateFlow<Long> = _resumePosition.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _currentVodId = MutableStateFlow<String?>(null)
    val isCollected: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        _currentVodId,
        collectRepository.allCollects,
    ) { vodId, collects ->
        vodId != null && collects.any { it.vodId == vodId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadDetail(vodId: String, sourceKey: String) {
        _currentVodId.value = vodId
        viewModelScope.launch {
            _detailState.value = DetailUiState.Loading
            _playerState.value = PlayerUiState.Idle
            _selectedEpisodeIndex.value = null
            _resumePosition.value = 0L
            try {
                val detail = getDetailUseCase(GetDetailUseCase.Params(vodId, sourceKey))
                _detailState.value = DetailUiState.Success(detail)
                selectInitialEpisode(detail)
            } catch (e: Exception) {
                Log.e(TAG, "loadDetail FAIL sourceKey=$sourceKey, vodId=$vodId, ${e.javaClass.simpleName}: ${e.message}", e)
                _detailState.value = DetailUiState.Error(e.message ?: "Failed to load detail")
            }
        }
    }

    fun toggleCollect() {
        val detail = (_detailState.value as? DetailUiState.Success)?.detail ?: return
        viewModelScope.launch {
            val sourceName = subscriptionRepository.sources
                .first()
                .firstOrNull { it.key == detail.sourceKey }?.name ?: ""
            collectRepository.toggleCollect(detail, sourceName)
        }
    }

    fun recordHistory(progress: Long) {
        val detail = (_detailState.value as? DetailUiState.Success)?.detail ?: return
        val flag = currentFlag(detail)
        val episodeIndex = _selectedEpisodeIndex.value ?: return
        viewModelScope.launch {
            val sourceName = subscriptionRepository.sources
                .first()
                .firstOrNull { it.key == detail.sourceKey }?.name ?: ""
            historyRepository.recordHistory(detail, flag, episodeIndex, progress, sourceName)
        }
    }

    fun selectFlag(index: Int) {
        _selectedFlagIndex.value = index
        _selectedEpisodeIndex.value = null
        _resumePosition.value = 0L
        _playerState.value = PlayerUiState.Idle
    }

    fun selectEpisode(episodeIndex: Int) {
        selectEpisode(episodeIndex, resumePosition = 0L)
    }

    private fun selectEpisode(episodeIndex: Int, resumePosition: Long) {
        val detail = (_detailState.value as? DetailUiState.Success)?.detail ?: return
        val flag = currentFlag(detail)
        val episodes = detail.seriesMap[flag] ?: return
        if (episodeIndex !in episodes.indices) return

        _selectedEpisodeIndex.value = episodeIndex
        _resumePosition.value = resumePosition.coerceAtLeast(0L)
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

    fun playNext() {
        val detail = (_detailState.value as? DetailUiState.Success)?.detail ?: return
        val currentIndex = _selectedEpisodeIndex.value ?: return
        val flag = currentFlag(detail)
        val episodes = detail.seriesMap[flag] ?: return
        if (currentIndex + 1 < episodes.size) {
            selectEpisode(currentIndex + 1)
        }
    }

    fun playPrevious() {
        val detail = (_detailState.value as? DetailUiState.Success)?.detail ?: return
        val currentIndex = _selectedEpisodeIndex.value ?: return
        if (currentIndex > 0) {
            selectEpisode(currentIndex - 1)
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
        val flags = detail.seriesFlags.ifEmpty { detail.seriesMap.keys.toList() }
        return if (flags.isNotEmpty() && _selectedFlagIndex.value in flags.indices) {
            flags[_selectedFlagIndex.value]
        } else {
            detail.seriesMap.keys.firstOrNull() ?: ""
        }
    }

    private suspend fun selectInitialEpisode(detail: VodDetail) {
        val record = historyRepository.getRecord(detail.id)
        val flags = detail.seriesFlags.ifEmpty { detail.seriesMap.keys.toList() }
        val flagIndex = record?.lastPlayFlag
            ?.takeIf { it.isNotBlank() }
            ?.let { flags.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?: 0

        _selectedFlagIndex.value = flagIndex

        val flag = flags.getOrNull(flagIndex) ?: detail.seriesMap.keys.firstOrNull() ?: return
        val episodeCount = detail.seriesMap[flag]?.size ?: return
        if (episodeCount <= 0) return

        val episodeIndex = record?.lastPlayIndex
            ?.takeIf { it in 0 until episodeCount }
            ?: 0
        val progress = record?.lastPlayProgress ?: 0L
        selectEpisode(episodeIndex, progress)
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
