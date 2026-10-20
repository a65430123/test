package com.twstock.core.model

import java.time.LocalDate

/**
 * 除權息事件（取自證交所/櫃買中心「除權除息計算結果表」）。
 * 還原因子 = 除權息參考價 / 除權息前收盤價，同時涵蓋現金股利（息）與股票股利（權）。
 */
data class ExRightEvent(
    val exDate: LocalDate,
    val closeBefore: Double,
    val referencePrice: Double,
) {
    val factor: Double
        get() = referencePrice / closeBefore
}
