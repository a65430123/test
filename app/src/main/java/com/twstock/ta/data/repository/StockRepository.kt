package com.twstock.ta.data.repository

import com.google.gson.JsonObject
import com.twstock.core.PriceAdjuster
import com.twstock.core.model.Candle
import com.twstock.core.model.ExRightEvent
import com.twstock.ta.data.local.AppDatabase
import com.twstock.ta.data.local.CandleEntity
import com.twstock.ta.data.local.ExRightEntity
import com.twstock.ta.data.local.MarketCandleEntity
import com.twstock.ta.data.local.MarketDayEntity
import com.twstock.ta.data.local.Market
import com.twstock.ta.data.local.StockInfoEntity
import com.twstock.ta.data.local.WatchlistEntity
import com.twstock.ta.data.local.WatchlistRow
import com.twstock.ta.data.local.toEntity
import com.twstock.ta.data.remote.DailyQuote
import com.twstock.ta.data.remote.MarketDataParser
import com.twstock.ta.data.remote.TpexApi
import com.twstock.ta.data.remote.TwseApi
import com.twstock.ta.data.remote.TwseOpenApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/** 日K下載月份規劃（新到舊）。 */
internal object CandlePlan {
    /** 本月往回到本地最新資料所在月份；本地無資料則到 targetStart。 */
    fun forward(thisMonth: YearMonth, latest: YearMonth?, targetStart: YearMonth): List<YearMonth> =
        generateSequence(thisMonth) { it.minusMonths(1) }.takeWhile { !it.isBefore(latest ?: targetStart) }.toList()

    /** 本地最早資料之前、到 targetStart 為止需補齊的月份（新到舊）。 */
    fun backfill(earliest: YearMonth?, targetStart: YearMonth): List<YearMonth> =
        if (earliest == null) emptyList()
        else generateSequence(earliest.minusMonths(1)) { it.minusMonths(1) }.takeWhile { !it.isBefore(targetStart) }.toList()
}

/**
 * 依主機節流：證交所會封鎖短時間大量請求，櫃買中心較寬鬆。
 * 前景請求（使用者正在看的個股）優先；背景同步（選股、除權息）在前景下載期間暫停讓路。
 */
private class HostThrottle(private val intervalMs: Long) {
    private val mutex = Mutex()
    private var lastRequestAt = 0L
    private val foreground = AtomicInteger(0)

    /** 在此區塊內，背景請求暫停。 */
    suspend fun <T> withPriority(block: suspend () -> T): T {
        foreground.incrementAndGet()
        try {
            return block()
        } finally {
            foreground.decrementAndGet()
        }
    }

    suspend fun <T> request(background: Boolean, block: suspend () -> T): T {
        if (background) while (foreground.get() > 0) delay(200)
        return mutex.withLock {
            val wait = lastRequestAt + intervalMs - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                block()
            } finally {
                lastRequestAt = System.currentTimeMillis()
            }
        }
    }
}

/**
 * @param candles 依設定為原始價或還原價
 * @param exRightDates 除權息日（圖表標記用，不論是否還原皆提供）
 */
data class PriceSeries(
    val candles: List<Candle>,
    val exRightDates: Set<LocalDate>,
    val adjusted: Boolean,
)

class StockRepository(
    private val db: AppDatabase,
    private val twse: TwseApi,
    private val tpex: TpexApi,
    private val twseOpen: TwseOpenApi,
) {
    private val infoDao = db.stockInfoDao()
    private val watchlistDao = db.watchlistDao()
    private val candleDao = db.candleDao()
    private val exRightDao = db.exRightDao()
    private val marketDao = db.marketDao()

    private val twseThrottle = HostThrottle(TWSE_INTERVAL_MS)
    private val tpexThrottle = HostThrottle(TPEX_INTERVAL_MS)
    private fun throttleFor(market: Market) = if (market == Market.TWSE) twseThrottle else tpexThrottle

    /** 本次執行中已確認沒有更早資料的股票（例如新上市），避免每次開啟都再查一次。 */
    private val noOlderHistory = HashSet<String>()

    private val exRightSyncLock = Mutex()
    private val marketSyncLock = Mutex()
    private var exRightsSyncedOn: LocalDate? = null

    fun observeWatchlist(): Flow<List<WatchlistRow>> = watchlistDao.observeRows()

    fun observeCandles(code: String): Flow<List<Candle>> =
        candleDao.observe(code).map { list -> list.map { it.toModel() } }

    suspend fun getCandles(code: String): List<Candle> = candleDao.getAll(code).map { it.toModel() }

    /** 指標計算用的價格序列；adjusted = true 時為除權息還原價。 */
    suspend fun getPriceSeries(code: String, adjusted: Boolean): PriceSeries =
        buildSeries(getCandles(code), exRightDao.getFor(code).map { it.toModel() }, adjusted)

    /** 除權息資料表有變動時發出新值。 */
    fun observeExRightsChanges(): Flow<Int> = exRightDao.observeCount()

    fun observePriceSeries(code: String, adjusted: Boolean): Flow<PriceSeries> =
        combine(observeCandles(code), exRightDao.observeFor(code)) { candles, events ->
            buildSeries(candles, events.map { it.toModel() }, adjusted)
        }

    private fun buildSeries(candles: List<Candle>, events: List<ExRightEvent>, adjusted: Boolean) = PriceSeries(
        candles = if (adjusted) PriceAdjuster.adjust(candles, events) else candles,
        exRightDates = events.mapTo(HashSet()) { it.exDate },
        adjusted = adjusted,
    )

    /**
     * 同步上市＋上櫃除權除息計算結果表（全市場）。
     * 首次下載 [HISTORY_MONTHS]+1 個月，之後從最後一筆事件日期補到今天；同一天只同步一次。
     */
    suspend fun syncExRights(force: Boolean = false) = exRightSyncLock.withLock {
        val today = LocalDate.now(TAIPEI)
        if (!force && exRightsSyncedOn == today) return@withLock
        for (market in Market.entries) {
            val from = exRightDao.latestEpochDay(market)?.let(LocalDate::ofEpochDay)
                ?: today.minusMonths(HISTORY_MONTHS + 1L)
            var chunkStart = from
            while (!chunkStart.isAfter(today)) {
                val end = chunkStart.plusMonths(EX_RIGHT_CHUNK_MONTHS).minusDays(1)
                val chunkEnd = if (end.isAfter(today)) today else end
                val records = fetchExRights(market, chunkStart, chunkEnd)
                if (records.isNotEmpty()) {
                    exRightDao.upsertAll(records.map {
                        ExRightEntity(it.code, it.event.exDate.toEpochDay(), market, it.event.closeBefore, it.event.referencePrice)
                    })
                }
                chunkStart = chunkEnd.plusDays(1)
            }
        }
        exRightsSyncedOn = today
    }

    private suspend fun fetchExRights(market: Market, from: LocalDate, to: LocalDate) = throttleFor(market).request(background = true) {
        when (market) {
            Market.TWSE -> {
                val r = twse.exRights(from.format(TWSE_DATE), to.format(TWSE_DATE))
                if (r.stat?.equals("OK", ignoreCase = true) == true) MarketDataParser.parseExRights(r.fields, r.data)
                else emptyList()
            }
            Market.TPEX -> {
                val r = tpex.exRights(from.format(TPEX_DATE), to.format(TPEX_DATE))
                val table = r.tables?.firstOrNull()
                MarketDataParser.parseExRights(table?.fields, table?.data ?: r.aaData)
            }
        }
    }

    // ---- 選股（全市場日 K） ----

    /**
     * 同步上市＋上櫃全市場每日收盤行情，保留最近 [MARKET_DAYS] 個交易日（每個交易日每個市場一個請求）。
     * 由今天往回查詢尚未下載的平日；查無資料的過去日期記為休市不再查詢，今天的資料尚未公布時下次再試。
     * @param onProgress (已完成請求數, 預估總請求數)
     */
    suspend fun syncMarketDaily(onProgress: (Int, Int) -> Unit = { _, _ -> }) = marketSyncLock.withLock {
        val today = LocalDate.now(TAIPEI)
        val weekdays = generateSequence(today) { it.minusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(MARKET_DAYS * 3 / 2)
            .toList()
        val known = Market.entries.associateWith { m -> marketDao.days(m).associateBy { it.epochDay } }
        val estimate = Market.entries.sumOf { m -> pendingDays(weekdays, known.getValue(m)) }
        val done = AtomicInteger(0)
        // 上市、上櫃為不同主機、各自節流，可同時下載
        suspend fun syncMarket(market: Market) {
            val days = known.getValue(market)
            var trading = 0
            for (date in weekdays) {
                if (trading >= MARKET_DAYS) break
                val epochDay = date.toEpochDay()
                val k = days[epochDay]
                if (k != null) {
                    if (k.hasData) trading++
                    continue
                }
                val quotes = fetchDailyQuotes(market, date)
                    .filter { MarketDataParser.isStock(it.code) || MarketDataParser.isEtf(it.code) }
                val n = done.incrementAndGet()
                onProgress(n, maxOf(estimate, n))
                if (quotes.isNotEmpty()) {
                    marketDao.upsertCandles(quotes.map {
                        val c = it.candle
                        MarketCandleEntity(it.code, epochDay, c.open, c.high, c.low, c.close, c.volume)
                    })
                    infoDao.upsertAll(quotes.map { StockInfoEntity(it.code, it.name, market) })
                    marketDao.upsertDay(MarketDayEntity(market, epochDay, hasData = true))
                    trading++
                } else if (date.isBefore(today)) {
                    marketDao.upsertDay(MarketDayEntity(market, epochDay, hasData = false))
                }
            }
        }
        coroutineScope { Market.entries.map { m -> async { syncMarket(m) } }.awaitAll() }
        val oldest = weekdays.last().toEpochDay()
        marketDao.pruneCandles(oldest)
        marketDao.pruneDays(oldest)
    }

    /** 湊滿 [MARKET_DAYS] 個交易日預估還需查詢的天數（未查過的日子先當作交易日）。 */
    private fun pendingDays(weekdays: List<LocalDate>, known: Map<Long, MarketDayEntity>): Int {
        var trading = 0
        var pending = 0
        for (date in weekdays) {
            if (trading >= MARKET_DAYS) break
            val k = known[date.toEpochDay()]
            if (k == null) pending++
            if (k == null || k.hasData) trading++
        }
        return pending
    }

    private suspend fun fetchDailyQuotes(market: Market, date: LocalDate): List<DailyQuote> = throttleFor(market).request(background = true) {
        when (market) {
            Market.TWSE -> twse.dailyQuotes(date.format(TWSE_DATE)).let { r ->
                val t = MarketDataParser.pickTable(r.tables, "代號", "收盤")
                if (t != null) MarketDataParser.parseDailyQuotes(t.fields, t.data, date)
                else MarketDataParser.parseDailyQuotes(r.fields, r.data, date)
            }
            Market.TPEX -> tpex.dailyQuotes(date.format(TPEX_DATE)).let { r ->
                val t = MarketDataParser.pickTable(r.tables, "代號", "收盤")
                MarketDataParser.parseDailyQuotes(t?.fields, t?.data ?: r.aaData, date)
            }
        }
    }

    /** 全市場日 K（股票代號 → 舊到新）。 */
    suspend fun getMarketSeries(): Map<String, List<Candle>> =
        marketDao.allCandles().groupBy({ it.code }, { it.toModel() })

    suspend fun latestMarketDay(): LocalDate? = marketDao.latestTradingDay()?.let(LocalDate::ofEpochDay)

    fun observeMarketChanges(): Flow<Int> = marketDao.observeDayCount()

    suspend fun getAllExRights(): Map<String, List<ExRightEvent>> =
        exRightDao.getAll().groupBy({ it.code }, { it.toModel() })

    suspend fun getStockInfoMap(): Map<String, StockInfoEntity> = infoDao.getAll().associateBy { it.code }

    suspend fun getStockInfo(code: String): StockInfoEntity? = infoDao.get(code)

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
        val otc = runCatching { latestTpexQuotes() }.getOrDefault(emptyList())
            .filter { MarketDataParser.isStock(it.code) || MarketDataParser.isEtf(it.code) }
            .map { StockInfoEntity(it.code, it.name, Market.TPEX) }
        if (listed.isEmpty() && otc.isEmpty()) error("無法下載股票清單，請檢查網路連線")
        infoDao.upsertAll(listed + otc)
    }

    /**
     * 上櫃清單取自最近一個交易日的全市場收盤行情（約 150 KB）。
     * openapi 的 tpex_mainboard_daily_close_quotes 含上萬檔權證、超過 4 MB，行動網路下容易中斷。
     */
    private suspend fun latestTpexQuotes(): List<DailyQuote> {
        var date = LocalDate.now(TAIPEI)
        repeat(7) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                val quotes = fetchDailyQuotes(Market.TPEX, date)
                if (quotes.isNotEmpty()) return quotes
            }
            date = date.minusDays(1)
        }
        return emptyList()
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
     * 更新日K（[HISTORY_MONTHS] 個月）：
     * 1. 從本月往回下載到本地最新一筆所在月份（本地無資料則下載全部月份），**新到舊**，第一個月下載完圖表即可顯示；
     * 2. 若本地最早資料晚於目標起始月份（例如先前下載中斷，或由選股資料預先帶入），再由新到舊補齊更早的月份。
     * @param foreground 使用者正在看的個股：優先於背景同步
     * @return 新寫入（或覆寫）的 K 棒數
     */
    suspend fun refreshCandles(code: String, market: Market, foreground: Boolean = false): Int {
        val throttle = throttleFor(market)
        return if (foreground) throttle.withPriority { downloadCandles(code, market, background = false) }
        else downloadCandles(code, market, background = true)
    }

    private suspend fun downloadCandles(code: String, market: Market, background: Boolean): Int {
        val thisMonth = YearMonth.from(LocalDate.now(TAIPEI))
        val targetStart = thisMonth.minusMonths(HISTORY_MONTHS - 1L)
        var written = 0
        suspend fun fetchAndStore(month: YearMonth): Int {
            val candles = fetchMonth(code, market, month, background)
            if (candles.isNotEmpty()) candleDao.upsertAll(candles.map { it.toEntity(code) })
            written += candles.size
            return candles.size
        }

        val latest = candleDao.latestEpochDay(code)?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }
        CandlePlan.forward(thisMonth, latest, targetStart).forEach { fetchAndStore(it) }

        if (code !in noOlderHistory) {
            val earliest = candleDao.earliestEpochDay(code)?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }
            for (month in CandlePlan.backfill(earliest, targetStart)) {
                if (fetchAndStore(month) == 0) {
                    noOlderHistory += code
                    break
                }
            }
        }
        return written
    }

    /**
     * 本地沒有這檔的日K時，先用選股的全市場資料（最近 [MARKET_DAYS] 個交易日）帶入，圖表可立即顯示。
     * @return 帶入的 K 棒數
     */
    suspend fun seedFromMarketData(code: String): Int {
        if (candleDao.latestEpochDay(code) != null) return 0
        val rows = marketDao.candlesFor(code)
        if (rows.isNotEmpty()) {
            candleDao.upsertAll(rows.map { CandleEntity(code, it.epochDay, it.open, it.high, it.low, it.close, it.volume) })
        }
        return rows.size
    }

    private suspend fun fetchMonth(code: String, market: Market, month: YearMonth, background: Boolean): List<Candle> =
        throttleFor(market).request(background) {
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
            twseThrottle.request(background = false) { twse.stockDay(month.atDay(1).format(TWSE_DATE), code) }
        }.getOrNull()
        if (twseResult?.stat.equals("OK", ignoreCase = true) && !twseResult?.data.isNullOrEmpty()) {
            val name = MarketDataParser.parseTwseName(twseResult?.title, code) ?: code
            return StockInfoEntity(code, name, Market.TWSE).also { infoDao.upsertAll(listOf(it)) }
        }
        val tpexResult = runCatching {
            tpexThrottle.request(background = false) { tpex.tradingStock(code, month.atDay(1).format(TPEX_DATE)) }
        }.getOrNull()
        val tpexRows = tpexResult?.tables?.firstOrNull()?.data ?: tpexResult?.aaData
        if (!tpexRows.isNullOrEmpty()) {
            return StockInfoEntity(code, code, Market.TPEX).also { infoDao.upsertAll(listOf(it)) }
        }
        return null
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
        /** 證交所約每 5 秒 3 個請求即可能被暫時封鎖 */
        private const val TWSE_INTERVAL_MS = 1_700L
        private const val TPEX_INTERVAL_MS = 600L
        private const val EX_RIGHT_CHUNK_MONTHS = 3L
        /** 選股保留的交易日數（首次同步約 2 × 65 個請求、約 3 分鐘） */
        const val MARKET_DAYS = 60
        private val TWSE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TPEX_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    }
}
