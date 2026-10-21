package com.twstock.ta.ui.screener

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.SignalType
import com.twstock.ta.data.local.labelFor
import com.twstock.ta.data.repository.StockRepository
import com.twstock.ta.ui.AppViewModelProvider
import com.twstock.ta.ui.common.SignalTypes
import com.twstock.ta.ui.navigation.MainBottomBar
import com.twstock.ta.ui.navigation.Routes
import com.twstock.ta.ui.theme.MarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenerScreen(
    onOpenStock: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onNavigate: (String) -> Unit,
    vm: ScreenerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("選股") },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, "搜尋個股") }
                    IconButton(onClick = vm::refresh, enabled = !state.syncing) { Icon(Icons.Default.Refresh, "更新市場資料") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "設定") }
                },
            )
        },
        bottomBar = { MainBottomBar(Routes.SCREENER, onNavigate) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "status") { Status(state) }
            item(key = "filters") { Filters(state.filters, state.typeCounts, vm::updateFilters) }
            item(key = "divider") { HorizontalDivider() }
            when {
                state.latestDate == null && !state.scanning -> item(key = "empty") {
                    Hint("尚未下載市場資料。首次下載上市上櫃最近 ${StockRepository.MARKET_DAYS} 個交易日約需 3 分鐘，之後每天只需更新一次。")
                }
                state.items.isEmpty() && !state.scanning -> item(key = "none") {
                    Hint(
                        if (state.totalHits == 0) "最新交易日沒有出現進出場訊號的股票"
                        else "共 ${state.totalHits} 檔出現訊號，但都不符合目前的篩選條件",
                    )
                }
                else -> items(state.items, key = { it.code }) { item ->
                    ScreenRow(item, onClick = { onOpenStock(item.code) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun Status(state: ScreenerUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.syncing) {
            val p = state.progress
            if (p != null && p.second > 0) {
                LinearProgressIndicator(progress = { p.first.toFloat() / p.second }, modifier = Modifier.fillMaxWidth())
                Text("下載市場資料 ${p.first}/${p.second}", style = MaterialTheme.typography.labelSmall)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("檢查市場資料…", style = MaterialTheme.typography.labelSmall)
            }
        }
        state.latestDate?.let {
            Text(
                "資料日期 $it・掃描 %,d 檔・出現訊號 %,d 檔・顯示 %,d 檔".format(state.scanned, state.totalHits, state.items.size),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.signalMode.isNotEmpty()) {
            Text(
                "訊號：${state.signalMode}（於個股頁或設定頁調整）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Filters(
    f: ScreenerFilters,
    typeCounts: List<Pair<SignalType, Int>>,
    update: ((ScreenerFilters) -> ScreenerFilters) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        ChipRow("方向") {
            SideFilter.entries.forEach { s ->
                FilterChip(selected = f.side == s, onClick = { update { it.copy(side = s) } }, label = { Text(s.label) })
            }
        }
        ChipRow("市場") {
            MarketFilter.entries.forEach { m ->
                FilterChip(selected = f.market == m, onClick = { update { it.copy(market = m) } }, label = { Text(m.label) })
            }
        }
        ChipRow("成交量") {
            ScreenerFilters.VOLUME_OPTIONS.forEach { v ->
                FilterChip(
                    selected = f.minVolume == v,
                    onClick = { update { it.copy(minVolume = v) } },
                    label = { Text(if (v == 0L) "不限" else "≥%,d 張".format(v)) },
                )
            }
        }
        if (typeCounts.isNotEmpty()) {
            ChipRow("類型") {
                FilterChip(selected = f.types.isEmpty(), onClick = { update { it.copy(types = emptySet()) } }, label = { Text("全部") })
                typeCounts.forEach { (type, count) ->
                    val on = type in f.types
                    FilterChip(
                        selected = on,
                        onClick = { update { it.copy(types = if (on) it.types - type else it.types + type) } },
                        label = { Text("${type.label}（$count）") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        content()
    }
}

@Composable
private fun ScreenRow(item: ScreenItem, onClick: () -> Unit) {
    val buy = item.hit.signal.side == SignalSide.BUY
    val changeColor = MarketColors.forChange(item.change)
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (buy) "▲ 買進" else "▼ 賣出",
                color = if (buy) MarketColors.Up else MarketColors.Down,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Text(item.name, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text(
                "${item.code}${item.market?.let { "・${it.labelFor(item.code)}" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text("%.2f".format(item.hit.close), color = changeColor, fontWeight = FontWeight.Bold)
                Text(
                    item.changePercent?.let { "%+.2f%%".format(it) } ?: "--",
                    color = changeColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            "成交量 %,d 張".format(item.hit.volume),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SignalTypes(item.hit.signal)
    }
}
