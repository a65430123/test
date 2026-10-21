package com.twstock.ta.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twstock.core.AnalysisResult
import com.twstock.core.model.IndicatorParams
import com.twstock.core.model.SignalIndicator
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.TradeSignal
import com.twstock.ta.ui.AppViewModelProvider
import com.twstock.ta.ui.chart.StockChart
import com.twstock.ta.ui.common.SignalTypes
import com.twstock.ta.ui.common.TypeLine
import com.twstock.ta.ui.chart.rememberChartState
import com.twstock.ta.ui.theme.MarketColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    onBack: () -> Unit,
    vm: StockDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val chart by vm.chart.collectAsStateWithLifecycle()
    val chartState = rememberChartState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${vm.code} ${state.name}")
                        if (state.marketLabel.isNotEmpty()) {
                            Text(state.marketLabel, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    if (!state.inWatchlist) {
                        IconButton(onClick = vm::addToWatchlist) { Icon(Icons.Default.Add, "加入自選股") }
                    }
                    IconButton(onClick = vm::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, "更新")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val data = chart
            if (data == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.isRefreshing) CircularProgressIndicator() else Text("尚無資料")
                }
                return@Column
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "adjust") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = data.adjusted,
                            onClick = { vm.setAdjustPrices(!data.adjusted) },
                            label = { Text("除權息還原") },
                        )
                        Text(
                            if (data.adjusted) "價格與指標以還原價計算" else "原始價（除權息日可能產生缺口）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "signal-mode") {
                    SignalModeSelector(
                        params = data.params,
                        onToggle = { indicator, selected ->
                            vm.updateSignalParams { it.toggleIndicator(indicator, selected) }
                        },
                        onConfirmations = { n -> vm.updateSignalParams { it.copy(minConfirmations = n) } },
                    )
                }
                item(key = "chart") {
                    // 圖表內的拖曳用於平移；要捲動頁面請從圖表外開始滑動
                    StockChart(
                        analysis = data.analysis,
                        params = data.params,
                        state = chartState,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        exRightDates = data.exRightDates,
                        adjusted = data.adjusted,
                    )
                }
                item(key = "selected-bar") {
                    SelectedBarSignals(
                        analysis = data.analysis,
                        selectedIndex = chartState.selectedIndex,
                        adjusted = data.adjusted,
                    )
                }
                item(key = "header") {
                    Column {
                        Text(
                            "雙指縮放、左右拖曳平移、點選 K 棒查看數值與當日訊號",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        HorizontalDivider(Modifier.padding(top = 4.dp))
                        Text(
                            "進出場訊號（共 ${data.analysis.signals.size} 筆）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(data.analysis.signals.asReversed(), key = { "${it.index}-${it.side}" }) { signal ->
                    SignalRow(signal, data.adjusted, onClick = {
                        chartState.focus(signal.index, data.analysis.candles.size)
                        scope.launch { listState.animateScrollToItem(0) }
                    })
                }
            }
        }
    }
}

/** 選擇以哪些指標判斷進出場訊號：單選＝只看該指標，多選時可選確認數。 */
@Composable
private fun SignalModeSelector(
    params: IndicatorParams,
    onToggle: (SignalIndicator, Boolean) -> Unit,
    onConfirmations: (Int) -> Unit,
) {
    val selected = params.selectedIndicators
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("訊號來源", style = MaterialTheme.typography.labelMedium)
            SignalIndicator.entries.forEach { indicator ->
                val isSelected = indicator in selected
                FilterChip(
                    selected = isSelected,
                    // 至少保留一個指標
                    onClick = { if (!(isSelected && selected.size == 1)) onToggle(indicator, !isSelected) },
                    label = { Text(indicator.label) },
                )
            }
        }
        if (selected.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("確認方式", style = MaterialTheme.typography.labelMedium)
                (1..selected.size).forEach { n ->
                    FilterChip(
                        selected = params.requiredConfirmations == n,
                        onClick = { onConfirmations(n) },
                        label = { Text(if (n == 1) "任一" else "$n 個") },
                    )
                }
            }
        }
        Text(
            params.describeSignalMode(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 點選 K 棒時，顯示該日的進出場訊號；沒有訊號但有指標事件時一併列出（未達確認）。 */
@Composable
private fun SelectedBarSignals(analysis: AnalysisResult, selectedIndex: Int?, adjusted: Boolean) {
    val index = selectedIndex ?: return
    val bar = analysis.candles.getOrNull(index) ?: return
    val signals = analysis.signals.filter { it.index == index }
    val events = analysis.events.filter { it.index == index }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${bar.date} 的進出場訊號", style = MaterialTheme.typography.titleSmall)
            when {
                signals.isNotEmpty() -> signals.forEach { signal ->
                    val buy = signal.side == SignalSide.BUY
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (buy) "▲ 買進訊號" else "▼ 賣出訊號",
                            color = if (buy) MarketColors.Up else MarketColors.Down,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${if (adjusted) "還原收盤" else "收盤"} %.2f".format(signal.price),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    SignalTypes(signal)
                }
                events.isNotEmpty() -> {
                    Text("沒有進出場訊號（未達確認條件）", style = MaterialTheme.typography.bodyMedium)
                    events.groupBy { it.source.indicator }.toSortedMap().forEach { (indicator, list) ->
                        TypeLine(indicator, list.joinToString("、") { it.description })
                    }
                }
                else -> Text(
                    "這一天沒有進出場訊號",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SignalRow(signal: TradeSignal, adjusted: Boolean, onClick: () -> Unit) {
    val buy = signal.side == SignalSide.BUY
    val color = if (buy) MarketColors.Up else MarketColors.Down
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (buy) "▲ 買" else "▼ 賣", color = color, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text("${signal.date}　${if (adjusted) "還原收盤" else "收盤"} %.2f".format(signal.price), style = MaterialTheme.typography.bodyMedium)
            SignalTypes(signal)
        }
    }
}
