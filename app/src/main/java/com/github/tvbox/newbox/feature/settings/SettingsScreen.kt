package com.github.tvbox.newbox.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tvbox.newbox.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val subUrls by viewModel.subscriptionUrls.collectAsStateWithLifecycle()
    val sourceCounts by viewModel.sourceCounts.collectAsStateWithLifecycle()
    val currentSubscriptionUrl by viewModel.currentSubscriptionUrl.collectAsStateWithLifecycle()
    val titles by viewModel.subscriptionTitles.collectAsStateWithLifecycle()
    val warehousesMap by viewModel.subscriptionWarehouses.collectAsStateWithLifecycle()
    val currentWarehouseMap by viewModel.currentWarehouse.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val multiRouteResult by viewModel.multiRouteResult.collectAsStateWithLifecycle()
    val warehouseChoices by viewModel.warehouseChoices.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var subUrl by remember { mutableStateOf("http://z.qiqiv.cn/123.txt") }
    var selectedWarehouse by remember { mutableStateOf(-1) }
    var editingTitleForUrl by remember { mutableStateOf<String?>(null) }
    var editTitleValue by remember { mutableStateOf("") }
    var showWarehouseDialogForUrl by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val addRoutesSuccessMessage = stringResource(
        R.string.settings_add_routes_success,
        multiRouteResult?.routes?.size ?: 0,
    )

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(multiRouteResult) {
        multiRouteResult?.let { result ->
            snackbarHostState.showSnackbar(addRoutesSuccessMessage)
            viewModel.clearMultiRouteResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_subscription_management)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_subscription))
            }
        },
        modifier = modifier,
    ) { padding ->
        if (subUrls.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.settings_no_subscriptions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_add_subscription_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(padding),
            ) {
                itemsIndexed(subUrls) { index, url ->
                    val defaultTitle = stringResource(R.string.settings_default_source_title, index + 1)
                    val warehouses = warehousesMap[url]
                    val isMultiWarehouse = warehouses != null && warehouses.isNotEmpty()
                    val currentWhIndex = currentWarehouseMap[url] ?: -1
                    val currentWhName = if (isMultiWarehouse && currentWhIndex >= 0 && currentWhIndex < warehouses!!.size) {
                        warehouses[currentWhIndex].name
                    } else null
                    SubscriptionUrlItem(
                        url = url,
                        index = index,
                        title = titles[url],
                        sourceCount = sourceCounts[url] ?: 0,
                        isMultiWarehouse = isMultiWarehouse,
                        currentWarehouseName = currentWhName,
                        isSelected = url == currentSubscriptionUrl,
                        onSelect = { viewModel.selectSubscription(url) },
                        onWarehouseClick = { showWarehouseDialogForUrl = url },
                        onEditTitle = {
                            editTitleValue = titles[url] ?: defaultTitle
                            editingTitleForUrl = url
                        },
                        onDelete = { viewModel.removeSubscription(url) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.settings_add_subscription)) },
            text = {
                OutlinedTextField(
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    label = { Text(stringResource(R.string.settings_subscription_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (subUrl.isNotBlank()) {
                            val url = subUrl
                            viewModel.addSubscription(url)
                            subUrl = ""
                            showAddDialog = false
                        }
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    warehouseChoices?.let { result ->
        if (selectedWarehouse < 0) selectedWarehouse = 0
        AlertDialog(
            onDismissRequest = {
                viewModel.clearWarehouseChoices()
                selectedWarehouse = -1
            },
            title = { Text(stringResource(R.string.settings_select_repository)) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(result.warehouses.size) { index ->
                        val warehouse = result.warehouses[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedWarehouse == index,
                                    onClick = { selectedWarehouse = index },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedWarehouse == index,
                                onClick = { selectedWarehouse = index },
                            )
                            Text(
                                text = warehouse.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedWarehouse >= 0 && selectedWarehouse < result.warehouses.size) {
                            val chosen = result.warehouses[selectedWarehouse]
                            viewModel.addSubscription(chosen.url)
                        }
                        viewModel.clearWarehouseChoices()
                        selectedWarehouse = -1
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearWarehouseChoices()
                        selectedWarehouse = -1
                    },
                ) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    showWarehouseDialogForUrl?.let { url ->
        val warehouses = warehousesMap[url] ?: emptyList()
        val currentIndex = currentWarehouseMap[url] ?: -1
        var selectedIdx by remember { mutableStateOf(currentIndex) }
        AlertDialog(
            onDismissRequest = { showWarehouseDialogForUrl = null },
            title = { Text(stringResource(R.string.settings_select_warehouse_source)) },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(warehouses) { index, wh ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedIdx == index,
                                    onClick = { selectedIdx = index },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedIdx == index,
                                onClick = { selectedIdx = index },
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = wh.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = wh.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedIdx >= 0 && selectedIdx < warehouses.size) {
                            viewModel.selectWarehouse(url, selectedIdx)
                        }
                        showWarehouseDialogForUrl = null
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showWarehouseDialogForUrl = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    editingTitleForUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { editingTitleForUrl = null },
            title = { Text(stringResource(R.string.settings_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editTitleValue,
                    onValueChange = { editTitleValue = it },
                    label = { Text(stringResource(R.string.settings_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSubscriptionTitle(url, editTitleValue)
                        editingTitleForUrl = null
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { editingTitleForUrl = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionUrlItem(
    url: String,
    index: Int,
    title: String?,
    sourceCount: Int,
    isMultiWarehouse: Boolean,
    currentWarehouseName: String?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onWarehouseClick: () -> Unit,
    onEditTitle: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayTitle = title ?: stringResource(R.string.settings_default_source_title, index + 1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { showMenu = true },
            )
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.shapes.medium,
                ) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        ) {
            Text(
                text = if (isMultiWarehouse) displayTitle
                else stringResource(R.string.settings_source_count_format, displayTitle, sourceCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isMultiWarehouse) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onWarehouseClick,
                        label = {
                            val label = if (currentWarehouseName != null) {
                                stringResource(R.string.settings_multi_warehouse_name, currentWarehouseName)
                            } else {
                                stringResource(R.string.settings_multi_warehouse)
                            }
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_edit_title)) },
                onClick = {
                    showMenu = false
                    onEditTitle()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete)) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
}
