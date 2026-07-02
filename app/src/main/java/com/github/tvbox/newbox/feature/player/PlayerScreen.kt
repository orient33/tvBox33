package com.github.tvbox.newbox.feature.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.tvbox.newbox.ui.common.ErrorView
import com.github.tvbox.newbox.ui.common.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    flag: String,
    playUrl: String,
    sourceKey: String,
    title: String = "",
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(flag, playUrl, sourceKey) {
        viewModel.play(flag, playUrl, sourceKey)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.release() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title.ifBlank { "播放" }, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        when (val state = uiState) {
            is PlayerUiState.Idle -> LoadingView()
            is PlayerUiState.Loading -> LoadingView(message = "获取播放地址…")
            is PlayerUiState.Error -> ErrorView(message = state.message)
            is PlayerUiState.Ready -> {
                val playerResult = state.playerResult
                val exoPlayer = rememberExoPlayer(context, playerResult.url, playerResult.headers)

                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            useController = true
                            player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun rememberExoPlayer(
    context: android.content.Context,
    url: String,
    headers: Map<String, String>,
): ExoPlayer {
    val exoPlayer = remember {
        val dataSourceFactory = if (headers.isNotEmpty()) {
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
        } else {
            DefaultHttpDataSource.Factory()
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .build()
    }

    LaunchedEffect(url) {
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    return exoPlayer
}
