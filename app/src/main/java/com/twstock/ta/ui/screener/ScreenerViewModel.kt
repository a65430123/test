package com.twstock.ta.ui.screener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twstock.core.ScreenHit
import com.twstock.core.Screener
import com.twstock.core.signal.SignalSide
import com.twstock.ta.data.local.Market
import com.twstock.ta.data.remote.MarketDataParser
import com.twstock.ta.data.repository.StockRepository
import com.twstock.ta.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SideFilter(val label: String) { ALL("全部"), BUY("買進"), SELL("賣出") }

/** 上市、上櫃只含股票；ETF 含上市與上櫃 ETF；全部含股票與 ETF。 */
enum class MarketFilter(val label: String) { ALL("全部"), TWSE("上市"), TPEX("上櫃"), ETF("ETF") }

data class ScreenerFilters(
    val side: SideFilter = SideFilter.ALL,
    val market: MarketFilter = MarketFilter.ALL,
    /** 最低成交量（張）；0 表示不限 */
    val minVolume: Long = 500,
) {
    companion object {
        val VOLUME_OPTIONS = listOf(0L, 500L, 1000L, 5000L)
    }
}

data class ScreenItem(
    val code: String,
    val name: String,
    val market: Market?,
    val hit: ScreenHit,
) {
    val change: Double? get() = hit.prevClose?.let { hit.close - it }
    val changePercent: Double? get() = hit.prevClose?.takeIf { it != 0.0 }?.let { (hit.close - it) / it * 100 }
}

data class ScreenerUiState(
    val syncing: Boolean = false,
    /** 下載進度（已完成, 預估總數） */
    val progress: Pair<Int, Int>? = null,
    val message: String? = null,
    val latestDate: LocalDate? = null,
    /** 最新交易日有資料、參與掃描的股票數 */
    val scanned: Int = 0,
    /** 篩選前的符合檔數 */
    val totalHits: Int = 0,
    val items: List<ScreenItem> = emptyList(),
    val filters: ScreenerFilters = ScreenerFilters(),
    val signalMode: String = "",
    val scanning: Boolean = true,
)

private data class ScanResult(
    val latestDate: LocalDate?,
    val scanned: Int,
    val items: List<ScreenItem>,
    val signalMode: String,
)

private data class SyncState(val syncing: Boolean = false, val progress: Pair<Int, Int>? = null, val message: String? = null)

/** 選股：掃描上市上櫃全部股票，列出最新交易日出現進出場訊號者（依目前的訊號設定）。 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ScreenerViewModel(
    private val repo: StockRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(ScreenerFilters())
    private val sync = MutableStateFlow(SyncState())

    private val scan: StateFlow<ScanResult?> =
        combine(settingsRepo.settings, repo.observeMarketChanges().debounce(1_000)) { settings, _ -> settings }
            .mapLatest { settings ->
                val latest = repo.latestMarketDay()
                    ?: return@mapLatest ScanResult(null, 0, emptyList(), settings.params.describeSignalMode())
                val series = repo.getMarketSeries()
                val exRights = if (settings.adjustPrices) repo.getAllExRights() else emptyMap()
                val info = repo.getStockInfoMap()
                val hits = Screener.scan(series, settings.params, latest, exRights, settings.adjustPrices)
                ScanResult(
                    latestDate = latest,
                    scanned = series.values.count { it.lastOrNull()?.date == latest },
                    items = hits.map { ScreenItem(it.code, info[it.code]?.name ?: it.code, info[it.code]?.market, it) },
                    signalMode = settings.params.describeSignalMode(),
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<ScreenerUiState> = combine(scan, filters, sync) { result, f, s ->
        val all = result?.items.orEmpty()
        ScreenerUiState(
            syncing = s.syncing,
            progress = s.progress,
            message = s.message,
            latestDate = result?.latestDate,
            scanned = result?.scanned ?: 0,
            totalHits = all.size,
            items = all.filter { it.matches(f) },
            filters = f,
            signalMode = result?.signalMode.orEmpty(),
            scanning = result == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenerUiState())

    init {
        refresh()
    }

    /** 下載尚未取得的市場資料（首次約 3 分鐘，之後每天 2 個請求），完成後自動重新掃描。 */
    fun refresh() {
        if (sync.value.syncing) return
        viewModelScope.launch {
            sync.value = SyncState(syncing = true)
            val result = runCatching {
                if (settingsRepo.settings.first().adjustPrices) runCatching { repo.syncExRights() }
                repo.syncMarketDaily { done, total -> sync.update { it.copy(progress = done to total) } }
            }
            sync.value = SyncState(
                message = result.exceptionOrNull()?.let { "市場資料下載失敗：${it.message ?: it.javaClass.simpleName}" },
            )
        }
    }

    fun updateFilters(transform: (ScreenerFilters) -> ScreenerFilters) = filters.update(transform)

    fun messageShown() = sync.update { it.copy(message = null) }

    private fun ScreenItem.matches(f: ScreenerFilters): Boolean {
        val isEtf = MarketDataParser.isEtf(code)
        if (f.minVolume > 0 && hit.volume < f.minVolume) return false
        when (f.side) {
            SideFilter.BUY -> if (hit.signal.side != SignalSide.BUY) return false
            SideFilter.SELL -> if (hit.signal.side != SignalSide.SELL) return false
            SideFilter.ALL -> Unit
        }
        return when (f.market) {
            MarketFilter.ALL -> true
            MarketFilter.TWSE -> !isEtf && market == Market.TWSE
            MarketFilter.TPEX -> !isEtf && market == Market.TPEX
            MarketFilter.ETF -> isEtf
        }
    }
}
