package com.twstock.core.model

import java.time.LocalDate

/** 單日 K 棒。成交量單位統一為「張」(1 張 = 1000 股)。 */
data class Candle(
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
)
