package com.twstock.core.model

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
    // MACD
    val macdFast: Int = 12,
    val macdSlow: Int = 26,
    val macdSignal: Int = 9,
    // 訊號判斷
    /** 布林通道收回訊號（跌破下軌後收回、突破上軌後跌回）。 */
    val useBollinger: Boolean = true,
    /** 布林通道突破訊號（收盤突破上軌、跌破下軌）。 */
    val useBollingerBreakout: Boolean = true,
    val useKd: Boolean = true,
    val useMacd: Boolean = true,
    /** 同方向需有幾個不同指標確認才產生進出場訊號。 */
    val minConfirmations: Int = 2,
    /** 確認視窗（交易日）：在此天數內出現的同方向指標事件可互相確認。 */
    val confirmWindow: Int = 3,
) {
    val enabledIndicatorCount: Int
        get() = listOf(useBollinger, useBollingerBreakout, useKd, useMacd).count { it }

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
        if (macdFast !in 1..250 || macdSlow !in 1..250 || macdSignal !in 1..250) add("MACD 週期需介於 1–250")
        if (macdFast >= macdSlow) add("MACD 快線週期需小於慢線週期")
        if (enabledIndicatorCount == 0) add("至少需啟用一個指標作為訊號來源")
        if (minConfirmations !in 1..4) add("確認指標數需介於 1–4")
        if (minConfirmations > enabledIndicatorCount) add("確認指標數不可大於已啟用的指標數")
        if (confirmWindow !in 1..20) add("確認視窗需介於 1–20 日")
    }
}
