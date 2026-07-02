package com.github.tvbox.newbox.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

fun <T> Flow<T>.asAppResult(
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(5000),
): StateFlow<AppResult<T>> = map<T, AppResult<T>> { AppResult.Success(it) }
    .catch { emit(AppResult.Error(it)) }
    .stateIn(scope, started, AppResult.Loading)
