package com.twstock.ta.data.local

import androidx.room.Entity
import androidx.room.Index
import com.twstock.core.model.Candle
import java.time.LocalDate

enum class Market(val label: String) {
    TWSE("上市"),
    TPEX("上櫃"),
}

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
