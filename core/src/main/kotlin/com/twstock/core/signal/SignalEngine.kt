package com.twstock.core.signal

import com.twstock.core.indicator.BollingerBands
import com.twstock.core.indicator.KdResult
import com.twstock.core.indicator.MacdResult
import com.twstock.core.model.Candle
import com.twstock.core.model.IndicatorParams

/**
 * 進出場規則：
 * - 布林收回：前日收盤跌破下軌、今日收回下軌之上 → 買；前日收盤突破上軌、今日跌回上軌之下 → 賣。
 * - 布林突破（超買／超賣）：收盤由上軌之下突破上軌 → 賣；收盤由下軌之上跌破下軌 → 買。
 *   可要求量增：當日量 ≥ bbBreakoutVolumeRatio × 前一日的短期成交量均線。
 * - KD：K 由下往上穿越 D（黃金交叉）且前日 K 位於超賣區 → 買；K 由上往下穿越 D（死亡交叉）且前日 K 位於超買區 → 賣。
 * - MACD：DIF 由下往上穿越訊號線（柱狀體翻正）→ 買；由上往下穿越（柱狀體翻負）→ 賣。
 *
 * 只選一個指標作為訊號來源時，該指標的每個事件都是訊號（不需確認）。
 * 多選時的確認機制：當日有事件觸發，且在最近 confirmWindow 個交易日內（含當日）
 * 有至少 minConfirmations 個「不同指標」出現同方向事件，即產生訊號。
 * 同方向訊號產生後，於 confirmWindow 日內不重複發出。
 */
object SignalEngine {

    fun detectEvents(
        candles: List<Candle>,
        bands: BollingerBands,
        kd: KdResult,
        macd: MacdResult,
        params: IndicatorParams,
        volumeMa: List<Double?> = List(candles.size) { null },
    ): List<IndicatorEvent> {
        val events = mutableListOf<IndicatorEvent>()
        for (i in 1 until candles.size) {
            val date = candles[i].date
            if (params.bollingerPullbackActive) bollingerEvent(i, candles, bands)?.let { (side, desc) ->
                events += IndicatorEvent(i, date, side, SignalSource.BOLLINGER, desc)
            }
            if (params.bollingerBreakoutActive) breakoutEvent(i, candles, bands, volumeMa, params)?.let { (side, desc) ->
                events += IndicatorEvent(i, date, side, SignalSource.BOLLINGER_BREAKOUT, desc)
            }
            if (params.useKd) kdEvent(i, kd, params)?.let { (side, desc) ->
                events += IndicatorEvent(i, date, side, SignalSource.KD, desc)
            }
            if (params.useMacd) macdEvent(i, macd)?.let { (side, desc) ->
                events += IndicatorEvent(i, date, side, SignalSource.MACD, desc)
            }
        }
        return events
    }

    fun confirmSignals(
        candles: List<Candle>,
        events: List<IndicatorEvent>,
        params: IndicatorParams,
    ): List<TradeSignal> {
        val required = params.requiredConfirmations
        val window = params.confirmWindow.coerceAtLeast(1)
        val byIndex = events.groupBy { it.index }
        val lastSignalIndex = mutableMapOf<SignalSide, Int>()
        val signals = mutableListOf<TradeSignal>()

        for (i in byIndex.keys.sorted()) {
            for (side in SignalSide.entries) {
                if (byIndex[i].orEmpty().none { it.side == side }) continue
                val last = lastSignalIndex[side]
                if (last != null && i - last < window) continue
                val inWindow = events.filter { it.side == side && it.index in (i - window + 1)..i }
                // 每個指標只取窗口內最近一次事件
                val distinct = inWindow.groupBy { it.source }.map { (_, list) -> list.maxBy { it.index } }
                if (distinct.size >= required) {
                    signals += TradeSignal(
                        index = i,
                        date = candles[i].date,
                        side = side,
                        price = candles[i].close,
                        events = distinct.sortedBy { it.index },
                    )
                    lastSignalIndex[side] = i
                }
            }
        }
        return signals
    }

    private fun bollingerEvent(i: Int, c: List<Candle>, b: BollingerBands): Pair<SignalSide, String>? {
        val prevLower = b.lower[i - 1] ?: return null
        val prevUpper = b.upper[i - 1] ?: return null
        val lower = b.lower[i] ?: return null
        val upper = b.upper[i] ?: return null
        val prevClose = c[i - 1].close
        val close = c[i].close
        return when {
            prevClose < prevLower && close >= lower -> SignalSide.BUY to "布林跌破下軌後收回"
            prevClose > prevUpper && close <= upper -> SignalSide.SELL to "布林突破上軌後跌回"
            else -> null
        }
    }

    private fun breakoutEvent(
        i: Int,
        c: List<Candle>,
        b: BollingerBands,
        volumeMa: List<Double?>,
        p: IndicatorParams,
    ): Pair<SignalSide, String>? {
        val prevLower = b.lower[i - 1] ?: return null
        val prevUpper = b.upper[i - 1] ?: return null
        val lower = b.lower[i] ?: return null
        val upper = b.upper[i] ?: return null
        val prevClose = c[i - 1].close
        val close = c[i].close
        val result = when {
            prevClose <= prevUpper && close > upper -> SignalSide.SELL to "突破布林上軌"
            prevClose >= prevLower && close < lower -> SignalSide.BUY to "跌破布林下軌"
            else -> return null
        }
        if (p.bbBreakoutVolumeRatio <= 0.0) return result
        val avg = volumeMa[i - 1]?.takeIf { it > 0 } ?: return null
        val ratio = c[i].volume / avg
        if (ratio < p.bbBreakoutVolumeRatio) return null
        return result.first to "%s（量 %.1f 倍）".format(result.second, ratio)
    }

    private fun kdEvent(i: Int, kd: KdResult, p: IndicatorParams): Pair<SignalSide, String>? {
        val pk = kd.k[i - 1] ?: return null
        val pd = kd.d[i - 1] ?: return null
        val k = kd.k[i] ?: return null
        val d = kd.d[i] ?: return null
        return when {
            pk <= pd && k > d && pk <= p.kdOversold ->
                SignalSide.BUY to "KD 低檔黃金交叉 (K=%.1f)".format(k)
            pk >= pd && k < d && pk >= p.kdOverbought ->
                SignalSide.SELL to "KD 高檔死亡交叉 (K=%.1f)".format(k)
            else -> null
        }
    }

    private fun macdEvent(i: Int, m: MacdResult): Pair<SignalSide, String>? {
        val ph = m.histogram[i - 1] ?: return null
        val h = m.histogram[i] ?: return null
        return when {
            ph <= 0 && h > 0 -> SignalSide.BUY to "MACD 黃金交叉"
            ph >= 0 && h < 0 -> SignalSide.SELL to "MACD 死亡交叉"
            else -> null
        }
    }
}
