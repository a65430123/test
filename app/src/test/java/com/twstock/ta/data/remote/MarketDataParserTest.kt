package com.twstock.ta.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MarketDataParserTest {

    @Test
    fun parseRocDate() {
        assertEquals(LocalDate.of(2024, 10, 1), MarketDataParser.parseRocDate("113/10/01"))
        assertEquals(LocalDate.of(2024, 10, 1), MarketDataParser.parseRocDate("113/10/01＊"))
        assertNull(MarketDataParser.parseRocDate("abc"))
    }

    @Test
    fun parseNumber() {
        assertEquals(1234.5, MarketDataParser.parseNumber("1,234.50")!!, 1e-9)
        assertNull(MarketDataParser.parseNumber("--"))
        assertNull(MarketDataParser.parseNumber(""))
    }

    @Test
    fun parseTwseRows_convertsSharesToLots_andSkipsNoTradeDays() {
        val rows = listOf(
            listOf("113/10/01", "25,123,456", "24,000,000,000", "960.00", "975.00", "955.00", "970.00", "+10.00", "30,000"),
            listOf("113/10/02", "0", "0", "--", "--", "--", "--", " 0.00", "0"),
        )
        val c = MarketDataParser.parseTwseRows(rows)
        assertEquals(1, c.size)
        assertEquals(LocalDate.of(2024, 10, 1), c[0].date)
        assertEquals(970.0, c[0].close, 1e-9)
        assertEquals(25_123L, c[0].volume)
    }

    @Test
    fun parseTpexRows_keepsLots() {
        val rows = listOf(listOf("113/10/01", "1,234", "56,789", "100.5", "102", "99.5", "101", "0.5", "800"))
        val c = MarketDataParser.parseTpexRows(rows)
        assertEquals(1234L, c[0].volume)
        assertEquals(102.0, c[0].high, 1e-9)
    }

    @Test
    fun parseTwseName() {
        assertEquals("台積電", MarketDataParser.parseTwseName("113年10月 2330 台積電           各日成交資訊", "2330"))
        assertNull(MarketDataParser.parseTwseName(null, "2330"))
    }
}
