package com.twstock.core

import com.twstock.core.indicator.BollingerBands
import com.twstock.core.indicator.KdResult
import com.twstock.core.indicator.MacdResult
import com.twstock.core.model.Candle
import com.twstock.core.model.IndicatorParams
import com.twstock.core.model.SignalIndicator
import com.twstock.core.signal.SignalEngine
import com.twstock.core.signal.SignalSide
import com.twstock.core.signal.SignalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KdMacdRulesTest {
    private val start = LocalDate.of(2024, 1, 1)
    private fun candles(n: Int) = List(n) { Candle(start.plusDays(it.toLong()), 10.0, 10.0, 10.0, 10.0, 1) }
    private fun noBands(n: Int) = BollingerBands(List(n) { null }, List(n) { null }, List(n) { null })
    private fun noKd(n: Int) = KdResult(List(n) { null }, List(n) { null }, List(n) { null })
    private fun noMacd(n: Int) = MacdResult(List(n) { null }, List(n) { null }, List(n) { null })

    private fun kdEvents(k: List<Double>, d: List<Double>, p: IndicatorParams = IndicatorParams()) =
        SignalEngine.detectEvents(candles(k.size), noBands(k.size), KdResult(k.map { null }, k, d), noMacd(k.size), p)

    private fun histEvents(h: List<Double?>, p: IndicatorParams = IndicatorParams()) =
        SignalEngine.detectEvents(candles(h.size), noBands(h.size), noKd(h.size), MacdResult(h, h, h), p)
            .filter { it.source == SignalSource.MACD_HISTOGRAM }

    @Test
    fun kd_midZoneCrosses() {
        // 中檔黃金交叉（前日 K=45）、中檔死亡交叉（前日 K=60）
        val e = kdEvents(k = listOf(45.0, 55.0, 60.0, 50.0), d = listOf(50.0, 50.0, 55.0, 55.0))
        assertEquals(listOf(SignalSide.BUY to "KD 黃金交叉 (K=55.0)", SignalSide.SELL to "KD 死亡交叉 (K=50.0)"),
            e.map { it.side to it.description })
        assertTrue(e.all { it.source == SignalSource.KD })
    }

    @Test
    fun kd_zoneCrossesUnchanged_andWrongZoneCrossesIgnored() {
        assertEquals(listOf("KD 低檔黃金交叉 (K=25.0)"), kdEvents(listOf(15.0, 25.0), listOf(20.0, 20.0)).map { it.description })
        assertEquals(listOf("KD 高檔死亡交叉 (K=82.0)"), kdEvents(listOf(90.0, 82.0), listOf(85.0, 86.0)).map { it.description })
        // 高檔黃金交叉、低檔死亡交叉不屬於任何規則
        assertTrue(kdEvents(listOf(85.0, 90.0), listOf(88.0, 88.0)).isEmpty())
        assertTrue(kdEvents(listOf(18.0, 12.0), listOf(16.0, 15.0)).isEmpty())
    }

    @Test
    fun kd_midCrossCanBeDisabled() {
        val e = kdEvents(listOf(45.0, 55.0), listOf(50.0, 50.0), IndicatorParams(kdMidCross = false))
        assertTrue(e.isEmpty())
    }

    @Test
    fun macdHistogram_firstBarDownFromRunHigh_isSell() {
        //            翻正  上升  最高  第一根往下  續降  反彈  再降（未創新高，不觸發）
        val h = listOf(null, 0.5, 1.2, 2.0, 1.6, 1.1, 1.4, 1.0)
        val e = histEvents(h)
        assertEquals(listOf(4), e.map { it.index })
        assertEquals("MACD 柱狀高點轉折向下", e.single().description)
        assertEquals(SignalSide.SELL, e.single().side)
    }

    @Test
    fun macdHistogram_turnAcrossZero_alsoCounts() {
        // 本段負柱狀只有一根（即最低點），下一根往上即使翻正也算低點轉折
        val e = histEvents(listOf(null, -1.0, 0.5))
        assertEquals(listOf(SignalSide.BUY), e.map { it.side })
    }

    @Test
    fun macdHistogram_newHighInSameRun_triggersAgain() {
        val h = listOf(0.5, 2.0, 1.5, 2.5, 2.2)
        assertEquals(listOf(2, 4), histEvents(h).map { it.index })
    }

    @Test
    fun macdHistogram_firstBarUpFromRunLow_isBuy() {
        val h = listOf(null, -0.4, -1.5, -2.1, -1.7, -1.9, -1.2)
        val e = histEvents(h)
        assertEquals(listOf(4), e.map { it.index })
        assertEquals(SignalSide.BUY to "MACD 柱狀低點轉折向上", e.single().side to e.single().description)
    }

    @Test
    fun macdRules_selectionAndCounting() {
        val p = IndicatorParams()
        assertEquals(5, p.enabledRuleCount)
        val crossOnly = p.copy(macdHistogramTurn = false)
        assertTrue(histEvents(listOf(1.0, 2.0, 1.0), crossOnly).isEmpty())
        val macdOff = p.copy(macdCross = false, macdHistogramTurn = false)
        assertEquals(listOf(SignalIndicator.BOLLINGER, SignalIndicator.KD), macdOff.selectedIndicators)
        val back = macdOff.toggleIndicator(SignalIndicator.MACD, true)
        assertTrue(back.macdCrossActive && back.macdHistogramActive)
        assertTrue(IndicatorParams(minConfirmations = 5).validate().isEmpty())
        assertTrue(IndicatorParams(minConfirmations = 6).validate().isNotEmpty())
    }
}
