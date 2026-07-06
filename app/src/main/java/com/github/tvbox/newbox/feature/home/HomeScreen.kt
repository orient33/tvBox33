package com.github.tvbox.newbox.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tvbox.newbox.domain.FilterGroup
import com.github.tvbox.newbox.domain.SourceConfig
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.ui.common.LoadingView
import com.github.tvbox.newbox.ui.common.VodCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVodClick: (VodItem) -> Unit,
    onSearchClick: () -> Unit,
    onMineClick: () -> Unit = {},
    onSubscriptionClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSource by viewModel.currentSource.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val selectedFilters by viewModel.selectedFilters.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val isReloading by viewModel.isReloading.collectAsStateWithLifecycle()
    var showSourceDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                        .then(
                            if (sources.size > 1) Modifier.clickable { showSourceDialog = true }
                            else Modifier
                        ),
                ) {
                    Text(
                        text = currentSource?.name ?: "NewBox",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (sources.size > 1) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "切换源",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
                IconButton(onClick = onMineClick) {
                    Icon(Icons.Default.Person, contentDescription = "我的")
                }
            },
        )

        when (val state = uiState) {
            is HomeUiState.Loading -> LoadingView()
            is HomeUiState.Error -> {
                val isNoSource = state.message.contains("No source selected")
                if (isNoSource) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onSubscriptionClick() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无视频源",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "点击右上角头像添加订阅",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "加载失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = { viewModel.retry() }) {
                                Text("重试")
                            }
                            if (sources.size > 1) {
                                TextButton(onClick = { showSourceDialog = true }) {
                                    Text("切换源")
                                }
                            }
                            TextButton(onClick = onMineClick) {
                                Text("我的")
                            }
                        }
                    }
                }
            }
            is HomeUiState.Success -> HomeContent(
                homeContent = state.homeContent,
                selectedCategoryId = selectedCategoryId,
                selectedFilters = selectedFilters,
                isLoadingMore = isLoadingMore,
                isReloading = isReloading,
                onCategoryClick = viewModel::selectCategory,
                onFilterClick = viewModel::selectFilter,
                onLoadMore = viewModel::loadMore,
                onVodClick = onVodClick,
            )
        }
    }

    if (showSourceDialog && sources.isNotEmpty()) {
        SourceSwitchDialog(
            sources = sources,
            currentKey = currentSource?.key,
            onSelect = { key ->
                viewModel.selectSource(key)
                showSourceDialog = false
            },
            onDismiss = { showSourceDialog = false },
        )
    }
}

@Composable
private fun SourceSwitchDialog(
    sources: List<SourceConfig>,
    currentKey: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换源") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sources.size) { index ->
                    val source = sources[index]
                    val isSelected = source.key == currentKey
                    TextButton(
                        onClick = { onSelect(source.key) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isSelected) "● ${index + 1}. ${source.name}" else "${index + 1}. ${source.name}",
                            style = if (isSelected) MaterialTheme.typography.bodyLarge
                            else MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun HomeContent(
    homeContent: com.github.tvbox.newbox.domain.HomeContent,
    selectedCategoryId: String?,
    selectedFilters: Map<String, String>,
    isLoadingMore: Boolean,
    isReloading: Boolean,
    onCategoryClick: (String?) -> Unit,
    onFilterClick: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    onVodClick: (VodItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val hasMore = selectedCategoryId != null &&
        (homeContent.page.toIntOrNull() ?: 1) < (homeContent.pageCount.toIntOrNull() ?: 1)
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 6
        }
    }

    LaunchedEffect(shouldLoadMore, hasMore, isLoadingMore) {
        if (shouldLoadMore && hasMore && !isLoadingMore) {
            onLoadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (homeContent.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onCategoryClick(null) },
                        label = { Text("主页") },
                    )
                }
                items(homeContent.categories) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { onCategoryClick(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }
        val categoryFilters = selectedCategoryId?.let { homeContent.filters[it] }.orEmpty()
        if (categoryFilters.isNotEmpty()) {
            CategoryFilters(
                filters = categoryFilters,
                selectedFilters = selectedFilters,
                onFilterClick = onFilterClick,
            )
        }

        if (isReloading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (homeContent.videos.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "当前源暂无内容",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "请尝试切换其他源或分类",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(homeContent.videos) { vod ->
                    VodCard(item = vod, onClick = onVodClick)
                }
                if (isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilters(
    filters: List<FilterGroup>,
    selectedFilters: Map<String, String>,
    onFilterClick: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        filters.forEach { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 6.dp),
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(group.items) { item ->
                        FilterChip(
                            selected = selectedFilters[group.key] == item.value,
                            onClick = { onFilterClick(group.key, item.value) },
                            label = {
                                Text(
                                    text = item.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
