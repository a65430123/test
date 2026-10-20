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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val name: String = "",
    val marketLabel: String = "",
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

data class ChartData(val analysis: AnalysisResult, val params: IndicatorParams)

class StockDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repo: StockRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    val code: String = checkNotNull(savedStateHandle["code"])

    private val _state = MutableStateFlow(DetailUiState(name = code))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** 參數或資料變動時重新計算指標。 */
    val chart: StateFlow<ChartData?> =
        combine(repo.observeCandles(code), settingsRepo.settings) { candles, settings ->
            if (candles.isEmpty()) null
            else ChartData(TechnicalAnalyzer.analyze(candles, settings.params), settings.params)
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

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            val item = repo.getWatchlistItem(code) ?: return@launch
            _state.update { it.copy(isRefreshing = true, error = null) }
            val result = runCatching { repo.refreshCandles(item.code, item.market) }
            _state.update {
                it.copy(
                    isRefreshing = false,
                    error = result.exceptionOrNull()?.let { e -> "更新失敗：${e.message ?: e.javaClass.simpleName}" },
                )
            }
        }
    }
}
