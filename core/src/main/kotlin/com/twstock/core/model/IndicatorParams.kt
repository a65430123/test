package com.twstock.core.model

/** 可被選為進出場訊號來源的指標。 */
enum class SignalIndicator(val label: String) {
    BOLLINGER("布林通道"),
    KD("KD"),
    MACD("MACD"),
}

/**
 * 所有可於設定頁調整的指標與訊號參數。
 *
 * KD 平滑係數：K = 前日K × (kSmoothing-1)/kSmoothing + RSV × 1/kSmoothing，
 * 預設 kSmoothing = 3 即 K = 前日K×2/3 + RSV×1/3；D 同理。
 */
data class IndicatorParams(
    // 布林通道
    val bbPeriod: Int = 20,
    val bbMultiplier: Double = 2.0,
    /** 布林突破訊號需量增：當日量 ≥ 倍數 × 前一日的短期均量；0 表示不檢查。 */
    val bbBreakoutVolumeRatio: Double = 0.0,
    /** 訊號預告：收盤距布林上／下軌在此百分比內，提示即將突破／跌破 */
    val bbNearPercent: Double = 1.0,
    // 成交量均線
    val volumeMaShort: Int = 5,
    val volumeMaLong: Int = 20,
    // KD
    val kdPeriod: Int = 9,
    val kdKSmoothing: Int = 3,
    val kdDSmoothing: Int = 3,
    val kdInitial: Double = 50.0,
    val kdOversold: Double = 20.0,
    val kdOverbought: Double = 80.0,
    /** 訊號預告：K、D 差距在此值內且朝交叉方向移動，提示即將交叉 */
    val kdNearGap: Double = 3.0,
    // MACD
    val macdFast: Int = 12,
    val macdSlow: Int = 26,
    val macdSignal: Int = 9,
    // 訊號判斷
    /** 布林通道是否作為訊號來源；包含哪些規則由 useBollinger、useBollingerBreakout 決定。 */
    val bollingerSignals: Boolean = true,
    /** 布林通道收回訊號（跌破下軌後收回、突破上軌後跌回）。 */
    val useBollinger: Boolean = true,
    /** 布林通道突破訊號（收盤突破上軌賣、跌破下軌買）。 */
    val useBollingerBreakout: Boolean = true,
    /** KD 是否作為訊號來源（高低檔交叉固定啟用） */
    val useKd: Boolean = true,
    /** KD 中檔交叉：高低檔以外（前日 K 介於超賣與超買門檻之間）的黃金／死亡交叉 */
    val kdMidCross: Boolean = true,
    /** MACD 是否作為訊號來源；包含哪些規則由 macdCross、macdHistogramTurn 決定。 */
    val useMacd: Boolean = true,
    /** MACD 交叉訊號（DIF 穿越訊號線，即柱狀翻正／翻負） */
    val macdCross: Boolean = true,
    /** MACD 柱狀轉折訊號（正柱狀由最高點第一根往下賣、負柱狀由最低點第一根往上買） */
    val macdHistogramTurn: Boolean = true,
    /**
     * 多選指標時，同方向需有幾個不同指標（布林通道、KD、MACD）確認才產生進出場訊號；
     * 同一指標的多種訊號類型只算一個。只選一個指標時不適用（該指標任一類型即為訊號）。
     */
    val minConfirmations: Int = 2,
    /** 確認視窗（交易日）：在此天數內出現的同方向指標事件可互相確認。 */
    val confirmWindow: Int = 3,
) {
    val bollingerPullbackActive: Boolean get() = bollingerSignals && useBollinger
    val bollingerBreakoutActive: Boolean get() = bollingerSignals && useBollingerBreakout
    val macdCrossActive: Boolean get() = useMacd && macdCross
    val macdHistogramActive: Boolean get() = useMacd && macdHistogramTurn

    /** 目前選為訊號來源的指標。 */
    val selectedIndicators: List<SignalIndicator>
        get() = buildList {
            if (bollingerPullbackActive || bollingerBreakoutActive) add(SignalIndicator.BOLLINGER)
            if (useKd) add(SignalIndicator.KD)
            if (macdCrossActive || macdHistogramActive) add(SignalIndicator.MACD)
        }

    /** 實際需要的確認指標數：只選一個指標時為 1，多選時為 minConfirmations（不超過已選指標數）。 */
    val requiredConfirmations: Int
        get() = if (selectedIndicators.size <= 1) 1 else minConfirmations.coerceIn(1, selectedIndicators.size)

    /** 例如「只看 KD」、「布林通道＋KD＋MACD：3 日內至少 2 個指標同向」。 */
    fun describeSignalMode(): String {
        val names = selectedIndicators.joinToString("＋") { it.label }
        return when {
            selectedIndicators.isEmpty() -> "未選擇訊號來源"
            selectedIndicators.size == 1 -> "只看$names"
            requiredConfirmations == 1 -> "$names：任一觸發"
            else -> "$names：${confirmWindow} 日內至少 $requiredConfirmations 個指標同向"
        }
    }

    /** 切換某指標是否作為訊號來源。重新選回布林通道或 MACD 時，若其規則都關閉則一併開啟。 */
    fun toggleIndicator(indicator: SignalIndicator, selected: Boolean): IndicatorParams = when (indicator) {
        SignalIndicator.BOLLINGER ->
            if (selected && !useBollinger && !useBollingerBreakout) {
                copy(bollingerSignals = true, useBollinger = true, useBollingerBreakout = true)
            } else {
                copy(bollingerSignals = selected)
            }
        SignalIndicator.KD -> copy(useKd = selected)
        SignalIndicator.MACD ->
            if (selected && !macdCross && !macdHistogramTurn) {
                copy(useMacd = true, macdCross = true, macdHistogramTurn = true)
            } else {
                copy(useMacd = selected)
            }
    }

    /** 回傳錯誤訊息清單；空清單代表參數合法。 */
    fun validate(): List<String> = buildList {
        if (bbPeriod !in 2..250) add("布林通道週期需介於 2–250")
        if (bbMultiplier <= 0.0 || bbMultiplier > 10.0) add("布林通道標準差倍數需介於 0–10")
        if (bbBreakoutVolumeRatio < 0.0 || bbBreakoutVolumeRatio > 20.0) add("突破量增倍數需介於 0–20")
        if (volumeMaShort !in 1..250 || volumeMaLong !in 1..250) add("成交量均線週期需介於 1–250")
        if (kdPeriod !in 2..250) add("KD RSV 週期需介於 2–250")
        if (kdKSmoothing !in 1..50) add("K 值平滑係數需介於 1–50")
        if (kdDSmoothing !in 1..50) add("D 值平滑係數需介於 1–50")
        if (kdInitial !in 0.0..100.0) add("KD 初始值需介於 0–100")
        if (kdOversold !in 0.0..100.0 || kdOverbought !in 0.0..100.0) add("KD 超買/超賣門檻需介於 0–100")
        if (kdOversold >= kdOverbought) add("KD 超賣門檻需小於超買門檻")
        if (bbNearPercent < 0.0 || bbNearPercent > 10.0) add("布林預告門檻需介於 0–10%")
        if (kdNearGap < 0.0 || kdNearGap > 50.0) add("KD 預告差距需介於 0–50")
        if (macdFast !in 1..250 || macdSlow !in 1..250 || macdSignal !in 1..250) add("MACD 週期需介於 1–250")
        if (macdFast >= macdSlow) add("MACD 快線週期需小於慢線週期")
        if (selectedIndicators.isEmpty()) add("至少需選擇一個指標作為訊號來源")
        if (minConfirmations !in 1..3) add("確認指標數需介於 1–3")
        if (confirmWindow !in 1..20) add("確認視窗需介於 1–20 日")
    }
}
