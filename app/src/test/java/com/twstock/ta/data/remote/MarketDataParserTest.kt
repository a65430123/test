package com.twstock.ta.data.remote

import com.google.gson.JsonObject
import com.twstock.core.model.BrokerFlow
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
    fun parseInstitutional_twseLayout() {
        val fields = listOf(
            "證券代號", "證券名稱", "外陸資買進股數(不含外資自營商)", "外陸資賣出股數(不含外資自營商)",
            "外陸資買賣超股數(不含外資自營商)", "外資自營商買進股數", "外資自營商賣出股數", "外資自營商買賣超股數",
            "投信買進股數", "投信賣出股數", "投信買賣超股數", "自營商買賣超股數", "自營商買進股數(自行買賣)",
            "自營商賣出股數(自行買賣)", "自營商買賣超股數(自行買賣)", "自營商買進股數(避險)", "自營商賣出股數(避險)",
            "自營商買賣超股數(避險)", "三大法人買賣超股數",
        )
        val row = listOf(
            "2330  ", "台積電", "30,000,000", "20,000,000", "10,000,000", "0", "0", "0",
            "1,000,000", "3,500,000", "-2,500,000", "400,600", "500,000", "100,000", "400,000", "1,000", "400", "600",
            "7,900,600",
        )
        val r = MarketDataParser.parseInstitutional(fields, listOf(row)).single()
        assertEquals(InstitutionalRecord("2330", 10_000, -2_500, 401, 7_901), r)
    }

    @Test
    fun parseInstitutional_tpexLayout() {
        val fields = listOf(
            "代號", "名稱", "外資及陸資(不含外資自營商)-買進股數", "外資及陸資(不含外資自營商)-賣出股數",
            "外資及陸資(不含外資自營商)-買賣超股數", "外資自營商-買賣超股數", "投信-買賣超股數",
            "自營商(自行買賣)-買賣超股數", "自營商(避險)-買賣超股數", "自營商-買賣超股數", "三大法人買賣超股數合計",
        )
        val row = listOf("6488", "環球晶", "5,000", "2,000", "3,000", "0", "-1,000", "500", "-200", "300", "2,300")
        assertEquals(InstitutionalRecord("6488", 3, -1, 0, 2), MarketDataParser.parseInstitutional(fields, listOf(row)).single())
    }

    @Test
    fun parseInstitutional_missingColumns_returnsEmpty() {
        assertEquals(emptyList<InstitutionalRecord>(), MarketDataParser.parseInstitutional(listOf("代號", "名稱"), listOf(listOf("1", "x"))))
    }

    @Test
    fun parseMargin_twseLayout() {
        val fields = listOf("代號", "名稱", "買進", "賣出", "現金償還", "前日餘額", "今日餘額", "次一營業日限額",
            "買進", "賣出", "現券償還", "前日餘額", "今日餘額", "次一營業日限額", "資券互抵", "註記")
        val row = listOf("2330", "台積電", "1,200", "800", "10", "20,000", "20,390", "0", "50", "70", "0", "300", "320", "0", "5", "")
        assertEquals(MarginRecord("2330", 20_390, 390, 320, 20), MarketDataParser.parseMargin(fields, listOf(row)).single())
    }

    @Test
    fun parseMargin_tpexLayout() {
        val fields = listOf("代號", "名稱", "前資餘額(張)", "資買", "資賣", "現償", "資餘額", "資屬證金", "資使用率(%)", "資限額",
            "前券餘額(張)", "券賣", "券買", "券償", "券餘額")
        val row = listOf("6488", "環球晶", "1,000", "50", "80", "0", "970", "0", "1.2", "0", "40", "5", "0", "0", "45")
        assertEquals(MarginRecord("6488", 970, -30, 45, 5), MarketDataParser.parseMargin(fields, listOf(row)).single())
    }

    @Test
    fun pickTable_findsMarginDetail() {
        val summary = DataTable("信用交易統計", listOf("項目", "買進"), listOf(listOf("融資(交易單位)", "1")))
        val detail = DataTable("融資融券彙總", listOf("代號", "名稱", "今日餘額"), listOf(listOf("2330", "台積電", "1")))
        assertEquals(detail, MarketDataParser.pickTable(listOf(summary, detail), "代號", "餘額"))
    }

    @Test
    fun parseBrokerFlows_aggregatesPriceLevels() {
        fun row(id: String, name: String, buy: Int, sell: Int) = JsonObject().apply {
            addProperty("securities_trader_id", id); addProperty("securities_trader", name)
            addProperty("price", 100.0); addProperty("buy", buy); addProperty("sell", sell)
        }
        val flows = MarketDataParser.parseBrokerFlows(
            listOf(row("9200", "凱基", 3000, 0), row("9200", "凱基", 2000, 1000), row("1440", "美林", 0, 8000)),
        )
        assertEquals(listOf(BrokerFlow("9200", "凱基", 5, 1), BrokerFlow("1440", "美林", 0, 8)), flows)
        assertEquals(4L, flows[0].net)
    }

    @Test
    fun parseTwseName() {
        assertEquals("台積電", MarketDataParser.parseTwseName("113年10月 2330 台積電           各日成交資訊", "2330"))
        assertNull(MarketDataParser.parseTwseName(null, "2330"))
    }
}
