package com.github.tvbox.newbox.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tvbox.newbox.domain.VodItem
import com.github.tvbox.newbox.ui.common.ErrorView
import com.github.tvbox.newbox.ui.common.LoadingView
import com.github.tvbox.newbox.ui.common.VodCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVodClick: (VodItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var active by rememberSaveable { mutableStateOf(initialQuery.isEmpty()) }
    var selectedSourceKey by rememberSaveable { mutableStateOf<String?>(null) }

    if (initialQuery.isNotBlank()) {
        LaunchedEffect(initialQuery) {
            selectedSourceKey = null
            viewModel.search(initialQuery)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onSearch = {
                active = false
                selectedSourceKey = null
                viewModel.search(it)
            },
            active = active,
            onActiveChange = { active = it },
            placeholder = { Text("搜索影片…") },
            leadingIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            // Search suggestions placeholder
        }

        when (val state = uiState) {
            is SearchUiState.Idle -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("输入关键词搜索", style = MaterialTheme.typography.bodyLarge)
                }
            }
            is SearchUiState.Loading -> LoadingView()
            is SearchUiState.Error -> ErrorView(message = state.message)
            is SearchUiState.Searching -> SearchResultContent(
                results = state.results,
                selectedSourceKey = selectedSourceKey,
                onSourceSelect = { selectedSourceKey = it },
                onVodClick = onVodClick,
                progressText = "${state.completedSources}/${state.totalSources}",
            )
            is SearchUiState.Success -> {
                SearchResultContent(
                    results = state.results,
                    selectedSourceKey = selectedSourceKey,
                    onSourceSelect = { selectedSourceKey = it },
                    onVodClick = onVodClick,
                    progressText = "${state.totalSources}/${state.totalSources}"
                )
            }
        }
    }
}

@Composable
private fun SearchResultContent(
    results: List<com.github.tvbox.newbox.domain.SearchResult>,
    selectedSourceKey: String?,
    onSourceSelect: (String?) -> Unit,
    onVodClick: (VodItem) -> Unit,
    progressText: String? = null,
) {
    val allVods = results.flatMap { it.vodItems }
    val sourceFilters = results.map { result ->
        SourceFilter(
            sourceKey = result.sourceKey,
            sourceName = result.sourceName,
            count = result.vodItems.size,
        )
    }
    val effectiveSourceKey = selectedSourceKey?.takeIf { sourceKey ->
        sourceFilters.any { it.sourceKey == sourceKey }
    }
    val displayVods = effectiveSourceKey?.let { sourceKey ->
        results.firstOrNull { it.sourceKey == sourceKey }?.vodItems.orEmpty()
    } ?: allVods

    if (allVods.isEmpty()) {
        Row(modifier = Modifier.fillMaxSize()) {
            SourceFilterRail(
                filters = sourceFilters,
                totalCount = 0,
                selectedSourceKey = effectiveSourceKey,
                onSelect = onSourceSelect,
                progressText = progressText,
                modifier = Modifier
                    .width(112.dp)
                    .fillMaxSize(),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("没有找到结果", style = MaterialTheme.typography.bodyLarge)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            SourceFilterRail(
                filters = sourceFilters,
                totalCount = allVods.size,
                selectedSourceKey = effectiveSourceKey,
                onSelect = onSourceSelect,
                progressText = progressText,
                modifier = Modifier
                    .width(112.dp)
                    .fillMaxSize(),
            )
            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayVods) { vod ->
                    VodCard(item = vod, onClick = onVodClick)
                }
            }
        }
    }
}

private data class SourceFilter(
    val sourceKey: String,
    val sourceName: String,
    val count: Int,
)

@Composable
private fun SourceFilterRail(
    filters: List<SourceFilter>,
    totalCount: Int,
    selectedSourceKey: String?,
    onSelect: (String?) -> Unit,
    progressText: String? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(start = 4.dp, end = 4.dp, top = 1.dp, bottom = 1.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!progressText.isNullOrBlank()) {
            item {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        item {
            SourceFilterItem(
                selected = selectedSourceKey == null,
                onClick = { onSelect(null) },
                name = "全部",
                count = totalCount,
            )
        }
        items(filters) { filter ->
            SourceFilterItem(
                selected = selectedSourceKey == filter.sourceKey,
                onClick = { onSelect(filter.sourceKey) },
                name = filter.sourceName,
                count = filter.count,
            )
        }
    }
}

@Composable
private fun SourceFilterItem(
    selected: Boolean,
    onClick: () -> Unit,
    name: String,
    count: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                text = "$count $name",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
