package com.twstock.ta.data.remote

import com.twstock.core.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun parseRocDate_chineseFormat() {
        assertEquals(LocalDate.of(2024, 7, 18), MarketDataParser.parseRocDate("113年07月18日"))
    }

    @Test
    fun parseExRights_twseLayout() {
        val fields = listOf("資料日期", "股票代號", "股票名稱", "除權息前收盤價", "除權息參考價", "權值+息值", "權/息", "漲停價格", "跌停價格", "開盤競價基準", "減除股利參考價")
        val rows = listOf(
            listOf("113年06月13日", "2330", "台積電", "914.00", "910.50", "3.50", "息", "1,001.00", "819.50", "910.50", "910.50"),
            listOf("113年06月13日", "9999", "壞資料", "--", "--", "", "息", "", "", "", ""),
        )
        val r = MarketDataParser.parseExRights(fields, rows)
        assertEquals(1, r.size)
        assertEquals("2330", r[0].code)
        assertEquals(LocalDate.of(2024, 6, 13), r[0].event.exDate)
        assertEquals(914.0, r[0].event.closeBefore, 1e-9)
        assertEquals(910.5, r[0].event.referencePrice, 1e-9)
    }

    @Test
    fun parseExRights_tpexLayout_locatesColumnsByName() {
        val fields = listOf("除權息日期", "代號", "名稱", "除權息前收盤價", "除權息參考價", "權值", "息值", "權值+息值", "權/息", "減除股利參考價")
        val rows = listOf(listOf("113/07/18", "6488", "環球晶", "500.00", "487.50", "0", "12.5", "12.5", "息", "487.50"))
        val r = MarketDataParser.parseExRights(fields, rows)
        assertEquals("6488", r[0].code)
        assertEquals(487.5, r[0].event.referencePrice, 1e-9)
    }

    @Test
    fun parseDailyQuotes_twseLayout() {
        val fields = listOf("證券代號", "證券名稱", "成交股數", "成交筆數", "成交金額", "開盤價", "最高價", "最低價", "收盤價",
            "漲跌(+/-)", "漲跌價差", "最後揭示買價", "最後揭示買量", "最後揭示賣價", "最後揭示賣量", "本益比")
        val rows = listOf(
            listOf("2330", "台積電", "25,123,456", "30,000", "24,000,000,000", "960.00", "975.00", "955.00", "970.00",
                "<p style= color:red>+</p>", "10.00", "969.00", "100", "970.00", "50", "25.1"),
            listOf("1101", "台泥", "0", "0", "0", "--", "--", "--", "--", "", "0.00", "", "", "", "", ""),
        )
        val q = MarketDataParser.parseDailyQuotes(fields, rows, LocalDate.of(2024, 10, 1)).single()
        assertEquals("2330", q.code)
        assertEquals("台積電", q.name)
        assertEquals(Candle(LocalDate.of(2024, 10, 1), 960.0, 975.0, 955.0, 970.0, 25_123), q.candle)
    }

    @Test
    fun parseDailyQuotes_tpexLayout() {
        val fields = listOf("代號", "名稱", "收盤", "漲跌", "開盤", "最高", "最低", "均價", "成交股數", "成交金額(元)", "成交筆數")
        val rows = listOf(listOf("6488", "環球晶", "500.00", "+5.00", "495.00", "505.00", "490.00", "498.2", "1,234,000", "615,000,000", "900"))
        val q = MarketDataParser.parseDailyQuotes(fields, rows, LocalDate.of(2024, 10, 1)).single()
        assertEquals(Candle(LocalDate.of(2024, 10, 1), 495.0, 505.0, 490.0, 500.0, 1_234), q.candle)
    }

    @Test
    fun pickTable_andStockCodes() {
        val index = DataTable("價格指數", listOf("指數", "收盤指數"), listOf(listOf("發行量加權股價指數", "20,000")))
        val quotes = DataTable("每日收盤行情", listOf("證券代號", "收盤價"), listOf(listOf("2330", "970")))
        assertEquals(quotes, MarketDataParser.pickTable(listOf(index, quotes), "代號", "收盤"))
        assertTrue(MarketDataParser.isStock("2330"))
        assertTrue(!MarketDataParser.isStock("0050") && MarketDataParser.isEtf("0050") && MarketDataParser.isEtf("00878"))
        assertTrue(!MarketDataParser.isStock("030001") && !MarketDataParser.isEtf("2330"))
        listOf("0056", "006208", "00631L", "00632R", "00679B").forEach { assertTrue(it, MarketDataParser.isEtf(it)) }
        listOf("030001", "00", "0050AB", "00ABC").forEach { assertTrue(it, !MarketDataParser.isEtf(it)) }
    }

    @Test
    fun parseTwseName() {
        assertEquals("台積電", MarketDataParser.parseTwseName("113年10月 2330 台積電           各日成交資訊", "2330"))
        assertNull(MarketDataParser.parseTwseName(null, "2330"))
    }
}
