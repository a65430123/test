package com.twstock.core

import com.twstock.core.model.Candle
import com.twstock.core.model.ExRightEvent
import com.twstock.core.model.IndicatorParams
import com.twstock.core.signal.TradeSignal
import java.time.LocalDate

/** 選股結果：最新交易日出現進出場訊號的股票。價格與成交量為原始（未還原）數值。 */
data class ScreenHit(
    val code: String,
    val signal: TradeSignal,
    val close: Double,
    val prevClose: Double?,
    /** 成交量（張） */
    val volume: Long,
)

object Screener {
    /** 計算指標所需的最少 K 棒數（MACD 訊號線約需 34 根） */
    const val MIN_BARS = 35

    /**
     * 掃描所有股票，找出 [latestDate] 當日出現訊號者（依目前的訊號來源與確認方式），依成交量由大到小排序。
     * 最後一根 K 棒不是 [latestDate]（當日未交易）或資料不足 [MIN_BARS] 的股票略過。
     */
    fun scan(
        series: Map<String, List<Candle>>,
        params: IndicatorParams,
        latestDate: LocalDate,
        exRights: Map<String, List<ExRightEvent>> = emptyMap(),
        adjust: Boolean = false,
    ): List<ScreenHit> = series.mapNotNull { (code, raw) ->
        val last = raw.lastOrNull() ?: return@mapNotNull null
        if (last.date != latestDate || raw.size < MIN_BARS) return@mapNotNull null
        val candles = if (adjust) PriceAdjuster.adjust(raw, exRights[code].orEmpty()) else raw
        val signal = TechnicalAnalyzer.analyze(candles, params).signalOnLastBar ?: return@mapNotNull null
        ScreenHit(code, signal, last.close, raw.getOrNull(raw.size - 2)?.close, last.volume)
    }.sortedByDescending { it.volume }
}
