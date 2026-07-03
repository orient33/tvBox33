package com.github.tvbox.newbox.feature.detailplayer

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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

private const val INLINE_EPISODE_LIMIT = 20

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

    LaunchedEffect(vodItem) {
        if (vodItem != null && vodItem.id.isNotBlank() && !vodItem.id.startsWith("msearch:")) {
            viewModel.loadDetail(vodItem.id, vodItem.sourceKey)
        }
    }

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

    var showSynopsisSheet by rememberSaveable { mutableStateOf(false) }
    var showAllEpisodesSheet by rememberSaveable { mutableStateOf(false) }

    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = true
                        player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = { viewModel.toggleFullscreen() },
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出全屏", tint = Color.White)
            }
        }
        return
    }

    val detail = (detailState as? DetailUiState.Success)?.detail

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        PlayerSection(
            playerState = playerState,
            thumbnailUrl = vodItem?.pic ?: "",
            detailPic = detail?.pic ?: "",
            exoPlayer = exoPlayer,
            onBackClick = onBackClick,
            onFullscreenClick = { viewModel.toggleFullscreen() },
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
        val flags = detail.seriesFlags
        val flag = flags.getOrElse(selectedFlagIndex) { detail.seriesMap.keys.firstOrNull() ?: "" }
        val episodes = detail.seriesMap[flag] ?: emptyList()
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
}

@Composable
private fun PlayerSection(
    playerState: PlayerUiState,
    thumbnailUrl: String,
    detailPic: String,
    exoPlayer: ExoPlayer,
    onBackClick: () -> Unit,
    onFullscreenClick: () -> Unit,
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
                    useController = true
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val overlay = when (playerState) {
            is PlayerUiState.Idle -> true
            is PlayerUiState.Loading -> true
            is PlayerUiState.Error -> true
            is PlayerUiState.Ready -> false
        }
        if (overlay && pic.isNotBlank()) {
            AsyncImage(
                model = pic,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
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

        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }

        if (playerState is PlayerUiState.Ready) {
            IconButton(
                onClick = onFullscreenClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "全屏", tint = Color.White)
            }
        }
    }
}

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
