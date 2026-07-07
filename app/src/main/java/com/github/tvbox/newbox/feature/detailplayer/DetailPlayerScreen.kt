package com.github.tvbox.newbox.feature.detailplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.ui.common.ErrorView
import com.github.tvbox.newbox.ui.common.LoadingView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    val resumePosition by viewModel.resumePosition.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val isCollected by viewModel.isCollected.collectAsStateWithLifecycle()

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
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPercentage by remember { mutableIntStateOf(0) }
    var playbackUrl by remember { mutableStateOf("") }
    var playbackMediaInfo by remember { mutableStateOf(PlaybackMediaInfo()) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { playbackState = state }
        }
        playbackState = exoPlayer.playbackState
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            bufferedPercentage = exoPlayer.bufferedPercentage
            if (duration == 0L) duration = exoPlayer.duration.coerceAtLeast(0L)
            playbackMediaInfo = buildPlaybackMediaInfo(playbackUrl, exoPlayer.videoFormat)
            delay(500)
        }
    }

    // ---- Controls visibility & lock state ----
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var lockVisible by remember { mutableStateOf(true) }
    var gestureHint by remember { mutableStateOf<PlayerGestureHint?>(null) }
    var isAdjustingGesture by remember { mutableStateOf(false) }
    var volumeGestureProgress by remember { mutableStateOf<Float?>(null) }

    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context.findActivity()

    DisposableEffect(activity, isPlaying) {
        val window = activity?.window
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val updateVolume: (Float) -> Unit = { delta ->
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val currentProgress = volumeGestureProgress
            ?: (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
        val nextProgress = (currentProgress + delta * 1.5f).coerceIn(0f, 1f)
        volumeGestureProgress = nextProgress
        val nextVolume = (nextProgress * maxVolume).roundToInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVolume, 0)
        isAdjustingGesture = true
        gestureHint = PlayerGestureHint("音量", nextProgress)
    }
    val updateBrightness: (Float) -> Unit = { delta ->
        val window = activity?.window
        if (window != null) {
            val attrs = window.attributes
            val currentBrightness = if (attrs.screenBrightness >= 0f) attrs.screenBrightness else 0.5f
            val nextBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
            attrs.screenBrightness = nextBrightness
            window.attributes = attrs
            isAdjustingGesture = true
            gestureHint = PlayerGestureHint("亮度", nextBrightness)
        }
    }

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
            playbackUrl = result.url
            playbackMediaInfo = buildPlaybackMediaInfo(result.url, exoPlayer.videoFormat)
            dataSourceFactory.setDefaultRequestProperties(result.headers)
            val mediaItem = MediaItem.Builder()
                .setUri(result.url)
                .setMimeType(guessMimeType(result.url))
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
            if (resumePosition > 0L) {
                exoPlayer.seekTo(resumePosition)
            }
        }
    }

    // ---- Record play history after 10s of continuous playback ----
    var recordedEpisodeKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isPlaying, selectedEpisodeIndex, playerState) {
        val episodeKey = selectedEpisodeIndex?.toString()
        if (playerState is PlayerUiState.Ready && isPlaying && episodeKey != null) {
            while (true) {
                delay(10_000)
                viewModel.recordHistory(exoPlayer.currentPosition)
                recordedEpisodeKey = episodeKey
            }
        }
    }

    LaunchedEffect(isPlaying, selectedEpisodeIndex) {
        if (!isPlaying && playerState is PlayerUiState.Ready && recordedEpisodeKey == selectedEpisodeIndex?.toString()) {
            viewModel.recordHistory(exoPlayer.currentPosition)
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
    var showPlaybackInfoSheet by rememberSaveable { mutableStateOf(false) }

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
    val playerLoadingMessage = resolvePlayerLoadingMessage(playerState, playbackState)

    // ---- Fullscreen layout ----
    if (isFullscreen) {
        FullscreenPlayerSection(
            exoPlayer = exoPlayer,
            title = fullscreenTitle,
            loadingMessage = playerLoadingMessage,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            bufferedPercentage = bufferedPercentage,
            controlsVisible = controlsVisible,
            isLocked = isLocked,
            lockVisible = lockVisible,
            gestureHint = gestureHint,
            hasEpisodes = episodes.size > 1,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            onToggleControls = {
                if (isAdjustingGesture) {
                    isAdjustingGesture = false
                } else if (isLocked) {
                    lockVisible = true
                } else {
                    controlsVisible = !controlsVisible
                }
            },
            onBackClick = { viewModel.toggleFullscreen() },
            onSettingsClick = { showSettingsSheet = true },
            onPlaybackInfoClick = { showPlaybackInfoSheet = true },
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
                currentPosition = pos
                controlsVisible = true
            },
            onSeekPreview = { pos ->
                isAdjustingGesture = true
                currentPosition = pos
                gestureHint = PlayerGestureHint("进度", if (duration > 0L) pos.toFloat() / duration else 0f, formatTime(pos))
            },
            onVolumeChange = updateVolume,
            onBrightnessChange = updateBrightness,
            onGestureEnd = {
                gestureHint = null
                volumeGestureProgress = null
            },
            onFullscreenClick = { viewModel.toggleFullscreen() },
            onPreviousEpisode = { viewModel.playPrevious() },
            onNextEpisode = { viewModel.playNext() },
            onSelectEpisode = { showAllEpisodesSheet = true },
        )
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
        if (showPlaybackInfoSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showPlaybackInfoSheet = false },
                sheetState = sheetState,
            ) {
                PlaybackInfoSheetContent(info = playbackMediaInfo)
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
            loadingMessage = playerLoadingMessage,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            bufferedPercentage = bufferedPercentage,
            controlsVisible = controlsVisible,
            isLocked = isLocked,
            lockVisible = lockVisible,
            gestureHint = gestureHint,
            onBackClick = onBackClick,
            onFullscreenClick = { viewModel.toggleFullscreen() },
            onSettingsClick = { showSettingsSheet = true },
            onPlaybackInfoClick = { showPlaybackInfoSheet = true },
            onToggleControls = {
                if (isAdjustingGesture) {
                    isAdjustingGesture = false
                } else if (isLocked) {
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
                currentPosition = pos
                controlsVisible = true
            },
            onSeekPreview = { pos ->
                isAdjustingGesture = true
                currentPosition = pos
                gestureHint = PlayerGestureHint("进度", if (duration > 0L) pos.toFloat() / duration else 0f, formatTime(pos))
            },
            onVolumeChange = updateVolume,
            onBrightnessChange = updateBrightness,
            onGestureEnd = {
                gestureHint = null
                volumeGestureProgress = null
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
            SynopsisSheetContent(
                detail = detail,
                isCollected = isCollected,
                onToggleCollect = { viewModel.toggleCollect() },
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

    if (showPlaybackInfoSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlaybackInfoSheet = false },
            sheetState = sheetState,
        ) {
            PlaybackInfoSheetContent(info = playbackMediaInfo)
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

private fun buildPlaybackMediaInfo(url: String, format: Format?): PlaybackMediaInfo = PlaybackMediaInfo(
    url = url,
    width = format?.width?.takeIf { it > 0 } ?: 0,
    height = format?.height?.takeIf { it > 0 } ?: 0,
    sampleMimeType = format?.sampleMimeType,
    codecs = format?.codecs,
    bitrate = format?.bitrate?.takeIf { it > 0 } ?: 0,
    frameRate = format?.frameRate?.takeIf { it > 0f } ?: 0f,
)

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
