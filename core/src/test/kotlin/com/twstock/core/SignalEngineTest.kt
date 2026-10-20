package com.twstock.core

import com.twstock.core.model.Candle
import com.twstock.core.model.IndicatorParams
import com.twstock.core.signal.IndicatorEvent
import com.twstock.core.signal.SignalEngine
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.SignalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SignalEngineTest {
    private val start = LocalDate.of(2024, 1, 1)
    private val candles = List(20) { Candle(start.plusDays(it.toLong()), 10.0, 10.0, 10.0, 10.0, 1) }

    private fun ev(i: Int, side: SignalSide, src: SignalSource) =
        IndicatorEvent(i, start.plusDays(i.toLong()), side, src, src.label)

    @Test
    fun requiresTwoDistinctIndicatorsWithinWindow() {
        val events = listOf(
            ev(5, SignalSide.BUY, SignalSource.KD),
            ev(7, SignalSide.BUY, SignalSource.BOLLINGER), // within 3-day window of day 5
            ev(12, SignalSide.BUY, SignalSource.MACD), // alone
        )
        val s = SignalEngine.confirmSignals(candles, events, IndicatorParams())
        assertEquals(1, s.size)
        assertEquals(7, s[0].index)
        assertEquals(setOf(SignalSource.KD, SignalSource.BOLLINGER), s[0].events.map { it.source }.toSet())
    }

    @Test
    fun outsideWindow_doesNotConfirm() {
        val events = listOf(
            ev(5, SignalSide.BUY, SignalSource.KD),
            ev(8, SignalSide.BUY, SignalSource.BOLLINGER), // 4 days apart, window 3
        )
        assertTrue(SignalEngine.confirmSignals(candles, events, IndicatorParams()).isEmpty())
    }

    @Test
    fun sameSourceTwice_doesNotConfirm() {
        val events = listOf(
            ev(5, SignalSide.SELL, SignalSource.MACD),
            ev(6, SignalSide.SELL, SignalSource.MACD),
        )
        assertTrue(SignalEngine.confirmSignals(candles, events, IndicatorParams()).isEmpty())
    }

    @Test
    fun cooldownSuppressesDuplicateSameSideSignals() {
        val events = listOf(
            ev(5, SignalSide.BUY, SignalSource.KD),
            ev(6, SignalSide.BUY, SignalSource.BOLLINGER), // signal here
            ev(7, SignalSide.BUY, SignalSource.MACD), // would re-confirm; suppressed
        )
        val s = SignalEngine.confirmSignals(candles, events, IndicatorParams())
        assertEquals(listOf(6), s.map { it.index })
    }

    @Test
    fun singleConfirmation_firesOnEachEvent() {
        val events = listOf(
            ev(3, SignalSide.BUY, SignalSource.MACD),
            ev(10, SignalSide.SELL, SignalSource.KD),
        )
        val s = SignalEngine.confirmSignals(candles, events, IndicatorParams(minConfirmations = 1))
        assertEquals(listOf(SignalSide.BUY, SignalSide.SELL), s.map { it.side })
    }

    @Test
    fun endToEnd_vShapedReboundProducesBuy() {
        // 先盤整、急跌跌破下軌，再反彈：預期 KD 低檔黃金交叉 + 布林收回下軌 產生買訊
        val closes = List(30) { 100.0 + if (it % 2 == 0) 1 else -1 } +
            listOf(97.0, 93.0, 89.0, 85.0, 82.0, 80.0, 79.0, 86.0, 90.0, 93.0)
        val cs = closes.mapIndexed { i, c ->
            Candle(start.plusDays(i.toLong()), c, c + 1, c - 1, c, 1000)
        }
        val result = TechnicalAnalyzer.analyze(cs, IndicatorParams())
        val buys = result.signals.filter { it.side == SignalSide.BUY }
        assertTrue("expected a buy signal, events=${result.events}", buys.isNotEmpty())
        assertTrue(buys.first().events.any { it.source == SignalSource.KD })
    }

    @Test
    fun params_validation() {
        assertTrue(IndicatorParams().validate().isEmpty())
        assertTrue(IndicatorParams(macdFast = 26, macdSlow = 12).validate().isNotEmpty())
        assertTrue(IndicatorParams(useKd = false, useMacd = false, minConfirmations = 2).validate().isNotEmpty())
    }
}
