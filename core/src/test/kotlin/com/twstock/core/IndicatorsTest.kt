package com.twstock.core

import com.twstock.core.indicator.Indicators
import com.twstock.core.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sqrt

class IndicatorsTest {
    private val eps = 1e-9

    private fun candle(i: Int, high: Double, low: Double, close: Double) =
        Candle(LocalDate.of(2024, 1, 1).plusDays(i.toLong()), close, high, low, close, 1000)

    @Test
    fun sma_basic() {
        val r = Indicators.sma(listOf(1.0, 2.0, 3.0, 4.0), 2)
        assertNull(r[0])
        assertEquals(1.5, r[1]!!, eps)
        assertEquals(3.5, r[3]!!, eps)
    }

    @Test
    fun ema_seededWithSma_thenRecursive() {
        val r = Indicators.ema(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertNull(r[1])
        assertEquals(2.0, r[2]!!, eps) // SMA(1,2,3)
        assertEquals(0.5 * 4 + 0.5 * 2.0, r[3]!!, eps) // alpha = 2/(3+1)
        assertEquals(0.5 * 5 + 0.5 * 3.0, r[4]!!, eps)
    }

    @Test
    fun ema_skipsLeadingNulls() {
        val r = Indicators.ema(listOf(null, null, 2.0, 4.0, 6.0), 2)
        assertNull(r[2])
        assertEquals(3.0, r[3]!!, eps)
        assertEquals(2.0 / 3 * 6 + 1.0 / 3 * 3.0, r[4]!!, eps)
    }

    @Test
    fun bollinger_20Day_2Sd() {
        val closes = (1..20).map { it.toDouble() }
        val b = Indicators.bollinger(closes, 20, 2.0)
        assertNull(b.middle[18])
        val sd = sqrt((20.0 * 20 - 1) / 12) // 母體標準差 of 1..20
        assertEquals(10.5, b.middle[19]!!, eps)
        assertEquals(10.5 + 2 * sd, b.upper[19]!!, eps)
        assertEquals(10.5 - 2 * sd, b.lower[19]!!, eps)
    }

    @Test
    fun kd_matchesTaiwanFormula() {
        // 9 根 K 棒，最高 20、最低 10，最後收盤 18 → RSV = 80
        val candles = (0 until 9).map { candle(it, 20.0, 10.0, 15.0) }.toMutableList()
        candles[8] = candle(8, 20.0, 10.0, 18.0)
        candles += candle(9, 20.0, 12.0, 12.0) // 第 10 日 RSV = (12-10)/(20-10)*100 = 20

        val r = Indicators.kd(candles, 9, 3, 3, 50.0)
        assertNull(r.k[7])
        assertEquals(80.0, r.rsv[8]!!, eps)
        val k8 = 50.0 * 2 / 3 + 80.0 / 3
        val d8 = 50.0 * 2 / 3 + k8 / 3
        assertEquals(k8, r.k[8]!!, eps)
        assertEquals(d8, r.d[8]!!, eps)

        assertEquals(20.0, r.rsv[9]!!, eps)
        val k9 = k8 * 2 / 3 + 20.0 / 3
        assertEquals(k9, r.k[9]!!, eps)
        assertEquals(d8 * 2 / 3 + k9 / 3, r.d[9]!!, eps)
    }

    @Test
    fun kd_flatRange_keepsPreviousK() {
        val candles = (0 until 12).map { candle(it, 10.0, 10.0, 10.0) }
        val r = Indicators.kd(candles, 9, 3, 3, 50.0)
        assertEquals(50.0, r.k[11]!!, eps)
        assertEquals(50.0, r.d[11]!!, eps)
    }

    @Test
    fun macd_constantPrice_isZero_andStartsAtExpectedIndex() {
        val closes = List(60) { 100.0 }
        val m = Indicators.macd(closes, 12, 26, 9)
        assertNull(m.dif[24])
        assertEquals(0.0, m.dif[25]!!, eps)
        assertNull(m.signal[32])
        assertEquals(0.0, m.signal[33]!!, eps)
        assertEquals(0.0, m.histogram[59]!!, eps)
    }

    @Test
    fun macd_risingPrice_hasPositiveDif() {
        val closes = (1..80).map { it.toDouble() }
        val m = Indicators.macd(closes, 12, 26, 9)
        assert(m.dif[79]!! > 0)
        // 線性上升時 DIF 趨近常數 (26-12)/2 = 7
        assertEquals(7.0, m.dif[79]!!, 0.05)
    }
}
