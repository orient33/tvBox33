package com.github.tvbox.newbox.feature.detailplayer

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.tvbox.newbox.R
import com.github.tvbox.newbox.domain.Episode
import com.github.tvbox.newbox.domain.VodDetail

private const val INLINE_EPISODE_LIMIT = 60

@Composable
fun DetailContent(
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (detail.description.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.detail_more),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onSynopsisClick() }
                            .padding(start = 8.dp),
                    )
                }
            }
        }

        item {
            val episodeCountText = stringResource(R.string.detail_episode_count, episodes.size)
            val metaParts = buildList {
                if (episodes.isNotEmpty()) add(episodeCountText)
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
                Text(
                    text = detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSynopsisClick() }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
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
                    text = stringResource(R.string.detail_episode_selection),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (episodes.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_view_all),
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
                    text = stringResource(R.string.detail_no_episode_data),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            item {
                val limit = minOf(episodes.size, INLINE_EPISODE_LIMIT)
                EpisodeGrid(
                    episodes = episodes.take(limit),
                    selectedIndex = selectedEpisodeIndex,
                    onEpisodeSelected = onEpisodeSelected,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

private val pureNumberRegex = Regex("^\\d{1,4}$")

private val episodeLabelRegex = Regex(
    "^(第\\s*\\d{1,3}\\s*[集话話期回]|EP?\\s*\\d{1,3}|\\d{1,3}\\s*[集话話期回])$",
    RegexOption.IGNORE_CASE,
)

private enum class EpisodeLayout(val columns: Int) {
    GRID_NUMERIC(5),
    GRID_LABELED(4),
    LIST(1),
}

private fun detectEpisodeLayout(names: List<String>): EpisodeLayout {
    if (names.isEmpty()) return EpisodeLayout.GRID_NUMERIC
    val threshold = (names.size * 9 + 9) / 10
    val numericCount = names.count { it.matches(pureNumberRegex) }
    if (numericCount >= threshold) return EpisodeLayout.GRID_NUMERIC
    val labeledCount = names.count { it.matches(episodeLabelRegex) }
    if (labeledCount >= threshold) return EpisodeLayout.GRID_LABELED
    return EpisodeLayout.LIST
}

@Composable
private fun EpisodeGrid(
    episodes: List<Episode>,
    selectedIndex: Int?,
    onEpisodeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = detectEpisodeLayout(episodes.map { it.name })
    if (layout == EpisodeLayout.LIST) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            episodes.forEachIndexed { index, episode ->
                EpisodeCard(
                    episode = episode,
                    isSelected = index == selectedIndex,
                    onClick = { onEpisodeSelected(index) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        val columns = layout.columns
        val rows = (episodes.size + columns - 1) / columns
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (col in 0 until columns) {
                        val index = row * columns + col
                        if (index < episodes.size) {
                            EpisodeCard(
                                episode = episodes[index],
                                isSelected = index == selectedIndex,
                                onClick = { onEpisodeSelected(index) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
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
}

@Composable
fun SynopsisSheetContent(
    detail: VodDetail,
    isCollected: Boolean,
    onToggleCollect: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleCollect) {
                    Icon(
                        imageVector = if (isCollected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isCollected) stringResource(R.string.detail_cancel_collect)
                        else stringResource(R.string.detail_collect),
                        tint = if (isCollected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (detail.director.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.detail_director_format, detail.director),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (detail.actor.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.detail_actor_format, detail.actor),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (detail.description.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.detail_intro),
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
fun AllEpisodesSheetContent(
    episodes: List<Episode>,
    selectedIndex: Int?,
    onEpisodeSelected: (Int) -> Unit,
) {
    val layout = detectEpisodeLayout(episodes.map { it.name })
    val gridCells = GridCells.Fixed(layout.columns)
    LazyVerticalGrid(
        columns = gridCells,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItemsIndexed(episodes) { index, episode ->
            EpisodeCard(
                episode = episode,
                isSelected = index == selectedIndex,
                onClick = { onEpisodeSelected(index) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(modifier = Modifier.height(32.dp))
}
