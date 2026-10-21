package com.twstock.ta.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twstock.core.AnalysisResult
import com.twstock.core.TechnicalAnalyzer
import com.twstock.core.model.BrokerFlow
import com.twstock.core.model.ChipDay
import com.twstock.core.model.IndicatorParams
import com.twstock.ta.data.repository.StockRepository
import com.twstock.ta.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DetailUiState(
    val name: String = "",
    val marketLabel: String = "",
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

data class ChartData(
    val analysis: AnalysisResult,
    val params: IndicatorParams,
    val exRightDates: Set<LocalDate>,
    val adjusted: Boolean,
    /** 最近的籌碼資料（舊到新） */
    val chips: List<ChipDay>,
)

/** 分點券商區塊狀態 */
sealed interface BrokerState {
    data object NoToken : BrokerState
    data object Idle : BrokerState
    data object Loading : BrokerState
    data class Loaded(val date: LocalDate, val flows: List<BrokerFlow>) : BrokerState
    data class Error(val message: String) : BrokerState
}

@OptIn(ExperimentalCoroutinesApi::class)
class StockDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: StockRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val code: String = checkNotNull(savedStateHandle["code"])

    private val _state = MutableStateFlow(DetailUiState(name = code))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** 參數、還原設定或資料變動時重新計算指標。 */
    val chart: StateFlow<ChartData?> =
        settingsRepo.settings
            .flatMapLatest { settings ->
                combine(repo.observePriceSeries(code, settings.adjustPrices), repo.observeChips(code)) { series, chips ->
                    if (series.candles.isEmpty()) null
                    else ChartData(
                        analysis = TechnicalAnalyzer.analyze(series.candles, settings.params, chips),
                        params = settings.params,
                        exRightDates = series.exRightDates,
                        adjusted = series.adjusted,
                        chips = chips,
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            repo.getWatchlistItem(code)?.let { item ->
                _state.update { it.copy(name = item.name, marketLabel = item.market.label) }
            }
            refresh()
        }
    }

    /** 更新訊號來源／確認方式（全域設定，自選股清單與推播一併套用）。 */
    fun updateSignalParams(transform: (IndicatorParams) -> IndicatorParams) {
        viewModelScope.launch {
            val next = transform(settingsRepo.settings.first().params)
            if (next.validate().isEmpty()) settingsRepo.saveParams(next)
        }
    }

    private val _broker = MutableStateFlow<BrokerState>(BrokerState.Idle)
    val broker: StateFlow<BrokerState> = _broker.asStateFlow()

    /** 載入指定交易日的分點券商進出（FinMind）。 */
    fun loadBrokers(date: LocalDate) {
        viewModelScope.launch {
            val token = settingsRepo.settings.first().finMindToken
            if (token.isBlank()) {
                _broker.value = BrokerState.NoToken
                return@launch
            }
            _broker.value = BrokerState.Loading
            _broker.value = runCatching { repo.getBrokerFlows(code, date, token) }.fold(
                onSuccess = { BrokerState.Loaded(date, it) },
                onFailure = { BrokerState.Error(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun setAdjustPrices(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAdjustPrices(enabled)
            if (enabled) {
                runCatching { repo.syncExRights() }.onFailure { e ->
                    _state.update { it.copy(error = "除權息資料下載失敗：${e.message ?: e.javaClass.simpleName}") }
                }
            }
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            val item = repo.getWatchlistItem(code) ?: return@launch
            _state.update { it.copy(isRefreshing = true, error = null) }
            val result = runCatching {
                if (settingsRepo.settings.first().adjustPrices) repo.syncExRights()
                repo.refreshCandles(item.code, item.market)
                repo.syncChips()
            }
            _state.update {
                it.copy(
                    isRefreshing = false,
                    error = result.exceptionOrNull()?.let { e -> "更新失敗：${e.message ?: e.javaClass.simpleName}" },
                )
            }
        }
    }
}
