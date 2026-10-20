package com.twstock.ta.data.repository

import com.google.gson.JsonObject
import com.twstock.core.model.Candle
import com.twstock.ta.data.local.AppDatabase
import com.twstock.ta.data.local.Market
import com.twstock.ta.data.local.StockInfoEntity
import com.twstock.ta.data.local.WatchlistEntity
import com.twstock.ta.data.local.WatchlistRow
import com.twstock.ta.data.local.toEntity
import com.twstock.ta.data.remote.MarketDataParser
import com.twstock.ta.data.remote.TpexApi
import com.twstock.ta.data.remote.TwseApi
import com.twstock.ta.data.remote.TwseOpenApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StockRepository(
    private val db: AppDatabase,
    private val twse: TwseApi,
    private val tpex: TpexApi,
    private val twseOpen: TwseOpenApi,
) {
    private val infoDao = db.stockInfoDao()
    private val watchlistDao = db.watchlistDao()
    private val candleDao = db.candleDao()

    /** 證交所對短時間大量請求會暫時封鎖 IP，所有月資料請求共用節流。 */
    private val throttle = Mutex()
    private var lastRequestAt = 0L

    fun observeWatchlist(): Flow<List<WatchlistRow>> = watchlistDao.observeRows()

    fun observeCandles(code: String): Flow<List<Candle>> =
        candleDao.observe(code).map { list -> list.map { it.toModel() } }

    suspend fun getCandles(code: String): List<Candle> = candleDao.getAll(code).map { it.toModel() }

    suspend fun getWatchlist(): List<WatchlistEntity> = watchlistDao.getAll()

    suspend fun getWatchlistItem(code: String): WatchlistEntity? = watchlistDao.get(code)

    suspend fun markNotified(code: String, date: LocalDate) =
        watchlistDao.setLastNotified(code, date.toEpochDay())

    fun search(query: String): Flow<List<StockInfoEntity>> = infoDao.search(query.trim())

    /** 下載上市＋上櫃股票清單（僅在本地為空或強制時）。 */
    suspend fun ensureStockList(force: Boolean = false) {
        if (!force && infoDao.count() > 0) return
        val listed = runCatching { twseOpen.stockDayAll() }.getOrDefault(emptyList())
            .mapNotNull { it.toInfo("Code", "Name", Market.TWSE) }
        val otc = runCatching { tpex.mainboardQuotes() }.getOrDefault(emptyList())
            .mapNotNull { it.toInfo("SecuritiesCompanyCode", "CompanyName", Market.TPEX) }
        if (listed.isEmpty() && otc.isEmpty()) error("無法下載股票清單，請檢查網路連線")
        infoDao.upsertAll(listed + otc)
    }

    /**
     * 加入自選股。若本地清單查不到代號，依序嘗試證交所與櫃買中心判斷市場別。
     */
    suspend fun addToWatchlist(code: String): WatchlistEntity {
        val trimmed = code.trim().uppercase()
        val info = infoDao.get(trimmed) ?: probeStock(trimmed)
            ?: error("查無股票代號 $trimmed")
        val entity = WatchlistEntity(info.code, info.name, info.market)
        watchlistDao.insert(entity)
        return entity
    }

    suspend fun removeFromWatchlist(code: String) {
        watchlistDao.delete(code)
        candleDao.deleteFor(code)
    }

    /**
     * 增量更新日K：本地無資料時下載 [HISTORY_MONTHS] 個月，否則從最後一筆資料所在月份補到本月。
     * @return 新寫入（或覆寫）的 K 棒數
     */
    suspend fun refreshCandles(code: String, market: Market): Int {
        val today = LocalDate.now(TAIPEI)
        val thisMonth = YearMonth.from(today)
        val latest = candleDao.latestEpochDay(code)?.let { LocalDate.ofEpochDay(it) }
        val fromMonth = if (latest == null) thisMonth.minusMonths(HISTORY_MONTHS - 1L) else YearMonth.from(latest)

        var written = 0
        var month = fromMonth
        while (!month.isAfter(thisMonth)) {
            val candles = fetchMonth(code, market, month)
            if (candles.isNotEmpty()) {
                candleDao.upsertAll(candles.map { it.toEntity(code) })
                written += candles.size
            }
            month = month.plusMonths(1)
        }
        return written
    }

    private suspend fun fetchMonth(code: String, market: Market, month: YearMonth): List<Candle> =
        throttled {
            when (market) {
                Market.TWSE -> {
                    val r = twse.stockDay(month.atDay(1).format(TWSE_DATE), code)
                    if (r.stat?.equals("OK", ignoreCase = true) == true) MarketDataParser.parseTwseRows(r.data)
                    else emptyList()
                }
                Market.TPEX -> {
                    val r = tpex.tradingStock(code, month.atDay(1).format(TPEX_DATE))
                    val rows = r.tables?.firstOrNull()?.data ?: r.aaData
                    MarketDataParser.parseTpexRows(rows)
                }
            }
        }

    private suspend fun probeStock(code: String): StockInfoEntity? {
        val month = YearMonth.now(TAIPEI)
        val twseResult = runCatching {
            throttled { twse.stockDay(month.atDay(1).format(TWSE_DATE), code) }
        }.getOrNull()
        if (twseResult?.stat.equals("OK", ignoreCase = true) && !twseResult?.data.isNullOrEmpty()) {
            val name = MarketDataParser.parseTwseName(twseResult?.title, code) ?: code
            return StockInfoEntity(code, name, Market.TWSE).also { infoDao.upsertAll(listOf(it)) }
        }
        val tpexResult = runCatching {
            throttled { tpex.tradingStock(code, month.atDay(1).format(TPEX_DATE)) }
        }.getOrNull()
        val tpexRows = tpexResult?.tables?.firstOrNull()?.data ?: tpexResult?.aaData
        if (!tpexRows.isNullOrEmpty()) {
            return StockInfoEntity(code, code, Market.TPEX).also { infoDao.upsertAll(listOf(it)) }
        }
        return null
    }

    private suspend fun <T> throttled(block: suspend () -> T): T = throttle.withLock {
        val wait = lastRequestAt + MIN_REQUEST_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private fun JsonObject.toInfo(codeKey: String, nameKey: String, market: Market): StockInfoEntity? {
        val code = get(codeKey)?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return null
        val name = get(nameKey)?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return null
        if (code.isEmpty() || name.isEmpty()) return null
        return StockInfoEntity(code, name, market)
    }

    companion object {
        val TAIPEI: ZoneId = ZoneId.of("Asia/Taipei")
        const val HISTORY_MONTHS = 12
        const val MIN_REQUEST_INTERVAL_MS = 1_500L
        private val TWSE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TPEX_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    }
}
