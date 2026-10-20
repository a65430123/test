package com.twstock.core.indicator

import com.twstock.core.model.Candle
import kotlin.math.sqrt

data class BollingerBands(
    val middle: List<Double?>,
    val upper: List<Double?>,
    val lower: List<Double?>,
)

data class KdResult(
    val rsv: List<Double?>,
    val k: List<Double?>,
    val d: List<Double?>,
)

data class MacdResult(
    /** DIF = EMA(fast) - EMA(slow) */
    val dif: List<Double?>,
    /** 訊號線（俗稱 MACD 線）= EMA(DIF, signal) */
    val signal: List<Double?>,
    /** 柱狀體 OSC = DIF - 訊號線 */
    val histogram: List<Double?>,
)

/** 所有函式回傳的 List 與輸入等長；資料不足的前段以 null 表示。 */
object Indicators {

    fun sma(values: List<Double>, period: Int): List<Double?> {
        require(period >= 1) { "period must be >= 1" }
        val out = arrayOfNulls<Double>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out.toList()
    }

    /**
     * 指數移動平均，alpha = 2 / (period + 1)。
     * 以第一段 period 個有效值的簡單平均作為起始值；輸入前段可為 null（例如 DIF 尚未產生時）。
     */
    fun ema(values: List<Double?>, period: Int): List<Double?> {
        require(period >= 1) { "period must be >= 1" }
        val out = arrayOfNulls<Double>(values.size)
        val start = values.indexOfFirst { it != null }
        if (start < 0 || values.size - start < period) return out.toList()
        val alpha = 2.0 / (period + 1)
        val seedIndex = start + period - 1
        var prev = (start..seedIndex).sumOf { values[it]!! } / period
        out[seedIndex] = prev
        for (i in seedIndex + 1 until values.size) {
            val v = values[i] ?: continue
            prev = alpha * v + (1 - alpha) * prev
            out[i] = prev
        }
        return out.toList()
    }

    /** 布林通道：中軌 = N 日均線，上下軌 = 中軌 ± multiplier × 母體標準差。 */
    fun bollinger(closes: List<Double>, period: Int, multiplier: Double): BollingerBands {
        require(period >= 1) { "period must be >= 1" }
        val n = closes.size
        val mid = arrayOfNulls<Double>(n)
        val up = arrayOfNulls<Double>(n)
        val lo = arrayOfNulls<Double>(n)
        for (i in period - 1 until n) {
            var sum = 0.0
            for (j in i - period + 1..i) sum += closes[j]
            val mean = sum / period
            var sq = 0.0
            for (j in i - period + 1..i) {
                val diff = closes[j] - mean
                sq += diff * diff
            }
            val sd = sqrt(sq / period)
            mid[i] = mean
            up[i] = mean + multiplier * sd
            lo[i] = mean - multiplier * sd
        }
        return BollingerBands(mid.toList(), up.toList(), lo.toList())
    }

    /**
     * KD 隨機指標（台灣常用算法）。
     * RSV = (今日收盤 - N日最低) / (N日最高 - N日最低) × 100
     * K = 前日K × (kSmoothing-1)/kSmoothing + RSV / kSmoothing
     * D = 前日D × (dSmoothing-1)/dSmoothing + K / dSmoothing
     * 第一筆可計算的 K、D 以 initial 作為「前日值」。
     * 若 N 日最高等於最低（價格完全不動），RSV 視為前日 K，使 K 維持不變。
     */
    fun kd(
        candles: List<Candle>,
        period: Int,
        kSmoothing: Int = 3,
        dSmoothing: Int = 3,
        initial: Double = 50.0,
    ): KdResult {
        require(period >= 1 && kSmoothing >= 1 && dSmoothing >= 1)
        val n = candles.size
        val rsvOut = arrayOfNulls<Double>(n)
        val kOut = arrayOfNulls<Double>(n)
        val dOut = arrayOfNulls<Double>(n)
        var prevK = initial
        var prevD = initial
        for (i in period - 1 until n) {
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            for (j in i - period + 1..i) {
                if (candles[j].high > hh) hh = candles[j].high
                if (candles[j].low < ll) ll = candles[j].low
            }
            val rsv = if (hh == ll) prevK else (candles[i].close - ll) / (hh - ll) * 100.0
            val k = prevK * (kSmoothing - 1) / kSmoothing + rsv / kSmoothing
            val d = prevD * (dSmoothing - 1) / dSmoothing + k / dSmoothing
            rsvOut[i] = rsv
            kOut[i] = k
            dOut[i] = d
            prevK = k
            prevD = d
        }
        return KdResult(rsvOut.toList(), kOut.toList(), dOut.toList())
    }

    fun macd(closes: List<Double>, fast: Int, slow: Int, signal: Int): MacdResult {
        val emaFast = ema(closes, fast)
        val emaSlow = ema(closes, slow)
        val dif = closes.indices.map { i ->
            val f = emaFast[i]
            val s = emaSlow[i]
            if (f != null && s != null) f - s else null
        }
        val sig = ema(dif, signal)
        val hist = closes.indices.map { i ->
            val d = dif[i]
            val s = sig[i]
            if (d != null && s != null) d - s else null
        }
        return MacdResult(dif, sig, hist)
    }
}
