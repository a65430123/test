package com.twstock.core

import com.twstock.core.indicator.BollingerBands
import com.twstock.core.indicator.KdResult
import com.twstock.core.indicator.MacdResult
import com.twstock.core.model.Candle
import com.twstock.core.model.IndicatorParams
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.SignalSource
import com.twstock.core.signal.SignalWarnings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SignalWarningsTest {
    private val start = LocalDate.of(2024, 1, 1)
    private fun candles(vararg closes: Double) =
        closes.mapIndexed { i, c -> Candle(start.plusDays(i.toLong()), c, c, c, c, 1) }
    private fun nul(n: Int) = List<Double?>(n) { null }
    private fun bands(n: Int, lower: Double, upper: Double) =
        BollingerBands(List(n) { (lower + upper) / 2 }, List(n) { upper }, List(n) { lower })

    private fun warnings(
        c: List<Candle>,
        b: BollingerBands = BollingerBands(nul(c.size), nul(c.size), nul(c.size)),
        k: List<Double?> = nul(c.size),
        d: List<Double?> = nul(c.size),
        h: List<Double?> = nul(c.size),
        p: IndicatorParams = IndicatorParams(),
    ) = SignalWarnings.detect(c, b, KdResult(nul(c.size), k, d), MacdResult(h, h, h), p)
        .map { Triple(it.index, it.side, it.source) }

    @Test
    fun bollinger_pullbackAndBreakoutWarnings() {
        val c = candles(95.0, 100.5, 109.5, 112.0, 105.0)
        val w = warnings(c, bands(c.size, lower = 100.0, upper = 110.0))
        assertEquals(
            listOf(
                Triple(0, SignalSide.BUY, SignalSource.BOLLINGER), // 在下軌之下 → 收回即買
                Triple(1, SignalSide.BUY, SignalSource.BOLLINGER_BREAKOUT), // 距下軌 0.5%
                Triple(2, SignalSide.SELL, SignalSource.BOLLINGER_BREAKOUT), // 距上軌 0.46%
                Triple(3, SignalSide.SELL, SignalSource.BOLLINGER), // 在上軌之上 → 跌回即賣
            ),
            w,
        )
    }

    @Test
    fun bollinger_respectsSelectionAndThreshold() {
        val c = candles(95.0, 100.5)
        assertTrue(warnings(c, bands(2, 100.0, 110.0), p = IndicatorParams(bollingerSignals = false)).isEmpty())
        assertEquals(1, warnings(c, bands(2, 100.0, 110.0), p = IndicatorParams(bbNearPercent = 0.2)).size)
    }

    @Test
    fun kd_approachingCrosses() {
        val c = candles(1.0, 1.0, 1.0, 1.0)
        // 低檔：K 15→17、D 19（差 2，上升）；中檔：K 48→50、D 52；高檔：K 85→83、D 81（差 2，下降）
        val w = warnings(c, k = listOf(15.0, 17.0, 48.0, 50.0), d = listOf(20.0, 19.0, 52.0, 52.0))
        assertEquals(listOf(1 to SignalSide.BUY, 3 to SignalSide.BUY), w.map { it.first to it.second })
        val sell = warnings(candles(1.0, 1.0), k = listOf(85.0, 83.0), d = listOf(80.0, 81.0))
        assertEquals(listOf(Triple(1, SignalSide.SELL, SignalSource.KD)), sell)
        // 差距太大或方向不對不預告；關閉中檔時中檔不預告
        assertTrue(warnings(candles(1.0, 1.0), k = listOf(10.0, 12.0), d = listOf(20.0, 20.0)).isEmpty())
        assertTrue(warnings(candles(1.0, 1.0), k = listOf(18.0, 17.0), d = listOf(19.0, 19.0)).isEmpty())
        assertTrue(warnings(candles(1.0, 1.0), k = listOf(48.0, 50.0), d = listOf(52.0, 52.0), p = IndicatorParams(kdMidCross = false)).isEmpty())
    }

    @Test
    fun macd_crossAndTurnWarnings() {
        // 交叉：-3 → -1，外推明日 +1 ≥ 0
        val cross = warnings(candles(1.0, 1.0), h = listOf(-3.0, -1.0), p = IndicatorParams(macdHistogramTurn = false))
        assertEquals(listOf(Triple(1, SignalSide.BUY, SignalSource.MACD)), cross)
        // 轉折：正柱狀 1 → 3（+2）→ 4（+1，增幅縮小且仍為本段最高）
        val turn = warnings(candles(1.0, 1.0, 1.0), h = listOf(1.0, 3.0, 4.0), p = IndicatorParams(macdCross = false))
        assertEquals(listOf(Triple(2, SignalSide.SELL, SignalSource.MACD_HISTOGRAM)), turn)
        // 負柱狀 -1 → -3 → -4 → 低點轉折預告
        val low = warnings(candles(1.0, 1.0, 1.0), h = listOf(-1.0, -3.0, -4.0), p = IndicatorParams(macdCross = false))
        assertEquals(listOf(Triple(2, SignalSide.BUY, SignalSource.MACD_HISTOGRAM)), low)
        // 增幅擴大不預告
        assertTrue(warnings(candles(1.0, 1.0, 1.0), h = listOf(1.0, 2.0, 4.0), p = IndicatorParams(macdCross = false)).isEmpty())
    }

    @Test
    fun analyzerIncludesWarnings_andValidation() {
        val c = candles(*DoubleArray(40) { 100.0 + it % 3 })
        val r = TechnicalAnalyzer.analyze(c, IndicatorParams())
        assertTrue(r.warnings.all { it.index in c.indices })
        assertTrue(IndicatorParams(bbNearPercent = -1.0).validate().isNotEmpty())
        assertTrue(IndicatorParams(kdNearGap = 60.0).validate().isNotEmpty())
    }
}
