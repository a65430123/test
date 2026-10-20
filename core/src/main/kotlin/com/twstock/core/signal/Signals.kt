package com.twstock.core.signal

import java.time.LocalDate

enum class SignalSide { BUY, SELL }

enum class SignalSource(val label: String) {
    BOLLINGER("布林收回"),
    BOLLINGER_BREAKOUT("布林突破"),
    KD("KD"),
    MACD("MACD"),
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
