package com.twstock.core.signal

import java.time.LocalDate

enum class SignalSide { BUY, SELL }

/** 確認機制以「指標」為單位計數；同一指標的不同規則不能互相確認。 */
enum class ConfirmGroup(val label: String) {
    BOLLINGER("布林通道"),
    KD("KD"),
    MACD("MACD"),
}

enum class SignalSource(val label: String, val group: ConfirmGroup) {
    BOLLINGER("布林收回", ConfirmGroup.BOLLINGER),
    BOLLINGER_BREAKOUT("布林突破", ConfirmGroup.BOLLINGER),
    KD("KD", ConfirmGroup.KD),
    MACD("MACD", ConfirmGroup.MACD),
}

/** 單一指標在某日觸發的事件。 */
data class IndicatorEvent(
    val index: Int,
    val date: LocalDate,
    val side: SignalSide,
    val source: SignalSource,
    val description: String,
)

/** 經多指標確認後的進出場訊號。 */
data class TradeSignal(
    val index: Int,
    val date: LocalDate,
    val side: SignalSide,
    val price: Double,
    /** 構成此訊號的指標事件（確認視窗內）。 */
    val events: List<IndicatorEvent>,
) {
    val summary: String
        get() = events.joinToString("、") { it.description }
}
