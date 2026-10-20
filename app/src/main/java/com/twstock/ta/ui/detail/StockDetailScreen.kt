package com.twstock.ta.ui.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.TradeSignal
import com.twstock.ta.ui.AppViewModelProvider
import com.twstock.ta.ui.chart.StockChart
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
                item(key = "header") {
                    Column {
                        Text(
                            "雙指縮放、左右拖曳平移、點選 K 棒查看數值",
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
            Text(
                signal.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
