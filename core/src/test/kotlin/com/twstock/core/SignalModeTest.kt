package com.twstock.core

import com.twstock.core.model.Candle
import com.twstock.core.model.IndicatorParams
import com.twstock.core.model.SignalIndicator
import com.twstock.core.signal.IndicatorEvent
import com.twstock.core.signal.SignalEngine
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.SignalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SignalModeTest {
    private val start = LocalDate.of(2024, 1, 1)
    private val candles = List(20) { Candle(start.plusDays(it.toLong()), 10.0, 10.0, 10.0, 10.0, 1) }

    private fun ev(i: Int, src: SignalSource, side: SignalSide = SignalSide.BUY) =
        IndicatorEvent(i, start.plusDays(i.toLong()), side, src, src.label)

    private val events = listOf(
        ev(2, SignalSource.KD),
        ev(6, SignalSource.MACD),
        ev(10, SignalSource.BOLLINGER_BREAKOUT),
        ev(11, SignalSource.BOLLINGER),
        ev(15, SignalSource.KD),
        ev(16, SignalSource.MACD),
    )

    private val kdOnly = IndicatorParams(bollingerSignals = false, useMacd = false)
    private val bbOnly = IndicatorParams(useKd = false, useMacd = false)

    @Test
    fun singleIndicator_everyEventIsSignal_evenWithMinConfirmations2() {
        assertEquals(1, kdOnly.requiredConfirmations)
        val s = SignalEngine.confirmSignals(candles, events.filter { it.source == SignalSource.KD }, kdOnly)
        assertEquals(listOf(2, 15), s.map { it.index })
    }

    @Test
    fun bollingerOnly_eachRuleFiresAlone() {
        assertEquals(listOf(SignalIndicator.BOLLINGER), bbOnly.selectedIndicators)
        val bb = events.filter { it.source.name.startsWith("BOLLINGER") }
        // 10 觸發；11 在冷卻期內（同方向 3 日內不重複）
        assertEquals(listOf(10), SignalEngine.confirmSignals(candles, bb, bbOnly).map { it.index })
    }

    @Test
    fun multiSelect_countsIndicators_notTypes() {
        val all = IndicatorParams()
        assertEquals(2, all.requiredConfirmations)
        // 布林突破＋布林收回同屬布林通道，不能互相確認；KD＋MACD → 16
        assertEquals(listOf(16), SignalEngine.confirmSignals(candles, events, all).map { it.index })
        val any = all.copy(minConfirmations = 1)
        assertEquals(listOf(2, 6, 10, 15), SignalEngine.confirmSignals(candles, events, any).map { it.index })
    }

    @Test
    fun requiredConfirmations_clampedToSelectedIndicators() {
        // 只選 KD、MACD 兩個指標
        val kdMacd = IndicatorParams(bollingerSignals = false, minConfirmations = 3)
        assertEquals(2, kdMacd.requiredConfirmations)
        assertTrue(kdMacd.validate().isEmpty())
    }

    @Test
    fun bollingerOnly_bothTypesSameDay_mergedIntoOneSignalWithBothTypes() {
        val sameDay = listOf(ev(10, SignalSource.BOLLINGER_BREAKOUT), ev(10, SignalSource.BOLLINGER))
        val s = SignalEngine.confirmSignals(candles, sameDay, bbOnly).single()
        assertEquals(setOf(SignalSource.BOLLINGER_BREAKOUT, SignalSource.BOLLINGER), s.events.map { it.source }.toSet())
        assertEquals(listOf(SignalIndicator.BOLLINGER), s.groups.map { it.first })
    }

    @Test
    fun summary_groupsTypesByIndicator() {
        val s = SignalEngine.confirmSignals(
            candles,
            listOf(ev(4, SignalSource.MACD_HISTOGRAM), ev(5, SignalSource.KD), ev(5, SignalSource.BOLLINGER_BREAKOUT)),
            IndicatorParams(),
        ).single()
        assertEquals(listOf(SignalIndicator.BOLLINGER, SignalIndicator.KD, SignalIndicator.MACD), s.groups.map { it.first })
        assertEquals("布林通道：布林突破｜KD：KD｜MACD：MACD 柱狀轉折", s.summary)
    }

    @Test
    fun describeSignalMode() {
        assertEquals("只看KD", kdOnly.describeSignalMode())
        assertEquals("只看布林通道", bbOnly.describeSignalMode())
        assertEquals("布林通道＋KD＋MACD：3 日內至少 2 個指標同向", IndicatorParams().describeSignalMode())
        assertEquals("KD＋MACD：任一觸發", IndicatorParams(bollingerSignals = false, minConfirmations = 1).describeSignalMode())
    }

    @Test
    fun toggleIndicator() {
        val off = IndicatorParams().toggleIndicator(SignalIndicator.BOLLINGER, false)
        assertFalse(off.bollingerSignals)
        assertEquals(listOf(SignalIndicator.KD, SignalIndicator.MACD), off.selectedIndicators)
        // 布林規則全關時重新選取，兩個規則一併開啟
        val back = IndicatorParams(useBollinger = false, useBollingerBreakout = false)
            .toggleIndicator(SignalIndicator.BOLLINGER, true)
        assertTrue(back.bollingerPullbackActive && back.bollingerBreakoutActive)
        assertTrue(IndicatorParams(bollingerSignals = false, useKd = false, useMacd = false).validate().isNotEmpty())
    }
}
