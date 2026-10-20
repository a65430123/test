package com.twstock.ta.data.remote

import com.twstock.core.model.Candle
import java.time.LocalDate

/** 將證交所/櫃買中心的字串表格轉為 [Candle]。 */
object MarketDataParser {

    /** 民國日期 "113/10/01"（可能夾雜 ＊ 等註記符號）→ LocalDate */
    fun parseRocDate(text: String): LocalDate? {
        val parts = text.filter { it.isDigit() || it == '/' }.split('/')
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(y + 1911, m, d) }.getOrNull()
    }

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

    /** 證交所 title 範例："113年10月 2330 台積電           各日成交資訊" → "台積電" */
    fun parseTwseName(title: String?, code: String): String? {
        if (title == null) return null
        val after = title.substringAfter(" $code ", missingDelimiterValue = "").trim()
        if (after.isEmpty()) return null
        return after.split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
