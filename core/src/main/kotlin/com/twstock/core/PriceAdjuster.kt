package com.twstock.core

import com.twstock.core.model.Candle
import com.twstock.core.model.ExRightEvent

/**
 * 除權息還原（向前還原）：最新價格維持不變，除權息日之前的開高低收
 * 乘上其後所有除權息事件的還原因子連乘積，讓歷史價格與現價可直接比較，
 * 避免除權息缺口造成假的跌破下軌、KD/MACD 轉弱等訊號。
 * 成交量不調整（本 App 的指標未使用成交量）。
 */
object PriceAdjuster {

    fun adjust(candles: List<Candle>, events: List<ExRightEvent>): List<Candle> {
        if (candles.isEmpty() || events.isEmpty()) return candles
        val last = candles.last().date
        val valid = events
            .filter { it.closeBefore > 0 && it.referencePrice > 0 && !it.exDate.isAfter(last) }
            .filter { it.factor in MIN_FACTOR..MAX_FACTOR }
            .distinctBy { it.exDate }
            .sortedByDescending { it.exDate }
        if (valid.isEmpty()) return candles

        val out = ArrayList<Candle>(candles.size)
        var cumulative = 1.0
        var e = 0
        for (i in candles.indices.reversed()) {
            val c = candles[i]
            while (e < valid.size && c.date.isBefore(valid[e].exDate)) {
                cumulative *= valid[e].factor
                e++
            }
            out += if (cumulative == 1.0) c else c.copy(
                open = c.open * cumulative,
                high = c.high * cumulative,
                low = c.low * cumulative,
                close = c.close * cumulative,
            )
        }
        out.reverse()
        return out
    }

    /** 排除明顯錯誤的資料（例如欄位錯位），正常除權息因子介於此範圍。 */
    private const val MIN_FACTOR = 0.2
    private const val MAX_FACTOR = 1.5
}
