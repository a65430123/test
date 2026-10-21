package com.twstock.ta.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twstock.core.AnalysisResult
import com.twstock.core.TechnicalAnalyzer
import com.twstock.core.model.IndicatorParams
import com.twstock.ta.data.repository.StockRepository
import com.twstock.ta.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
)

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
                repo.observePriceSeries(code, settings.adjustPrices).map { series ->
                    if (series.candles.isEmpty()) null
                    else ChartData(
                        analysis = TechnicalAnalyzer.analyze(series.candles, settings.params),
                        params = settings.params,
                        exRightDates = series.exRightDates,
                        adjusted = series.adjusted,
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
