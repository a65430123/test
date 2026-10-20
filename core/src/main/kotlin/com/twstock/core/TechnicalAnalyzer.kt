package com.twstock.core

import com.twstock.core.indicator.BollingerBands
import com.twstock.core.indicator.Indicators
import com.twstock.core.indicator.KdResult
import com.twstock.core.indicator.MacdResult
import com.twstock.core.model.Candle
import com.twstock.core.model.IndicatorParams
import com.twstock.core.signal.IndicatorEvent
import com.twstock.core.signal.SignalEngine
import com.twstock.core.signal.TradeSignal

data class AnalysisResult(
    val candles: List<Candle>,
    val bollinger: BollingerBands,
    val kd: KdResult,
    val macd: MacdResult,
    val events: List<IndicatorEvent>,
    val signals: List<TradeSignal>,
) {
    /** 最後一根 K 棒當日產生的訊號（推播用）。 */
    val signalOnLastBar: TradeSignal?
        get() = signals.lastOrNull()?.takeIf { it.index == candles.lastIndex }
}

object TechnicalAnalyzer {
    /** candles 需依日期由舊到新排序。 */
    fun analyze(candles: List<Candle>, params: IndicatorParams): AnalysisResult {
        val closes = candles.map { it.close }
        val bands = Indicators.bollinger(closes, params.bbPeriod, params.bbMultiplier)
        val kd = Indicators.kd(candles, params.kdPeriod, params.kdKSmoothing, params.kdDSmoothing, params.kdInitial)
        val macd = Indicators.macd(closes, params.macdFast, params.macdSlow, params.macdSignal)
        val events = SignalEngine.detectEvents(candles, bands, kd, macd, params)
        val signals = SignalEngine.confirmSignals(candles, events, params)
        return AnalysisResult(candles, bands, kd, macd, events, signals)
    }
}
