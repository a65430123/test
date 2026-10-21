package com.twstock.ta.data.local

import androidx.room.Entity
import androidx.room.Index
import com.twstock.core.model.Candle
import com.twstock.ta.data.remote.MarketDataParser
import com.twstock.core.model.ExRightEvent
import java.time.LocalDate

enum class Market(val label: String) {
    TWSE("上市"),
    TPEX("上櫃"),
}

/** 顯示用的市場別，ETF 加註，例如「上市ETF」。 */
fun Market.labelFor(code: String): String = if (MarketDataParser.isEtf(code)) "${label}ETF" else label

@Entity(tableName = "stock_info", primaryKeys = ["code"], indices = [Index("name")])
data class StockInfoEntity(
    val code: String,
    val name: String,
    val market: Market,
)

@Entity(tableName = "watchlist", primaryKeys = ["code"])
data class WatchlistEntity(
    val code: String,
    val name: String,
    val market: Market,
    val addedAt: Long = System.currentTimeMillis(),
    /** 最近一次已推播訊號的日期 (epochDay)，用於避免重複推播。 */
    val lastNotifiedEpochDay: Long = 0,
)

@Entity(tableName = "candles", primaryKeys = ["code", "epochDay"])
data class CandleEntity(
    val code: String,
    val epochDay: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    /** 成交量（張） */
    val volume: Long,
) {
    fun toModel() = Candle(LocalDate.ofEpochDay(epochDay), open, high, low, close, volume)
}

fun Candle.toEntity(code: String) =
    CandleEntity(code, date.toEpochDay(), open, high, low, close, volume)

/** 除權除息事件（全市場皆儲存，加入新自選股時不需重新下載）。 */
@Entity(tableName = "ex_rights", primaryKeys = ["code", "epochDay"])
data class ExRightEntity(
    val code: String,
    val epochDay: Long,
    val market: Market,
    val closeBefore: Double,
    val referencePrice: Double,
) {
    fun toModel() = ExRightEvent(LocalDate.ofEpochDay(epochDay), closeBefore, referencePrice)
}

/** 選股用的全市場日 K（僅保留最近 [com.twstock.ta.data.repository.StockRepository.MARKET_DAYS] 個交易日）。 */
@Entity(tableName = "market_candles", primaryKeys = ["code", "epochDay"])
data class MarketCandleEntity(
    val code: String,
    val epochDay: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    /** 成交量（張） */
    val volume: Long,
) {
    fun toModel() = Candle(LocalDate.ofEpochDay(epochDay), open, high, low, close, volume)
}

/** 已查詢過的市場日期；hasData = false 表示休市，不再重複查詢。 */
@Entity(tableName = "market_days", primaryKeys = ["market", "epochDay"])
data class MarketDayEntity(
    val market: Market,
    val epochDay: Long,
    val hasData: Boolean,
)

/** 自選股清單列：自選股 + 最新兩日收盤價。 */
data class WatchlistRow(
    val code: String,
    val name: String,
    val market: Market,
    val lastNotifiedEpochDay: Long,
    val lastEpochDay: Long?,
    val lastClose: Double?,
    val prevClose: Double?,
)
