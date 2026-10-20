package com.twstock.ta.data.remote

import com.twstock.core.model.Candle
import com.twstock.core.model.ExRightEvent
import java.time.LocalDate

/** 除權除息計算結果表中的一筆資料。 */
data class ExRightRecord(val code: String, val event: ExRightEvent)

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

    /** 證交所 title 範例："113年10月 2330 台積電           各日成交資訊" → "台積電" */
    fun parseTwseName(title: String?, code: String): String? {
        if (title == null) return null
        val after = title.substringAfter(" $code ", missingDelimiterValue = "").trim()
        if (after.isEmpty()) return null
        return after.split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
