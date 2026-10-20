package com.twstock.core

import com.twstock.core.model.Candle
import com.twstock.core.model.IndicatorParams
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.SignalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BollingerBreakoutTest {
    private val start = LocalDate.of(2024, 3, 1)

    /** 25 日窄幅盤整後，最後一日收在 last、量為 lastVolume。 */
    private fun series(last: Double, lastVolume: Long = 1000): List<Candle> {
        val closes = List(25) { 100.0 + if (it % 2 == 0) 0.5 else -0.5 } + last
        return closes.mapIndexed { i, c ->
            Candle(start.plusDays(i.toLong()), c, c + 0.2, c - 0.2, c, if (i == closes.lastIndex) lastVolume else 1000)
        }
    }

    private val onlyBreakout = IndicatorParams(
        useBollinger = false, useKd = false, useMacd = false, useBollingerBreakout = true, minConfirmations = 1,
    )

    private fun breakoutEvents(candles: List<Candle>, p: IndicatorParams) =
        TechnicalAnalyzer.analyze(candles, p).events.filter { it.source == SignalSource.BOLLINGER_BREAKOUT }

    @Test
    fun closeAboveUpperBand_isSell() {
        val e = breakoutEvents(series(104.0), onlyBreakout)
        assertEquals(1, e.size)
        assertEquals(SignalSide.SELL, e[0].side)
        assertEquals(25, e[0].index)
        assertEquals("突破布林上軌", e[0].description)
    }

    @Test
    fun closeBelowLowerBand_isBuy() {
        val r = TechnicalAnalyzer.analyze(series(96.0), onlyBreakout)
        val e = r.events.single()
        assertEquals(SignalSide.BUY, e.side)
        assertEquals("跌破布林下軌", e.description)
        assertEquals(SignalSide.BUY, r.signals.single().side)
    }

    @Test
    fun insideBands_noEvent() {
        assertTrue(breakoutEvents(series(100.3), onlyBreakout).isEmpty())
    }

    @Test
    fun volumeFilter() {
        val p = onlyBreakout.copy(bbBreakoutVolumeRatio = 1.5)
        assertTrue(breakoutEvents(series(104.0, lastVolume = 1200), p).isEmpty())
        val e = breakoutEvents(series(104.0, lastVolume = 2000), p).single()
        assertEquals("突破布林上軌（量 2.0 倍）", e.description)
    }

    @Test
    fun volumeMovingAverages() {
        val r = TechnicalAnalyzer.analyze(series(100.0, lastVolume = 6000), IndicatorParams())
        assertEquals(2000.0, r.volumeMaShort[25]!!, 1e-9) // (1000×4 + 6000) / 5
        assertEquals(1250.0, r.volumeMaLong[25]!!, 1e-9) // (1000×19 + 6000) / 20
    }

    @Test
    fun validation_allowsFourConfirmations() {
        assertTrue(IndicatorParams(minConfirmations = 4).validate().isEmpty())
        assertTrue(IndicatorParams(useBollingerBreakout = false, minConfirmations = 4).validate().isNotEmpty())
        assertTrue(IndicatorParams(bbBreakoutVolumeRatio = -1.0).validate().isNotEmpty())
    }
}
