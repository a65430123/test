package com.twstock.ta.data.repository

import com.google.gson.JsonObject
import com.twstock.core.PriceAdjuster
import com.twstock.core.model.BrokerFlow
import com.twstock.core.model.Candle
import com.twstock.core.model.ChipDay
import com.twstock.core.model.ExRightEvent
import com.twstock.ta.data.local.AppDatabase
import com.twstock.ta.data.local.ChipSyncEntity
import com.twstock.ta.data.local.ExRightEntity
import com.twstock.ta.data.local.InstitutionalEntity
import com.twstock.ta.data.local.MarginEntity
import com.twstock.ta.data.local.Market
import com.twstock.ta.data.local.StockInfoEntity
import com.twstock.ta.data.local.WatchlistEntity
import com.twstock.ta.data.local.WatchlistRow
import com.twstock.ta.data.local.toEntity
import com.twstock.ta.data.remote.FinMindApi
import com.twstock.ta.data.remote.MarketDataParser
import com.twstock.ta.data.remote.TpexApi
import com.twstock.ta.data.remote.TwseApi
import com.twstock.ta.data.remote.TwseOpenApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    private val finMind: FinMindApi,
) {
    private val infoDao = db.stockInfoDao()
    private val watchlistDao = db.watchlistDao()
    private val candleDao = db.candleDao()
    private val exRightDao = db.exRightDao()
    private val chipDao = db.chipDao()

    /** 證交所對短時間大量請求會暫時封鎖 IP，所有月資料請求共用節流。 */
    private val throttle = Mutex()
    private var lastRequestAt = 0L

    private val exRightSyncLock = Mutex()
    private val chipSyncLock = Mutex()
    private val brokerCache = HashMap<String, List<BrokerFlow>>()
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

    private suspend fun fetchExRights(market: Market, from: LocalDate, to: LocalDate) = throttled {
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

    // ---- 籌碼 ----

    suspend fun getChips(code: String): List<ChipDay> =
        mergeChips(chipDao.getInstitutional(code), chipDao.getMargin(code))

    fun observeChips(code: String): Flow<List<ChipDay>> =
        combine(chipDao.observeInstitutional(code), chipDao.observeMargin(code)) { inst, margin ->
            mergeChips(inst, margin)
        }

    /** 籌碼資料有新下載時發出新值。 */
    fun observeChipChanges(): Flow<Int> = chipDao.observeSyncCount()

    private fun mergeChips(inst: List<InstitutionalEntity>, margin: List<MarginEntity>): List<ChipDay> {
        val instByDay = inst.associateBy { it.epochDay }
        val marginByDay = margin.associateBy { it.epochDay }
        return (instByDay.keys + marginByDay.keys).sorted().map { day ->
            val i = instByDay[day]
            val m = marginByDay[day]
            ChipDay(
                date = LocalDate.ofEpochDay(day),
                foreignNet = i?.foreignNet,
                trustNet = i?.trustNet,
                dealerNet = i?.dealerNet,
                totalNet = i?.totalNet,
                marginBalance = m?.marginBalance,
                marginChange = m?.marginChange,
                shortBalance = m?.shortBalance,
                shortChange = m?.shortChange,
            )
        }
    }

    /**
     * 同步自選股所在市場最近 [CHIP_DAYS] 個交易日的三大法人與融資融券（全市場、每日一個請求）。
     * 已下載的日期不再請求；當日資料尚未公布（回傳空）時不標記，下次再試。較舊的資料會刪除。
     */
    suspend fun syncChips() = chipSyncLock.withLock {
        val days = candleDao.recentTradingDays(CHIP_DAYS)
        if (days.isEmpty()) return@withLock
        val markets = watchlistDao.getAll().map { it.market }.toSet()
        for (market in markets) {
            for (dataset in listOf(DATASET_INSTITUTIONAL, DATASET_MARGIN)) {
                val synced = chipDao.syncedDays(market, dataset).toSet()
                for (day in days) {
                    if (day in synced) continue
                    if (fetchChipDay(market, dataset, LocalDate.ofEpochDay(day))) {
                        chipDao.markSynced(ChipSyncEntity(market, dataset, day))
                    }
                }
            }
        }
        val oldest = days.min()
        chipDao.pruneInstitutional(oldest)
        chipDao.pruneMargin(oldest)
        chipDao.pruneSync(oldest)
    }

    /** @return 是否取得資料 */
    private suspend fun fetchChipDay(market: Market, dataset: String, date: LocalDate): Boolean = throttled {
        val day = date.toEpochDay()
        if (dataset == DATASET_INSTITUTIONAL) {
            val records = when (market) {
                Market.TWSE -> twse.institutional(date.format(TWSE_DATE)).let { r ->
                    if (r.stat.equals("OK", ignoreCase = true)) MarketDataParser.parseInstitutional(r.fields, r.data)
                    else emptyList()
                }
                Market.TPEX -> tpex.institutional(date.format(TPEX_DATE)).let { r ->
                    val t = r.tables?.firstOrNull()
                    MarketDataParser.parseInstitutional(t?.fields, t?.data)
                }
            }
            chipDao.upsertInstitutional(records.map {
                InstitutionalEntity(it.code, day, it.foreignNet, it.trustNet, it.dealerNet, it.totalNet)
            })
            records.isNotEmpty()
        } else {
            val records = when (market) {
                Market.TWSE -> twse.margin(date.format(TWSE_DATE)).let { r ->
                    val t = MarketDataParser.pickTable(r.tables, "代號", "餘額")
                    if (t != null) MarketDataParser.parseMargin(t.fields, t.data)
                    else MarketDataParser.parseMargin(r.fields, r.data)
                }
                Market.TPEX -> tpex.margin(date.format(TPEX_DATE)).let { r ->
                    val t = MarketDataParser.pickTable(r.tables, "代號", "餘額")
                    MarketDataParser.parseMargin(t?.fields, t?.data)
                }
            }
            chipDao.upsertMargin(records.map {
                MarginEntity(it.code, day, it.marginBalance, it.marginChange, it.shortBalance, it.shortChange)
            })
            records.isNotEmpty()
        }
    }

    /**
     * 分點券商進出（FinMind，需 token）。結果依股票＋日期快取於記憶體。
     * @throws IllegalStateException token 無效、額度不足等 API 錯誤（訊息取自 FinMind）
     */
    suspend fun getBrokerFlows(code: String, date: LocalDate, token: String): List<BrokerFlow> {
        val key = "$code@$date"
        brokerCache[key]?.let { return it }
        val d = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val r = finMind.data("Bearer ${token.trim()}", FinMindApi.BROKER_DATASET, code, d, d)
        if (r.status != null && r.status != 200) error(r.msg ?: "FinMind 回應錯誤 ${r.status}")
        return MarketDataParser.parseBrokerFlows(r.data).also { if (it.isNotEmpty()) brokerCache[key] = it }
    }

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
        private const val EX_RIGHT_CHUNK_MONTHS = 3L
        /** 籌碼資料保留的交易日數（首次同步每個市場約 2 × 20 個請求） */
        const val CHIP_DAYS = 20
        private const val DATASET_INSTITUTIONAL = "institutional"
        private const val DATASET_MARGIN = "margin"
        private val TWSE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TPEX_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    }
}
