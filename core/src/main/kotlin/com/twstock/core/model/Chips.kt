package com.twstock.core.model

import java.time.LocalDate

/** 籌碼訊號參考的法人別。 */
enum class ChipParty(val label: String) {
    TOTAL("三大法人"),
    FOREIGN("外資"),
    TRUST("投信"),
    DEALER("自營商"),
}

/**
 * 單日籌碼（單位：張）。法人買賣超與融資融券來自不同資料集，任一方缺資料時為 null。
 */
data class ChipDay(
    val date: LocalDate,
    val foreignNet: Long? = null,
    val trustNet: Long? = null,
    val dealerNet: Long? = null,
    val totalNet: Long? = null,
    val marginBalance: Long? = null,
    val marginChange: Long? = null,
    val shortBalance: Long? = null,
    val shortChange: Long? = null,
) {
    fun net(party: ChipParty): Long? = when (party) {
        ChipParty.TOTAL -> totalNet
        ChipParty.FOREIGN -> foreignNet
        ChipParty.TRUST -> trustNet
        ChipParty.DEALER -> dealerNet
    }
}

/** 分點券商單日進出（單位：張）。 */
data class BrokerFlow(
    val brokerId: String,
    val brokerName: String,
    val buy: Long,
    val sell: Long,
) {
    val net: Long get() = buy - sell
}
