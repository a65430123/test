package com.twstock.ta.data.remote

import com.google.gson.JsonObject
import com.twstock.core.model.BrokerFlow
import com.twstock.core.model.Candle
import com.twstock.core.model.ExRightEvent
import java.time.LocalDate

/** 除權除息計算結果表中的一筆資料。 */
data class ExRightRecord(val code: String, val event: ExRightEvent)

/** 三大法人買賣超（單位：張）。 */
data class InstitutionalRecord(
    val code: String,
    val foreignNet: Long,
    val trustNet: Long,
    val dealerNet: Long,
    val totalNet: Long,
)

/** 融資融券餘額與增減（單位：張）。 */
data class MarginRecord(
    val code: String,
    val marginBalance: Long?,
    val marginChange: Long?,
    val shortBalance: Long?,
    val shortChange: Long?,
)

/** 將證交所/櫃買中心的字串表格轉為 [Candle]。 */
object MarketDataParser {

    /** 民國日期 "113/10/01"、"113年10月01日"（可能夾雜 ＊ 等註記符號）→ LocalDate */
    fun parseRocDate(text: String): LocalDate? {
        val parts = DIGITS.findAll(text).map { it.value.toInt() }.toList()
        if (parts.size != 3) return null
        return runCatching { LocalDate.of(parts[0] + 1911, parts[1], parts[2]) }.getOrNull()
    }

    private val DIGITS = Regex("\\d+")

    /** "1,234.50" → 1234.5；"--"、"---"、"X0.00" 等無法解析者 → null */
    fun parseNumber(text: String): Double? {
        val cleaned = text.replace(",", "").trim()
        if (cleaned.isEmpty() || cleaned.all { it == '-' }) return null
        return cleaned.toDoubleOrNull()
    }

    /** 證交所成交股數為「股」，換算為「張」。 */
    fun parseTwseRows(rows: List<List<String>>?): List<Candle> =
        rows.orEmpty().mapNotNull { row -> parseRow(row, volumeDivisor = 1000.0) }

    /** 櫃買中心成交量已為「張」。 */
    fun parseTpexRows(rows: List<List<String>>?): List<Candle> =
        rows.orEmpty().mapNotNull { row -> parseRow(row, volumeDivisor = 1.0) }

    private fun parseRow(row: List<String>, volumeDivisor: Double): Candle? {
        if (row.size < 7) return null
        val date = parseRocDate(row[0]) ?: return null
        val volume = parseNumber(row[1]) ?: 0.0
        val open = parseNumber(row[3]) ?: return null
        val high = parseNumber(row[4]) ?: return null
        val low = parseNumber(row[5]) ?: return null
        val close = parseNumber(row[6]) ?: return null
        return Candle(date, open, high, low, close, (volume / volumeDivisor).toLong())
    }

    /**
     * 除權除息計算結果表（證交所 TWT49U / 櫃買中心 exDailyQ）。
     * 以欄位名稱定位「日期、代號、除權息前收盤價、除權息參考價」，找不到時退回證交所的固定欄位順序。
     */
    fun parseExRights(fields: List<String>?, rows: List<List<String>>?): List<ExRightRecord> {
        val names = fields.orEmpty().map { it.filterNot(Char::isWhitespace) }
        fun col(fallback: Int, match: (String) -> Boolean) =
            names.indexOfFirst(match).takeIf { it >= 0 } ?: fallback
        val dateIdx = col(0) { "日期" in it }
        val codeIdx = col(1) { "代號" in it }
        val beforeIdx = col(3) { "前收盤" in it }
        val refIdx = col(4) { "參考價" in it && "減除" !in it }
        val needed = maxOf(dateIdx, codeIdx, beforeIdx, refIdx)
        return rows.orEmpty().mapNotNull { row ->
            if (row.size <= needed) return@mapNotNull null
            val date = parseRocDate(row[dateIdx]) ?: return@mapNotNull null
            val code = row[codeIdx].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val before = parseNumber(row[beforeIdx]) ?: return@mapNotNull null
            val ref = parseNumber(row[refIdx]) ?: return@mapNotNull null
            ExRightRecord(code, ExRightEvent(date, before, ref))
        }
    }

    private fun normalize(fields: List<String>?) = fields.orEmpty().map { it.filterNot(Char::isWhitespace) }

    private fun sharesToLots(shares: Double): Long = Math.round(shares / 1000.0)

    /**
     * 三大法人買賣超（證交所 T86 / 櫃買中心 dailyTrade，單位：股 → 張）。
     * 外資取「不含外資自營商」的外陸資買賣超；自營商取合計（不含外資自營商）。
     * 找不到必要欄位時回傳空清單。
     */
    fun parseInstitutional(fields: List<String>?, rows: List<List<String>>?): List<InstitutionalRecord> {
        val names = normalize(fields)
        val code = names.indexOfFirst { "代號" in it }
        val foreign = names.indexOfFirst { "外" in it && "買賣超" in it }
        val trust = names.indexOfFirst { "投信" in it && "買賣超" in it }
        val dealer = names.indexOfFirst {
            "自營商" in it && "買賣超" in it && "外資" !in it && "自行" !in it && "避險" !in it
        }
        val total = names.indexOfFirst { "三大法人" in it }
        val cols = listOf(code, foreign, trust, dealer, total)
        if (cols.any { it < 0 }) return emptyList()
        return rows.orEmpty().mapNotNull { row ->
            if (row.size <= cols.max()) return@mapNotNull null
            val c = row[code].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            InstitutionalRecord(
                code = c,
                foreignNet = sharesToLots(parseNumber(row[foreign]) ?: return@mapNotNull null),
                trustNet = sharesToLots(parseNumber(row[trust]) ?: return@mapNotNull null),
                dealerNet = sharesToLots(parseNumber(row[dealer]) ?: return@mapNotNull null),
                totalNet = sharesToLots(parseNumber(row[total]) ?: return@mapNotNull null),
            )
        }
    }

    /**
     * 融資融券餘額（單位：張）。
     * 證交所欄位：前日餘額／今日餘額各出現兩次（先融資、後融券）；
     * 櫃買中心欄位：前資餘額、資餘額、前券餘額、券餘額。
     */
    fun parseMargin(fields: List<String>?, rows: List<List<String>>?): List<MarginRecord> {
        val names = normalize(fields)
        val code = names.indexOfFirst { "代號" in it }
        val today = names.indices.filter { "今日餘額" in names[it] }
        val prev = names.indices.filter { "前日餘額" in names[it] }
        val marginBal = today.getOrNull(0) ?: names.indexOfFirst { it.startsWith("資餘額") }
        val shortBal = today.getOrNull(1) ?: names.indexOfFirst { it.startsWith("券餘額") }
        val marginPrev = prev.getOrNull(0) ?: names.indexOfFirst { it.startsWith("前資餘額") }
        val shortPrev = prev.getOrNull(1) ?: names.indexOfFirst { it.startsWith("前券餘額") }
        if (code < 0 || marginBal < 0 || shortBal < 0) return emptyList()
        fun at(row: List<String>, i: Int) = if (i in row.indices) parseNumber(row[i])?.toLong() else null
        return rows.orEmpty().mapNotNull { row ->
            val c = row.getOrNull(code)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val mb = at(row, marginBal)
            val sb = at(row, shortBal)
            if (mb == null && sb == null) return@mapNotNull null
            val mp = at(row, marginPrev)
            val sp = at(row, shortPrev)
            MarginRecord(
                code = c,
                marginBalance = mb,
                marginChange = if (mb != null && mp != null) mb - mp else null,
                shortBalance = sb,
                shortChange = if (sb != null && sp != null) sb - sp else null,
            )
        }
    }

    /** 在多表格回應中找出含有指定欄位的表格（例如融資融券明細）。 */
    fun pickTable(tables: List<DataTable>?, vararg mustContain: String): DataTable? =
        tables.orEmpty().firstOrNull { t ->
            val names = normalize(t.fields)
            mustContain.all { key -> names.any { key in it } } && !t.data.isNullOrEmpty()
        }

    /**
     * FinMind 分點券商日報：每家券商每個價位一列（單位：股），彙總為每家券商買賣張數。
     * 欄位：securities_trader、securities_trader_id、price、buy、sell。
     */
    fun parseBrokerFlows(rows: List<JsonObject>?): List<BrokerFlow> {
        class Acc(val name: String) { var buy = 0.0; var sell = 0.0 }
        val acc = LinkedHashMap<String, Acc>()
        for (r in rows.orEmpty()) {
            val id = r.get("securities_trader_id")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            val name = r.get("securities_trader")?.takeIf { it.isJsonPrimitive }?.asString ?: id
            val a = acc.getOrPut(id) { Acc(name) }
            a.buy += r.get("buy")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0
            a.sell += r.get("sell")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0
        }
        return acc.map { (id, a) -> BrokerFlow(id, a.name, sharesToLots(a.buy), sharesToLots(a.sell)) }
    }

    /** 證交所 title 範例："113年10月 2330 台積電           各日成交資訊" → "台積電" */
    fun parseTwseName(title: String?, code: String): String? {
        if (title == null) return null
        val after = title.substringAfter(" $code ", missingDelimiterValue = "").trim()
        if (after.isEmpty()) return null
        return after.split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
