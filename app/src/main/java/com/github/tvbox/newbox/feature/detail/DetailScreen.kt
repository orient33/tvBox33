package com.github.tvbox.newbox.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.ui.common.ErrorView
import com.github.tvbox.newbox.ui.common.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vodItem: VodItem?,
    onBackClick: () -> Unit,
    onPlayClick: (flag: String, url: String, sourceKey: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(vodItem) {
        if (vodItem != null) viewModel.loadDetail(vodItem.id, vodItem.sourceKey)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = vodItem?.name ?: "详情",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        when (val state = uiState) {
            is DetailUiState.Idle -> {
                if (vodItem != null) LoadingView() else Text("未选择影片", modifier = Modifier.padding(16.dp))
            }
            is DetailUiState.Loading -> LoadingView()
            is DetailUiState.Error -> ErrorView(message = state.message)
            is DetailUiState.Success -> DetailContent(
                detail = state.detail,
                pic = vodItem?.pic ?: "",
                onPlayClick = onPlayClick,
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: com.github.tvbox.newbox.domain.VodDetail,
    pic: String,
    onPlayClick: (flag: String, url: String, sourceKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFlagIndex by rememberSaveable { mutableIntStateOf(0) }
    val flags = detail.seriesFlags
    val currentEpisodes = if (flags.isNotEmpty() && detail.seriesMap.containsKey(flags[selectedFlagIndex])) {
        detail.seriesMap[flags[selectedFlagIndex]] ?: emptyList()
    } else {
        detail.seriesMap.values.firstOrNull() ?: emptyList()
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AsyncImage(
                    model = detail.pic.ifBlank { pic },
                    contentDescription = detail.name,
                    modifier = Modifier.width(120.dp).height(180.dp).clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = detail.name, style = MaterialTheme.typography.headlineSmall)
                    if (detail.type.isNotBlank()) Text("类型: ${detail.type}", style = MaterialTheme.typography.bodySmall)
                    if (detail.year.isNotBlank()) Text("年份: ${detail.year}", style = MaterialTheme.typography.bodySmall)
                    if (detail.area.isNotBlank()) Text("地区: ${detail.area}", style = MaterialTheme.typography.bodySmall)
                    if (detail.director.isNotBlank()) Text("导演: ${detail.director}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (detail.actor.isNotBlank()) Text("演员: ${detail.actor}", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (detail.description.isNotBlank()) {
            item {
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (flags.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(flags) { index, flag ->
                        FilterChip(
                            selected = index == selectedFlagIndex,
                            onClick = { selectedFlagIndex = index },
                            label = { Text(flag) },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "选集",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        items(currentEpisodes) { episode ->
            OutlinedButton(
                onClick = {
                    val flag = flags.getOrElse(selectedFlagIndex) { "" }
                    onPlayClick(flag, episode.url, detail.sourceKey)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Text(episode.name)
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
