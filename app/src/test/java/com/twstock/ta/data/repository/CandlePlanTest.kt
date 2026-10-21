package com.twstock.ta.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class CandlePlanTest {
    private val now = YearMonth.of(2026, 10)
    private val start = now.minusMonths(11) // 2025-11

    @Test
    fun firstDownload_allMonthsNewestFirst() {
        val months = CandlePlan.forward(now, latest = null, targetStart = start)
        assertEquals(12, months.size)
        assertEquals(now, months.first())
        assertEquals(start, months.last())
    }

    @Test
    fun incrementalUpdate_onlyFromLatestMonth() {
        assertEquals(listOf(now), CandlePlan.forward(now, latest = now, targetStart = start))
        assertEquals(listOf(now, now.minusMonths(1)), CandlePlan.forward(now, latest = now.minusMonths(1), targetStart = start))
    }

    @Test
    fun backfill_afterSeedOrInterruptedDownload() {
        // 選股資料帶入 2026-07 起的資料：補 2026-06 往回到 2025-11
        val months = CandlePlan.backfill(earliest = YearMonth.of(2026, 7), targetStart = start)
        assertEquals(YearMonth.of(2026, 6), months.first())
        assertEquals(start, months.last())
        assertEquals(8, months.size)
        // 已有完整歷史：不需補
        assertEquals(emptyList<YearMonth>(), CandlePlan.backfill(earliest = start, targetStart = start))
        assertEquals(emptyList<YearMonth>(), CandlePlan.backfill(earliest = null, targetStart = start))
    }
}
