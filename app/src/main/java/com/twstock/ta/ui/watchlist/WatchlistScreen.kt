package com.twstock.ta.ui.watchlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twstock.core.signal.SignalSide
import com.twstock.ta.data.local.StockInfoEntity
import com.twstock.ta.ui.AppViewModelProvider
import com.twstock.ta.ui.theme.MarketColors
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("MM/dd")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onOpenStock: (String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: WatchlistViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val signalMode by vm.signalMode.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WatchlistItemUi?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自選股") },
                actions = {
                    IconButton(onClick = vm::refreshAll) { Icon(Icons.Default.Refresh, "更新") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "設定") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "加入自選股") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = vm::refreshAll,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (signalMode.isNotEmpty()) {
                    Text(
                        "訊號：$signalMode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("尚無自選股，點右下角 + 加入", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items, key = { it.code }) { item ->
                            WatchlistRow(
                                item = item,
                                onClick = { onOpenStock(item.code) },
                                onLongClick = { pendingDelete = item },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddStockDialog(
            query = query,
            results = results,
            onQueryChange = vm::onQueryChange,
            onPick = { code ->
                vm.addStock(code)
                showAdd = false
            },
            onDismiss = {
                vm.onQueryChange("")
                showAdd = false
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("移除自選股") },
            text = { Text("確定要移除 ${item.code} ${item.name}？本地歷史資料也會一併刪除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeStock(item.code)
                    pendingDelete = null
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchlistRow(item: WatchlistItemUi, onClick: () -> Unit, onLongClick: () -> Unit) {
    val color = MarketColors.forChange(item.change)
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${item.code}・${item.market.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingContent = {
            val signal = item.latestSignal
            if (signal != null) {
                val buy = signal.side == SignalSide.BUY
                AssistChip(
                    onClick = onClick,
                    label = { Text("${signal.date.format(DATE_FMT)} ${if (buy) "買進訊號" else "賣出訊號"}") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (buy) MarketColors.Up else MarketColors.Down,
                    ),
                )
            } else if (item.lastDate == null) {
                Text("資料下載中…", style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                Text(
                    item.lastClose?.let { "%.2f".format(it) } ?: "--",
                    color = color,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (item.change != null && item.changePercent != null) {
                    Text(
                        "%+.2f (%+.2f%%)".format(item.change, item.changePercent),
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

@Composable
private fun AddStockDialog(
    query: String,
    results: List<StockInfoEntity>,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入自選股") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("代號或名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.code }) { info ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(info.code) },
                            headlineContent = { Text("${info.code} ${info.name}") },
                            supportingContent = { Text(info.market.label) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(query) }, enabled = query.isNotBlank()) { Text("以代號加入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
