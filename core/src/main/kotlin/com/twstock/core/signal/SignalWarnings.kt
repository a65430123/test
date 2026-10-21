package com.twstock.core.signal

import com.twstock.core.indicator.BollingerBands
import com.twstock.core.indicator.KdResult
import com.twstock.core.indicator.MacdResult
import com.twstock.core.model.Candle
import com.twstock.core.model.IndicatorParams
import java.time.LocalDate
import kotlin.math.abs

/** 訊號預告：某類型的進出場訊號條件已接近，下一個交易日可能觸發。 */
data class SignalWarning(
    val index: Int,
    val date: LocalDate,
    val side: SignalSide,
    val source: SignalSource,
    val description: String,
)

/**
 * 接近程度判斷（依已選的訊號來源與細項開關）：
 * - 布林收回：收盤在下軌之下 → 收回即買；收盤在上軌之上 → 跌回即賣。
 * - 布林突破：收盤在下軌之上且距離 ≤ bbNearPercent% → 跌破即買；在上軌之下且距離 ≤ bbNearPercent% → 突破即賣。
 * - KD：K 在 D 之下、差距 ≤ kdNearGap 且 K 上升 → 即將黃金交叉（低檔或中檔）；反之即將死亡交叉（高檔或中檔）。
 * - MACD 交叉：依柱狀體今日變化量線性外推，明日將穿越 0 軸。
 * - MACD 柱狀轉折：柱狀仍創本段新高（低），但增（跌）幅已較前一日縮小。
 */
object SignalWarnings {

    fun detect(
        candles: List<Candle>,
        bands: BollingerBands,
        kd: KdResult,
        macd: MacdResult,
        params: IndicatorParams,
    ): List<SignalWarning> {
        val out = mutableListOf<SignalWarning>()
        for (i in candles.indices) {
            fun add(side: SignalSide, source: SignalSource, desc: String) {
                out += SignalWarning(i, candles[i].date, side, source, desc)
            }
            val close = candles[i].close
            val lower = bands.lower[i]
            val upper = bands.upper[i]
            if (lower != null && upper != null) {
                if (params.bollingerPullbackActive) {
                    if (close < lower) add(SignalSide.BUY, SignalSource.BOLLINGER, "收盤在布林下軌之下（%.1f%%），收回即買進".format(pct(close, lower)))
                    else if (close > upper) add(SignalSide.SELL, SignalSource.BOLLINGER, "收盤在布林上軌之上（+%.1f%%），跌回即賣出".format(pct(close, upper)))
                }
                if (params.bollingerBreakoutActive) {
                    val toLower = pct(close, lower)
                    val toUpper = pct(upper, close)
                    if (close >= lower && toLower <= params.bbNearPercent) {
                        add(SignalSide.BUY, SignalSource.BOLLINGER_BREAKOUT, "距布林下軌 %.1f%%，跌破即買進".format(toLower))
                    } else if (close <= upper && toUpper <= params.bbNearPercent) {
                        add(SignalSide.SELL, SignalSource.BOLLINGER_BREAKOUT, "距布林上軌 %.1f%%，突破即賣出".format(toUpper))
                    }
                }
            }
            if (params.useKd && i >= 1) kdWarning(i, kd, params)?.let { (side, desc) -> add(side, SignalSource.KD, desc) }
            if (i >= 1) {
                if (params.macdCrossActive) macdCrossWarning(i, macd)?.let { (side, desc) -> add(side, SignalSource.MACD, desc) }
                if (params.macdHistogramActive && i >= 2) {
                    macdTurnWarning(i, macd)?.let { (side, desc) -> add(side, SignalSource.MACD_HISTOGRAM, desc) }
                }
            }
        }
        return out
    }

    /** a 相對 b 的百分比差 */
    private fun pct(a: Double, b: Double) = (a - b) / b * 100

    private fun kdWarning(i: Int, kd: KdResult, p: IndicatorParams): Pair<SignalSide, String>? {
        val k = kd.k[i] ?: return null
        val d = kd.d[i] ?: return null
        val pk = kd.k[i - 1] ?: return null
        val detail = "（K=%.1f、D=%.1f）".format(k, d)
        return when {
            k < d && d - k <= p.kdNearGap && k > pk -> when {
                k <= p.kdOversold -> SignalSide.BUY to "KD 低檔即將黃金交叉$detail"
                p.kdMidCross && k < p.kdOverbought -> SignalSide.BUY to "KD 即將黃金交叉$detail"
                else -> null
            }
            k > d && k - d <= p.kdNearGap && k < pk -> when {
                k >= p.kdOverbought -> SignalSide.SELL to "KD 高檔即將死亡交叉$detail"
                p.kdMidCross && k > p.kdOversold -> SignalSide.SELL to "KD 即將死亡交叉$detail"
                else -> null
            }
            else -> null
        }
    }

    private fun macdCrossWarning(i: Int, m: MacdResult): Pair<SignalSide, String>? {
        val h = m.histogram[i] ?: return null
        val ph = m.histogram[i - 1] ?: return null
        val next = h + (h - ph)
        return when {
            h < 0 && h > ph && next >= 0 -> SignalSide.BUY to "MACD 柱狀接近 0 軸（OSC=%.2f），即將黃金交叉".format(h)
            h > 0 && h < ph && next <= 0 -> SignalSide.SELL to "MACD 柱狀接近 0 軸（OSC=%.2f），即將死亡交叉".format(h)
            else -> null
        }
    }

    private fun macdTurnWarning(i: Int, m: MacdResult): Pair<SignalSide, String>? {
        val h = m.histogram[i] ?: return null
        val ph = m.histogram[i - 1] ?: return null
        val pph = m.histogram[i - 2] ?: return null
        val step = h - ph
        val prevStep = ph - pph
        return when {
            h > 0 && ph > 0 && step > 0 && prevStep > 0 && step < prevStep && SignalEngine.isRunExtreme(m.histogram, i, positive = true) ->
                SignalSide.SELL to "MACD 正柱狀增幅縮小，可能即將高點轉折"
            h < 0 && ph < 0 && step < 0 && prevStep < 0 && abs(step) < abs(prevStep) && SignalEngine.isRunExtreme(m.histogram, i, positive = false) ->
                SignalSide.BUY to "MACD 負柱狀跌幅縮小，可能即將低點轉折"
            else -> null
        }
    }
}
