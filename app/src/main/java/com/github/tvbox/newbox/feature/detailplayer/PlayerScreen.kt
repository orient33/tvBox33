package com.github.tvbox.newbox.feature.detailplayer

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.github.tvbox.newbox.R
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PLAYER_GESTURE_SLOP_PX = 12f
private val PLAYER_EDGE_PADDING = 16.dp

data class PlaybackMediaInfo(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sampleMimeType: String? = null,
    val codecs: String? = null,
    val bitrate: Int = 0,
    val frameRate: Float = 0f,
)

@Composable
fun PlayerSection(
    playerState: PlayerUiState,
    thumbnailUrl: String,
    detailPic: String,
    exoPlayer: ExoPlayer,
    title: String,
    loadingMessage: String?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    controlsVisible: Boolean,
    isLocked: Boolean,
    lockVisible: Boolean,
    gestureHint: PlayerGestureHint?,
    onBackClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlaybackInfoClick: () -> Unit,
    onToggleControls: () -> Unit,
    onLockClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onGestureEnd: () -> Unit,
) {
    val pic = detailPic.ifBlank { thumbnailUrl }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Thumbnail / loading / error overlay
        val showOverlay = playerState !is PlayerUiState.Ready
        if (showOverlay && pic.isNotBlank()) {
            AsyncImage(
                model = pic,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        if (loadingMessage != null) {
            PlayerLoadingOverlay(
                message = loadingMessage,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (playerState is PlayerUiState.Error) {
            Text(
                text = playerState.message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }

        // Controls overlay (only when Ready)
        if (playerState is PlayerUiState.Ready) {
            PlayerControlsOverlay(
                isFullscreen = false,
                title = title,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPercentage = bufferedPercentage,
                controlsVisible = controlsVisible,
                isLocked = isLocked,
                lockVisible = lockVisible,
                gestureHint = gestureHint,
                hasEpisodes = false,
                hasPrevious = false,
                hasNext = false,
                onToggleControls = onToggleControls,
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick,
                onPlaybackInfoClick = onPlaybackInfoClick,
                onLockClick = onLockClick,
                onTogglePlay = onTogglePlay,
                onSeekTo = onSeekTo,
                onSeekPreview = onSeekPreview,
                onVolumeChange = onVolumeChange,
                onBrightnessChange = onBrightnessChange,
                onGestureEnd = onGestureEnd,
                onFullscreenClick = onFullscreenClick,
                onPreviousEpisode = {},
                onNextEpisode = {},
                onSelectEpisode = {},
            )
        }
    }
}

@Composable
fun FullscreenPlayerSection(
    exoPlayer: ExoPlayer,
    title: String,
    loadingMessage: String?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    controlsVisible: Boolean,
    isLocked: Boolean,
    lockVisible: Boolean,
    gestureHint: PlayerGestureHint?,
    hasEpisodes: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onToggleControls: () -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlaybackInfoClick: () -> Unit,
    onLockClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onGestureEnd: () -> Unit,
    onFullscreenClick: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectEpisode: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (loadingMessage != null) {
            PlayerLoadingOverlay(
                message = loadingMessage,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        PlayerControlsOverlay(
            isFullscreen = true,
            title = title,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            bufferedPercentage = bufferedPercentage,
            controlsVisible = controlsVisible,
            isLocked = isLocked,
            lockVisible = lockVisible,
            gestureHint = gestureHint,
            hasEpisodes = hasEpisodes,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            onToggleControls = onToggleControls,
            onBackClick = onBackClick,
            onSettingsClick = onSettingsClick,
            onPlaybackInfoClick = onPlaybackInfoClick,
            onLockClick = onLockClick,
            onTogglePlay = onTogglePlay,
            onSeekTo = onSeekTo,
            onSeekPreview = onSeekPreview,
            onVolumeChange = onVolumeChange,
            onBrightnessChange = onBrightnessChange,
            onGestureEnd = onGestureEnd,
            onFullscreenClick = onFullscreenClick,
            onPreviousEpisode = onPreviousEpisode,
            onNextEpisode = onNextEpisode,
            onSelectEpisode = onSelectEpisode,
        )
    }
}

@Composable
fun resolvePlayerLoadingMessage(playerState: PlayerUiState, playbackState: Int): String? = when {
    playerState is PlayerUiState.Loading -> stringResource(R.string.common_loading)
    playerState is PlayerUiState.Ready && playbackState == Player.STATE_BUFFERING -> stringResource(R.string.common_buffering)
    else -> null
}

@Composable
fun PlayerLoadingOverlay(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = Color.White,
        )
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

data class PlayerGestureHint(
    val title: String,
    val progress: Float,
    val valueText: String = "${(progress * 100).roundToInt()}%",
)

private enum class PlayerGestureMode { None, Seek, Volume, Brightness }

private fun Modifier.playerGestureControls(
    enabled: Boolean,
    currentPosition: () -> Long,
    duration: () -> Long,
    onSeekPreview: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onGestureEnd: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(enabled) {
        var mode = PlayerGestureMode.None
        var seekPosition = currentPosition()
        var accumulatedX = 0f
        var accumulatedY = 0f

        detectDragGestures(
            onDragStart = {
                mode = PlayerGestureMode.None
                seekPosition = currentPosition()
                accumulatedX = 0f
                accumulatedY = 0f
            },
            onDragEnd = {
                if (mode == PlayerGestureMode.Seek) {
                    onSeekTo(seekPosition)
                }
                mode = PlayerGestureMode.None
                onGestureEnd()
            },
            onDragCancel = {
                mode = PlayerGestureMode.None
                onGestureEnd()
            },
        ) { change, dragAmount ->
            accumulatedX += dragAmount.x
            accumulatedY += dragAmount.y
            if (mode == PlayerGestureMode.None) {
                if (abs(accumulatedX) < PLAYER_GESTURE_SLOP_PX && abs(accumulatedY) < PLAYER_GESTURE_SLOP_PX) {
                    return@detectDragGestures
                }
                mode = if (abs(accumulatedX) >= abs(accumulatedY)) {
                    PlayerGestureMode.Seek
                } else if (change.position.x < size.width / 2f) {
                    PlayerGestureMode.Brightness
                } else {
                    PlayerGestureMode.Volume
                }
            }

            when (mode) {
                PlayerGestureMode.Seek -> {
                    val safeDuration = duration()
                    if (safeDuration > 0L) {
                        val deltaPosition = (dragAmount.x / size.width * safeDuration).toLong()
                        seekPosition = (seekPosition + deltaPosition).coerceIn(0L, safeDuration)
                        onSeekPreview(seekPosition)
                    }
                }
                PlayerGestureMode.Volume -> {
                    onVolumeChange((-dragAmount.y / size.height).coerceIn(-1f, 1f))
                }
                PlayerGestureMode.Brightness -> {
                    onBrightnessChange((-dragAmount.y / size.height).coerceIn(-1f, 1f))
                }
                PlayerGestureMode.None -> Unit
            }
            change.consume()
        }
    }
}

@Composable
private fun PlayerGestureOverlay(
    hint: PlayerGestureHint,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xAA000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = hint.title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = hint.valueText,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Custom Compose player controls overlay
// ---------------------------------------------------------------------------

@Composable
fun PlayerControlsOverlay(
    isFullscreen: Boolean,
    title: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    controlsVisible: Boolean,
    isLocked: Boolean,
    lockVisible: Boolean,
    gestureHint: PlayerGestureHint?,
    hasEpisodes: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onToggleControls: () -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlaybackInfoClick: () -> Unit,
    onLockClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onGestureEnd: () -> Unit,
    onFullscreenClick: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectEpisode: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnSeekPreview by rememberUpdatedState(onSeekPreview)
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val currentOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val currentOnGestureEnd by rememberUpdatedState(onGestureEnd)
    val latestPosition by rememberUpdatedState(currentPosition)
    val latestDuration by rememberUpdatedState(duration)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .playerGestureControls(
                enabled = !isLocked,
                currentPosition = { latestPosition },
                duration = { latestDuration },
                onSeekPreview = { currentOnSeekPreview(it) },
                onSeekTo = { currentOnSeekTo(it) },
                onVolumeChange = { currentOnVolumeChange(it) },
                onBrightnessChange = { currentOnBrightnessChange(it) },
                onGestureEnd = { currentOnGestureEnd() },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggleControls,
            ),
    ) {
        // ---- Lock button (right center vertical) ----
        if (isLocked) {
            AnimatedVisibility(
                visible = lockVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = PLAYER_EDGE_PADDING),
            ) {
                IconButton(onClick = onLockClick) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.player_unlock),
                        tint = Color.White,
                    )
                }
            }
        }

        // ---- Controls (hidden when locked) ----
        if (!isLocked) {
            // Top bar: back + title (left) | settings (right)
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x99000000), Color.Transparent),
                            ),
                        )
                        .padding(horizontal = PLAYER_EDGE_PADDING, vertical = PLAYER_EDGE_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onPlaybackInfoClick) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.player_info),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.player_settings),
                            tint = Color.White,
                        )
                    }
                }
            }

            // Bottom bar
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            ) {
                PlayerBottomBar(
                    isFullscreen = isFullscreen,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    bufferedPercentage = bufferedPercentage,
                    hasEpisodes = hasEpisodes,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onTogglePlay = onTogglePlay,
                    onSeekTo = onSeekTo,
                    onFullscreenClick = onFullscreenClick,
                    onPreviousEpisode = onPreviousEpisode,
                    onNextEpisode = onNextEpisode,
                    onSelectEpisode = onSelectEpisode,
                )
            }
        }

        // ---- Lock button (non-locked state: show when controls visible) ----
        if (!isLocked && controlsVisible) {
            IconButton(
                onClick = onLockClick,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = PLAYER_EDGE_PADDING),
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = stringResource(R.string.player_lock),
                    tint = Color.White,
                )
            }
        }

        if (gestureHint != null) {
            PlayerGestureOverlay(
                hint = gestureHint,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

}

@Composable
fun PlaybackInfoSheetContent(
    info: PlaybackMediaInfo,
) {
    SelectionContainer {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.player_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            val unknown = stringResource(R.string.common_unknown)
            PlaybackInfoRow(stringResource(R.string.player_url), info.url.ifBlank { unknown })
            PlaybackInfoRow(stringResource(R.string.player_resolution), info.resolutionText(unknown))
            PlaybackInfoRow(stringResource(R.string.player_codec), info.codecText(unknown))
            PlaybackInfoRow(stringResource(R.string.player_bitrate), info.bitrateText(unknown))
            PlaybackInfoRow(stringResource(R.string.player_frame_rate), info.frameRateText(unknown))
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlaybackInfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun PlaybackMediaInfo.resolutionText(unknown: String): String =
    if (width > 0 && height > 0) "${width}*${height}" else unknown

private fun PlaybackMediaInfo.codecText(unknown: String): String = when {
    !codecs.isNullOrBlank() && !sampleMimeType.isNullOrBlank() -> "$codecs ($sampleMimeType)"
    !codecs.isNullOrBlank() -> codecs
    !sampleMimeType.isNullOrBlank() -> sampleMimeType
    else -> unknown
}

private fun PlaybackMediaInfo.bitrateText(unknown: String): String =
    if (bitrate > 0) "${bitrate / 1000} kbps" else unknown

private fun PlaybackMediaInfo.frameRateText(unknown: String): String =
    if (frameRate > 0f) String.format("%.2f fps", frameRate) else unknown

// ---------------------------------------------------------------------------
// Bottom bar (play/pause, [prev/next/select ep in fullscreen], seekbar, time)
// ---------------------------------------------------------------------------

@Composable
private fun PlayerBottomBar(
    isFullscreen: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    hasEpisodes: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onFullscreenClick: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectEpisode: () -> Unit,
) {
    val safeDuration = if (duration > 0) duration else 1L
    val sliderValue = (currentPosition.toFloat() / safeDuration).coerceIn(0f, 1f)
    val bufferedValue = (bufferedPercentage / 100f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x99000000)),
                ),
            )
            .padding(horizontal = PLAYER_EDGE_PADDING, vertical = PLAYER_EDGE_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.player_pause)
                else stringResource(R.string.player_play),
                tint = Color.White,
            )
        }

        if (isFullscreen && hasEpisodes) {
            IconButton(onClick = onPreviousEpisode, enabled = hasPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.player_previous_episode),
                    tint = if (hasPrevious) Color.White else Color(0x55FFFFFF),
                )
            }
        }

        Text(
            text = formatTime(currentPosition),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        ThinProgressSlider(
            progress = sliderValue,
            buffered = bufferedValue,
            onProgressChange = { fraction ->
                onSeekTo((fraction * safeDuration).toLong())
            },
            modifier = Modifier.weight(1f),
        )

        Text(
            text = formatTime(duration),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        if (isFullscreen && hasEpisodes) {
            IconButton(onClick = onNextEpisode, enabled = hasNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.player_next_episode),
                    tint = if (hasNext) Color.White else Color(0x55FFFFFF),
                )
            }
            IconButton(onClick = onSelectEpisode) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = stringResource(R.string.detail_episode_selection),
                    tint = Color.White,
                )
            }
        }

        IconButton(onClick = onFullscreenClick) {
            Icon(
                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = if (isFullscreen) stringResource(R.string.player_exit_fullscreen)
                else stringResource(R.string.player_fullscreen),
                tint = Color.White,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Detail content (below player in non-fullscreen)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaybackSettingsSheetContent(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.player_settings),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            text = stringResource(R.string.player_speed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            speeds.forEach { speed ->
                val isSelected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onSpeedSelected(speed) },
                ) {
                    Text(
                        text = "${speed}x",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// Utils
// ---------------------------------------------------------------------------

@Composable
private fun ThinProgressSlider(
    progress: Float,
    buffered: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { 2.dp.toPx() }
    val thumbRadiusPx = with(density) { 5.dp.toPx() }

    Box(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(fraction)
                    },
                ) { change, _ ->
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onProgressChange(fraction)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val centerY = size.height / 2f

            drawRoundRect(
                color = Color(0x55FFFFFF),
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = androidx.compose.ui.geometry.Size(canvasWidth, trackHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f),
            )

            drawRoundRect(
                color = Color(0x88FFFFFF),
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = androidx.compose.ui.geometry.Size(canvasWidth * buffered, trackHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f),
            )

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = androidx.compose.ui.geometry.Size(canvasWidth * progress, trackHeightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f),
            )

            drawCircle(
                color = Color.White,
                radius = thumbRadiusPx,
                center = Offset(canvasWidth * progress, centerY),
            )
        }
    }
}
