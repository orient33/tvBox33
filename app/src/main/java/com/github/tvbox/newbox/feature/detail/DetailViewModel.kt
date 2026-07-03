package com.github.tvbox.newbox.feature.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tvbox.newbox.domain.VodDetail
import com.github.tvbox.newbox.domain.usecase.GetDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val getDetailUseCase: GetDetailUseCase,
) : ViewModel() {

    companion object { private const val TAG = "NewBox-Detail" }

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Idle)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadDetail(vodId: String, sourceKey: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val detail = getDetailUseCase(GetDetailUseCase.Params(vodId, sourceKey))
                _uiState.value = DetailUiState.Success(detail)
            } catch (e: Exception) {
                Log.e(TAG, "Detail load failed sourceKey=$sourceKey, vodId=$vodId", e)
                _uiState.value = DetailUiState.Error(e.message ?: "Failed to load detail")
            }
        }
    }
}

sealed interface DetailUiState {
    data object Idle : DetailUiState
    data object Loading : DetailUiState
    data class Success(val detail: VodDetail) : DetailUiState
    data class Error(val message: String) : DetailUiState
}
