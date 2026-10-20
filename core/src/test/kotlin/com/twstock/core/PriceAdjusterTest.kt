package com.twstock.core

import com.twstock.core.model.Candle
import com.twstock.core.model.ExRightEvent
import com.twstock.core.model.IndicatorParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PriceAdjusterTest {
    private val eps = 1e-9
    private val start = LocalDate.of(2024, 7, 1)

    private fun candles(vararg closes: Double) = closes.mapIndexed { i, c ->
        Candle(start.plusDays(i.toLong()), c, c + 1, c - 1, c, 1000)
    }

    @Test
    fun cashDividend_scalesPricesBeforeExDate() {
        val cs = candles(100.0, 100.0, 97.0, 98.0)
        // 第 3 天除息 3 元：前收 100、參考價 97
        val adj = PriceAdjuster.adjust(cs, listOf(ExRightEvent(start.plusDays(2), 100.0, 97.0)))
        assertEquals(97.0, adj[0].close, eps)
        assertEquals(101.0 * 0.97, adj[1].high, eps)
        assertEquals(97.0, adj[2].close, eps) // 除息當日起不調整
        assertEquals(98.0, adj[3].close, eps)
        assertEquals(1000L, adj[0].volume)
    }

    @Test
    fun multipleEvents_compound() {
        val cs = candles(100.0, 100.0, 90.0, 90.0, 45.0)
        val events = listOf(
            ExRightEvent(start.plusDays(2), 100.0, 90.0), // 0.9
            ExRightEvent(start.plusDays(4), 90.0, 45.0), // 0.5（配股 1:1）
        )
        val adj = PriceAdjuster.adjust(cs, events)
        assertEquals(100.0 * 0.9 * 0.5, adj[0].close, eps)
        assertEquals(90.0 * 0.5, adj[3].close, eps)
        assertEquals(45.0, adj[4].close, eps)
    }

    @Test
    fun futureAndInvalidEvents_ignored() {
        val cs = candles(100.0, 100.0)
        val events = listOf(
            ExRightEvent(start.plusDays(10), 100.0, 95.0), // 尚未發生
            ExRightEvent(start.plusDays(1), 0.0, 95.0), // 前收為 0
            ExRightEvent(start.plusDays(1), 100.0, 5.0), // 因子異常
        )
        assertSame(cs, PriceAdjuster.adjust(cs, events))
    }

    @Test
    fun adjustment_removesFalseBollingerBreakdownOnExDividend() {
        // 盤整後除息 8%：未還原時收盤直接跌破布林下軌，還原後應無此現象
        val closes = List(30) { 100.0 + if (it % 2 == 0) 0.5 else -0.5 } + List(5) { 92.0 }
        val cs = closes.mapIndexed { i, c -> Candle(start.plusDays(i.toLong()), c, c + 0.3, c - 0.3, c, 1) }
        val exDate = start.plusDays(30)
        val params = IndicatorParams(minConfirmations = 1)

        val raw = TechnicalAnalyzer.analyze(cs, params)
        assertTrue(raw.bollinger.lower[30]!! > 92.0)

        val adjusted = TechnicalAnalyzer.analyze(
            PriceAdjuster.adjust(cs, listOf(ExRightEvent(exDate, 100.5, 92.0))),
            params,
        )
        assertTrue(adjusted.bollinger.lower[30]!! < 92.0)
    }
}
