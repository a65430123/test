package com.twstock.core.signal

import com.twstock.core.model.SignalIndicator
import java.time.LocalDate

enum class SignalSide { BUY, SELL }

/** 訊號類型；所屬指標相同的類型在多選確認時只算一個指標。 */
enum class SignalSource(val label: String, val indicator: SignalIndicator) {
    BOLLINGER("布林收回", SignalIndicator.BOLLINGER),
    BOLLINGER_BREAKOUT("布林突破", SignalIndicator.BOLLINGER),
    KD("KD", SignalIndicator.KD),
    MACD("MACD 交叉", SignalIndicator.MACD),
    MACD_HISTOGRAM("MACD 柱狀轉折", SignalIndicator.MACD),
}

/** 單一指標在某日觸發的事件。 */
data class IndicatorEvent(
    val index: Int,
    val date: LocalDate,
    val side: SignalSide,
    val source: SignalSource,
    val description: String,
)

/** 進出場訊號（單一指標任一類型觸發，或多選時經多個指標確認）。 */
data class TradeSignal(
    val index: Int,
    val date: LocalDate,
    val side: SignalSide,
    val price: Double,
    /** 構成此訊號的指標事件（確認視窗內）。 */
    val events: List<IndicatorEvent>,
) {
    /** 依指標分組的訊號類型（指標依 [SignalIndicator] 順序）。 */
    val groups: List<Pair<SignalIndicator, List<IndicatorEvent>>>
        get() = events.groupBy { it.source.indicator }.toSortedMap().map { (k, v) -> k to v }

    /** 例如「布林通道：跌破布林下軌、布林跌破下軌後收回｜KD：KD 低檔黃金交叉 (K=14.8)」 */
    val summary: String
        get() = groups.joinToString("｜") { (indicator, list) ->
            "${indicator.label}：" + list.joinToString("、") { it.description }
        }
}
