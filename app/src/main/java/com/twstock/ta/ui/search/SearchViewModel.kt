package com.twstock.ta.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twstock.ta.data.local.StockInfoEntity
import com.twstock.ta.data.repository.QuoteSummary
import com.twstock.ta.data.repository.StockQuery
import com.twstock.ta.data.repository.StockRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchResult(val info: StockInfoEntity, val quote: QuoteSummary?)

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    /** 查無結果且輸入像完整代號時，提供直接查詢 */
    val lookupCode: String? = null,
    val looking: Boolean = false,
    val loadingList: Boolean = false,
    val message: String? = null,
)

/** 搜尋個股：輸入代號或名稱，代號比對開頭、名稱比對任意位置。 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repo: StockRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val extra = MutableStateFlow(SearchUiState())

    private val results = query
        .debounce(150)
        .flatMapLatest { q ->
            if (StockQuery.normalize(q).isEmpty()) flowOf(emptyList())
            else repo.search(q).map { list ->
                val quotes = repo.latestQuotes(list.map { it.code })
                list.map { SearchResult(it, quotes[it.code]) }
            }
        }

    val state: StateFlow<SearchUiState> = combine(query, results, extra) { q, r, e ->
        val normalized = StockQuery.normalize(q)
        e.copy(
            query = q,
            results = r,
            lookupCode = normalized.takeIf { r.none { it.info.code == normalized } && StockQuery.looksLikeCode(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        // 股票清單為空時先下載（上市＋上櫃，含 ETF）
        viewModelScope.launch {
            extra.update { it.copy(loadingList = true) }
            runCatching { repo.ensureStockList() }
                .onFailure { e -> extra.update { it.copy(message = "股票清單下載失敗：${e.message ?: "請檢查網路"}") } }
            extra.update { it.copy(loadingList = false) }
        }
    }

    fun onQueryChange(q: String) {
        query.value = q
    }

    /** 直接向交易所查詢代號；找到後回呼開啟個股頁。 */
    fun lookup(code: String, onFound: (String) -> Unit) {
        if (extra.value.looking) return
        viewModelScope.launch {
            extra.update { it.copy(looking = true) }
            val info = runCatching { repo.lookupStock(code) }.getOrNull()
            extra.update { it.copy(looking = false, message = if (info == null) "查無股票代號 $code" else null) }
            info?.let { onFound(it.code) }
        }
    }

    fun messageShown() = extra.update { it.copy(message = null) }
}
