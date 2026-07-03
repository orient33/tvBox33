package com.github.tvbox.newbox.feature.detailplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.github.tvbox.newbox.domain.Episode
import com.github.tvbox.newbox.domain.VodDetail
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.ui.common.ErrorView
import com.github.tvbox.newbox.ui.common.LoadingView
import kotlinx.coroutines.delay

private const val INLINE_EPISODE_LIMIT = 20
private const val CONTROLS_AUTO_HIDE_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailPlayerScreen(
    vodItem: VodItem?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailPlayerViewModel = hiltViewModel(),
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val selectedFlagIndex by viewModel.selectedFlagIndex.collectAsStateWithLifecycle()
    val selectedEpisodeIndex by viewModel.selectedEpisodeIndex.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val dataSourceFactory = remember { DefaultHttpDataSource.Factory() }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory),
            )
            .build()
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // ---- Track ExoPlayer state ----
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPercentage by remember { mutableIntStateOf(0) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            bufferedPercentage = exoPlayer.bufferedPercentage
            if (duration == 0L) duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // ---- Controls visibility & lock state ----
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var lockVisible by remember { mutableStateOf(true) }

    // Auto-hide: only when playing, not locked, and controls are visible
    LaunchedEffect(controlsVisible, isLocked, isPlaying) {
        if (controlsVisible && !isLocked && isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // Lock auto-hide
    LaunchedEffect(lockVisible, isLocked) {
        if (lockVisible && isLocked) {
            delay(CONTROLS_AUTO_HIDE_MS)
            lockVisible = false
        }
    }

    // ---- Load detail ----
    LaunchedEffect(vodItem) {
        if (vodItem != null && vodItem.id.isNotBlank() && !vodItem.id.startsWith("msearch:")) {
            viewModel.loadDetail(vodItem.id, vodItem.sourceKey)
        }
    }

    // ---- Load media when player result ready ----
    LaunchedEffect(playerState) {
        if (playerState is PlayerUiState.Ready) {
            val result = (playerState as PlayerUiState.Ready).playerResult
            dataSourceFactory.setDefaultRequestProperties(result.headers)
            val mediaItem = MediaItem.Builder()
                .setUri(result.url)
                .setMimeType(guessMimeType(result.url))
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        }
    }

    BackHandler(enabled = isFullscreen) {
        viewModel.toggleFullscreen()
    }

    LaunchedEffect(isFullscreen) {
        val activity = context.findActivity() ?: return@LaunchedEffect
        activity.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(isFullscreen) {
        onDispose {
            val activity = context.findActivity() ?: return@onDispose
            if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            val window = activity.window
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---- Bottom sheets ----
    var showSynopsisSheet by rememberSaveable { mutableStateOf(false) }
    var showAllEpisodesSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    val detail = (detailState as? DetailUiState.Success)?.detail
    val flags = detail?.seriesFlags ?: emptyList()
    val flag = flags.getOrElse(selectedFlagIndex) { detail?.seriesMap?.keys?.firstOrNull() ?: "" }
    val episodes = detail?.seriesMap?.get(flag) ?: emptyList()
    val currentEpisodeName = selectedEpisodeIndex?.let { episodes.getOrNull(it)?.name } ?: ""
    val titleText = detail?.name ?: vodItem?.name ?: ""
    val fullscreenTitle = if (currentEpisodeName.isNotBlank()) "$titleText - $currentEpisodeName" else titleText

    val hasPrevious = (selectedEpisodeIndex ?: -1) > 0
    val nextIdx = selectedEpisodeIndex?.let { it + 1 }
    val hasNext = nextIdx != null && nextIdx < episodes.size

    // ---- Fullscreen layout ----
    if (isFullscreen) {
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

            PlayerControlsOverlay(
                isFullscreen = true,
                title = fullscreenTitle,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPercentage = bufferedPercentage,
                controlsVisible = controlsVisible,
                isLocked = isLocked,
                lockVisible = lockVisible,
                hasEpisodes = episodes.size > 1,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                onToggleControls = {
                    if (isLocked) {
                        lockVisible = true
                    } else {
                        controlsVisible = !controlsVisible
                    }
                },
                onBackClick = { viewModel.toggleFullscreen() },
                onSettingsClick = { showSettingsSheet = true },
                onLockClick = {
                    isLocked = !isLocked
                    if (isLocked) {
                        controlsVisible = false
                        lockVisible = true
                    } else {
                        controlsVisible = true
                        lockVisible = true
                    }
                },
                onTogglePlay = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    controlsVisible = true
                },
                onSeekTo = { pos ->
                    exoPlayer.seekTo(pos)
                    controlsVisible = true
                },
                onFullscreenClick = { viewModel.toggleFullscreen() },
                onPreviousEpisode = { viewModel.playPrevious() },
                onNextEpisode = { viewModel.playNext() },
                onSelectEpisode = { showAllEpisodesSheet = true },
            )
        }
        // Settings & episodes sheets available in fullscreen too
        if (showSettingsSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                sheetState = sheetState,
            ) {
                PlaybackSettingsSheetContent(
                    currentSpeed = exoPlayer.playbackParameters.speed,
                    onSpeedSelected = { speed ->
                        exoPlayer.setPlaybackSpeed(speed)
                        showSettingsSheet = false
                    },
                )
            }
        }
        if (showAllEpisodesSheet && detail != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showAllEpisodesSheet = false },
                sheetState = sheetState,
            ) {
                AllEpisodesSheetContent(
                    episodes = episodes,
                    selectedIndex = selectedEpisodeIndex,
                    onEpisodeSelected = { index ->
                        viewModel.selectEpisode(index)
                        showAllEpisodesSheet = false
                    },
                )
            }
        }
        return
    }

    // ---- Non-fullscreen layout ----
    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        PlayerSection(
            playerState = playerState,
            thumbnailUrl = vodItem?.pic ?: "",
            detailPic = detail?.pic ?: "",
            exoPlayer = exoPlayer,
            title = titleText,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            bufferedPercentage = bufferedPercentage,
            controlsVisible = controlsVisible,
            isLocked = isLocked,
            lockVisible = lockVisible,
            onBackClick = onBackClick,
            onFullscreenClick = { viewModel.toggleFullscreen() },
            onSettingsClick = { showSettingsSheet = true },
            onToggleControls = {
                if (isLocked) {
                    lockVisible = true
                } else {
                    controlsVisible = !controlsVisible
                }
            },
            onLockClick = {
                isLocked = !isLocked
                if (isLocked) {
                    controlsVisible = false
                    lockVisible = true
                } else {
                    controlsVisible = true
                    lockVisible = true
                }
            },
            onTogglePlay = {
                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                controlsVisible = true
            },
            onSeekTo = { pos ->
                exoPlayer.seekTo(pos)
                controlsVisible = true
            },
        )

        when (val state = detailState) {
            is DetailUiState.Idle -> {
                if (vodItem != null) LoadingView() else Text("未选择影片", modifier = Modifier.padding(16.dp))
            }
            is DetailUiState.Loading -> LoadingView()
            is DetailUiState.Error -> ErrorView(message = state.message)
            is DetailUiState.Success -> DetailContent(
                detail = state.detail,
                selectedFlagIndex = selectedFlagIndex,
                selectedEpisodeIndex = selectedEpisodeIndex,
                onFlagSelected = { viewModel.selectFlag(it) },
                onEpisodeSelected = { viewModel.selectEpisode(it) },
                onSynopsisClick = { showSynopsisSheet = true },
                onViewAllEpisodes = { showAllEpisodesSheet = true },
            )
        }
    }

    // ---- Bottom sheets (non-fullscreen) ----
    if (showSynopsisSheet && detail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSynopsisSheet = false },
            sheetState = sheetState,
        ) {
            SynopsisSheetContent(detail = detail)
        }
    }

    if (showAllEpisodesSheet && detail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAllEpisodesSheet = false },
            sheetState = sheetState,
        ) {
            AllEpisodesSheetContent(
                episodes = episodes,
                selectedIndex = selectedEpisodeIndex,
                onEpisodeSelected = { index ->
                    viewModel.selectEpisode(index)
                    showAllEpisodesSheet = false
                },
            )
        }
    }

    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
        ) {
            PlaybackSettingsSheetContent(
                currentSpeed = exoPlayer.playbackParameters.speed,
                onSpeedSelected = { speed ->
                    exoPlayer.setPlaybackSpeed(speed)
                    showSettingsSheet = false
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Player section (non-fullscreen 16:9 box with overlay controls)
// ---------------------------------------------------------------------------

@Composable
private fun PlayerSection(
    playerState: PlayerUiState,
    thumbnailUrl: String,
    detailPic: String,
    exoPlayer: ExoPlayer,
    title: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    controlsVisible: Boolean,
    isLocked: Boolean,
    lockVisible: Boolean,
    onBackClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleControls: () -> Unit,
    onLockClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
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
        if (playerState is PlayerUiState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White,
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
                hasEpisodes = false,
                hasPrevious = false,
                hasNext = false,
                onToggleControls = onToggleControls,
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick,
                onLockClick = onLockClick,
                onTogglePlay = onTogglePlay,
                onSeekTo = onSeekTo,
                onFullscreenClick = onFullscreenClick,
                onPreviousEpisode = {},
                onNextEpisode = {},
                onSelectEpisode = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Custom Compose player controls overlay
// ---------------------------------------------------------------------------

@Composable
private fun PlayerControlsOverlay(
    isFullscreen: Boolean,
    title: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int,
    controlsVisible: Boolean,
    isLocked: Boolean,
    lockVisible: Boolean,
    hasEpisodes: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onToggleControls: () -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLockClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onFullscreenClick: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectEpisode: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            ) {
                IconButton(onClick = onLockClick) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "解锁",
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
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
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
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "播放设置",
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
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "锁屏",
                    tint = Color.White,
                )
            }
        }
    }
}

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x99000000)),
                ),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                )
            }

            if (isFullscreen && hasEpisodes) {
                IconButton(onClick = onPreviousEpisode, enabled = hasPrevious) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一集",
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
                        contentDescription = "下一集",
                        tint = if (hasNext) Color.White else Color(0x55FFFFFF),
                    )
                }
                IconButton(onClick = onSelectEpisode) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = "选集",
                        tint = Color.White,
                    )
                }
            }

            IconButton(onClick = onFullscreenClick) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                    tint = Color.White,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Detail content (below player in non-fullscreen)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: VodDetail,
    selectedFlagIndex: Int,
    selectedEpisodeIndex: Int?,
    onFlagSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onSynopsisClick: () -> Unit,
    onViewAllEpisodes: () -> Unit,
) {
    val flags = detail.seriesFlags
    val flag = flags.getOrElse(selectedFlagIndex) { detail.seriesMap.keys.firstOrNull() ?: "" }
    val episodes = detail.seriesMap[flag] ?: emptyList()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item {
            val metaParts = buildList {
                if (episodes.isNotEmpty()) add("${episodes.size}集")
                if (detail.year.isNotBlank()) add(detail.year)
                if (detail.type.isNotBlank()) add(detail.type)
                if (detail.area.isNotBlank()) add(detail.area)
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (detail.description.isNotBlank()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSynopsisClick() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = detail.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "详情 >>",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (flags.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    itemsIndexed(flags) { index, flagName ->
                        FilterChip(
                            selected = index == selectedFlagIndex,
                            onClick = { onFlagSelected(index) },
                            label = { Text(flagName) },
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选集",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (episodes.isNotEmpty()) {
                    Text(
                        text = "查看全部",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onViewAllEpisodes() },
                    )
                }
            }
        }

        if (episodes.isEmpty()) {
            item {
                Text(
                    text = "暂无播放数据",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val limit = minOf(episodes.size, INLINE_EPISODE_LIMIT)
                    repeat(limit) { index ->
                        EpisodeCard(
                            episode = episodes[index],
                            isSelected = index == selectedEpisodeIndex,
                            onClick = { onEpisodeSelected(index) },
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Sheet contents
// ---------------------------------------------------------------------------

@Composable
private fun EpisodeCard(
    episode: Episode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = episode.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SynopsisSheetContent(detail: VodDetail) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = detail.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (detail.director.isNotBlank()) {
            item {
                Text(
                    text = "导演: ${detail.director}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (detail.actor.isNotBlank()) {
            item {
                Text(
                    text = "主演: ${detail.actor}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (detail.description.isNotBlank()) {
            item {
                Text(
                    text = "简介",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun AllEpisodesSheetContent(
    episodes: List<Episode>,
    selectedIndex: Int?,
    onEpisodeSelected: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 80.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItemsIndexed(episodes) { index, episode ->
            EpisodeCard(
                episode = episode,
                isSelected = index == selectedIndex,
                onClick = { onEpisodeSelected(index) },
            )
        }
    }
    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun PlaybackSettingsSheetContent(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "播放设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            text = "倍速播放",
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

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun guessMimeType(url: String): String? {
    val lower = url.lowercase()
    return when {
        lower.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
        lower.contains(".mpd") -> MimeTypes.APPLICATION_MPD
        lower.contains(".mp4") || lower.contains(".mkv") -> MimeTypes.APPLICATION_MP4
        lower.contains(".flv") -> MimeTypes.VIDEO_FLV
        lower.contains(".ts") -> MimeTypes.VIDEO_MP2T
        else -> null
    }
}
