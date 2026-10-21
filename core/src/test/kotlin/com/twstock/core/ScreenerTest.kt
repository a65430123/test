package com.twstock.core

import com.twstock.core.model.Candle
import com.twstock.core.model.ExRightEvent
import com.twstock.core.model.IndicatorParams
import com.twstock.core.signal.SignalSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScreenerTest {
    private val start = LocalDate.of(2024, 1, 1)

    private fun series(closes: List<Double>, volume: Long = 1000, offset: Long = 0) = closes.mapIndexed { i, c ->
        Candle(start.plusDays(i + offset), c, c + 1, c - 1, c, volume)
    }

    // 盤整後急跌再反彈：最後一天出現買進訊號（同 SignalEngineTest 的 V 型反彈）
    private val rebound = List(30) { 100.0 + if (it % 2 == 0) 1 else -1 } +
        listOf(97.0, 93.0, 89.0, 85.0, 82.0, 80.0, 79.0, 86.0)
    private val flat = List(rebound.size) { 100.0 }
    private val latest = start.plusDays(rebound.size - 1L)

    @Test
    fun findsStocksWithSignalOnLatestDay_sortedByVolume() {
        val hits = Screener.scan(
            mapOf("A" to series(rebound, 500), "B" to series(flat), "C" to series(rebound, 2000)),
            IndicatorParams(),
            latest,
        )
        assertEquals(listOf("C", "A"), hits.map { it.code })
        assertTrue(hits.all { it.signal.side == SignalSide.BUY })
        assertEquals(86.0, hits[0].close, 1e-9)
        assertEquals(79.0, hits[0].prevClose!!, 1e-9)
        assertEquals(2000L, hits[0].volume)
    }

    @Test
    fun skipsStaleOrShortSeries() {
        val stale = series(rebound, offset = -1) // 最後一天早於 latestDate
        val short = series(rebound.takeLast(20), offset = rebound.size - 20L)
        assertTrue(Screener.scan(mapOf("S" to stale, "T" to short), IndicatorParams(), latest).isEmpty())
    }

    @Test
    fun adjustedPricesUsedForSignals_rawPricesReported() {
        // 盤整中除息 8%：未還原時最後一天跌破布林下軌（布林突破買進），還原後沒有
        val closes = List(40) { 100.0 + if (it % 2 == 0) 0.5 else -0.5 } + 92.0
        val c = series(closes)
        val last = start.plusDays(closes.size - 1L)
        val p = IndicatorParams(useKd = false, useMacd = false)
        val ex = mapOf("X" to listOf(ExRightEvent(last, 99.5, 92.0)))
        assertEquals(1, Screener.scan(mapOf("X" to c), p, last).size)
        assertTrue(Screener.scan(mapOf("X" to c), p, last, ex, adjust = true).isEmpty())
    }
}
