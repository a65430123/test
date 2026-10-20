package com.twstock.ta.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twstock.core.TechnicalAnalyzer
import com.twstock.core.signal.TradeSignal
import com.twstock.ta.data.local.Market
import com.twstock.ta.data.local.StockInfoEntity
import com.twstock.ta.data.repository.StockRepository
import com.twstock.ta.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WatchlistItemUi(
    val code: String,
    val name: String,
    val market: Market,
    val lastDate: LocalDate?,
    val lastClose: Double?,
    val change: Double?,
    val changePercent: Double?,
    val latestSignal: TradeSignal?,
)

data class WatchlistUiState(
    val isRefreshing: Boolean = false,
    val message: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WatchlistViewModel(
    private val repo: StockRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    val items: StateFlow<List<WatchlistItemUi>> =
        combine(repo.observeWatchlist(), settingsRepo.settings) { rows, settings ->
            rows.map { row ->
                val candles = repo.getCandles(row.code)
                val signal = if (candles.isEmpty()) null
                else TechnicalAnalyzer.analyze(candles, settings.params).signals.lastOrNull()
                val change = if (row.lastClose != null && row.prevClose != null) row.lastClose - row.prevClose else null
                WatchlistItemUi(
                    code = row.code,
                    name = row.name,
                    market = row.market,
                    lastDate = row.lastEpochDay?.let(LocalDate::ofEpochDay),
                    lastClose = row.lastClose,
                    change = change,
                    changePercent = if (change != null && row.prevClose != null && row.prevClose != 0.0)
                        change / row.prevClose * 100 else null,
                    latestSignal = signal,
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(WatchlistUiState())
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val searchResults: StateFlow<List<StockInfoEntity>> = query
        .debounce(200)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { repo.ensureStockList() }
                .onFailure { showMessage("股票清單下載失敗，仍可直接輸入代號加入") }
        }
    }

    fun onQueryChange(q: String) {
        query.value = q
    }

    fun addStock(code: String) {
        viewModelScope.launch {
            try {
                val entity = repo.addToWatchlist(code)
                query.value = ""
                showMessage("已加入 ${entity.code} ${entity.name}，下載歷史資料中…")
                repo.refreshCandles(entity.code, entity.market)
            } catch (e: Exception) {
                showMessage(e.message ?: "加入失敗")
            }
        }
    }

    fun removeStock(code: String) {
        viewModelScope.launch { repo.removeFromWatchlist(code) }
    }

    fun refreshAll() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val failed = mutableListOf<String>()
            for (item in repo.getWatchlist()) {
                runCatching { repo.refreshCandles(item.code, item.market) }
                    .onFailure { failed += item.code }
            }
            _state.update {
                it.copy(
                    isRefreshing = false,
                    message = if (failed.isEmpty()) null else "更新失敗：${failed.joinToString()}",
                )
            }
        }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun showMessage(msg: String) {
        _state.update { it.copy(message = msg) }
    }
}
