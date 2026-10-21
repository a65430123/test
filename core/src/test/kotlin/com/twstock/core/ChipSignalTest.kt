package com.twstock.core

import com.twstock.core.model.Candle
import com.twstock.core.model.ChipDay
import com.twstock.core.model.ChipParty
import com.twstock.core.model.IndicatorParams
import com.twstock.core.model.SignalIndicator
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.SignalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChipSignalTest {
    private val start = LocalDate.of(2024, 5, 1)
    private val candles = List(12) { Candle(start.plusDays(it.toLong()), 10.0, 10.0, 10.0, 10.0, 1) }

    /** totals[i] 為第 i 日三大法人合計；外資固定為合計的一半。null 表示當日無資料。 */
    private fun chips(vararg totals: Long?) = totals.mapIndexedNotNull { i, t ->
        t?.let { ChipDay(start.plusDays(i.toLong()), foreignNet = it / 2, trustNet = 0, dealerNet = it - it / 2, totalNet = it) }
    }

    private val chipsOnly = IndicatorParams(bollingerSignals = false, useKd = false, useMacd = false, useChips = true)

    private fun chipEvents(chips: List<ChipDay>, p: IndicatorParams = chipsOnly) =
        TechnicalAnalyzer.analyze(candles, p, chips).events.filter { it.source == SignalSource.CHIPS }

    @Test
    fun buyStreak_firesOnceWhenReachingN() {
        val e = chipEvents(chips(100, 200, 300, 400, 500))
        assertEquals(listOf(2), e.map { it.index })
        assertEquals(SignalSide.BUY to "三大法人連續買超 3 日", e.single().side to e.single().description)
    }

    @Test
    fun sellStreak_andReset() {
        // 賣、賣、買（中斷）、賣、賣、賣
        val e = chipEvents(chips(-10, -10, 5, -10, -10, -10))
        assertEquals(listOf(5), e.map { it.index })
        assertEquals("三大法人連續賣超 3 日", e.single().description)
    }

    @Test
    fun missingDayBreaksStreak() {
        val e = chipEvents(chips(10, 10, null, 10, 10, 10))
        assertEquals(listOf(5), e.map { it.index })
    }

    @Test
    fun minNetThreshold_andParty() {
        assertTrue(chipEvents(chips(100, 100, 100), chipsOnly.copy(chipMinNet = 100)).isEmpty())
        assertEquals(1, chipEvents(chips(101, 101, 101), chipsOnly.copy(chipMinNet = 100)).size)
        // 外資＝合計一半：50 > 40 成立，連 2 日
        val foreign = chipsOnly.copy(chipParty = ChipParty.FOREIGN, chipStreakDays = 2, chipMinNet = 40)
        assertEquals("外資連續買超 2 日", chipEvents(chips(100, 100), foreign).single().description)
    }

    @Test
    fun onlyChipsSelected_eachStreakIsASignal_andAligned() {
        val r = TechnicalAnalyzer.analyze(candles, chipsOnly, chips(1, 1, 1))
        assertEquals(listOf(SignalSide.BUY), r.signals.map { it.side })
        assertEquals(100L, TechnicalAnalyzer.analyze(candles, chipsOnly, chips(100)).chips[0]?.totalNet)
        assertNull(r.chips[5])
    }

    @Test
    fun selectionAndValidation() {
        assertEquals("只看籌碼", chipsOnly.describeSignalMode())
        // 預設不納入籌碼，舊有的訊號來源選擇不受影響
        assertTrue(SignalIndicator.CHIPS !in IndicatorParams().selectedIndicators)
        val withChips = IndicatorParams().toggleIndicator(SignalIndicator.CHIPS, true)
        assertEquals(6, withChips.enabledRuleCount)
        assertEquals("布林通道＋KD＋MACD＋籌碼：3 日內至少 2 個同向", withChips.describeSignalMode())
        assertTrue(IndicatorParams(chipStreakDays = 0).validate().isNotEmpty())
        assertTrue(IndicatorParams(minConfirmations = 6).validate().isEmpty())
        assertTrue(IndicatorParams(chipMinNet = -1).validate().isNotEmpty())
    }
}
