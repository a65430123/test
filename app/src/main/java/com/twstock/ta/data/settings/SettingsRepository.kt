package com.twstock.ta.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twstock.core.model.IndicatorParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val params: IndicatorParams = IndicatorParams(),
    val notificationsEnabled: Boolean = true,
    /** 以除權息還原價計算指標與繪圖 */
    val adjustPrices: Boolean = true,
    /** 在 K 線上標註訊號預告（空心三角形） */
    val showWarningMarkers: Boolean = true,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = IndicatorParams()
        AppSettings(
            params = IndicatorParams(
                bbPeriod = p[BB_PERIOD] ?: d.bbPeriod,
                bbMultiplier = p[BB_MULT] ?: d.bbMultiplier,
                bbNearPercent = p[BB_NEAR] ?: d.bbNearPercent,
                kdNearGap = p[KD_NEAR] ?: d.kdNearGap,
                bbBreakoutVolumeRatio = p[BB_BREAKOUT_VOL] ?: d.bbBreakoutVolumeRatio,
                volumeMaShort = p[VOL_MA_SHORT] ?: d.volumeMaShort,
                volumeMaLong = p[VOL_MA_LONG] ?: d.volumeMaLong,
                kdPeriod = p[KD_PERIOD] ?: d.kdPeriod,
                kdKSmoothing = p[KD_K_SMOOTH] ?: d.kdKSmoothing,
                kdDSmoothing = p[KD_D_SMOOTH] ?: d.kdDSmoothing,
                kdInitial = p[KD_INITIAL] ?: d.kdInitial,
                kdOversold = p[KD_OVERSOLD] ?: d.kdOversold,
                kdOverbought = p[KD_OVERBOUGHT] ?: d.kdOverbought,
                macdFast = p[MACD_FAST] ?: d.macdFast,
                macdSlow = p[MACD_SLOW] ?: d.macdSlow,
                macdSignal = p[MACD_SIGNAL] ?: d.macdSignal,
                bollingerSignals = p[BB_SIGNALS] ?: d.bollingerSignals,
                useBollinger = p[USE_BB] ?: d.useBollinger,
                useBollingerBreakout = p[USE_BB_BREAKOUT] ?: d.useBollingerBreakout,
                useKd = p[USE_KD] ?: d.useKd,
                kdMidCross = p[KD_MID_CROSS] ?: d.kdMidCross,
                useMacd = p[USE_MACD] ?: d.useMacd,
                macdCross = p[MACD_CROSS] ?: d.macdCross,
                macdHistogramTurn = p[MACD_HIST_TURN] ?: d.macdHistogramTurn,
                // 舊版以規則計可存到 5，現改以指標計（1–3）
                minConfirmations = (p[MIN_CONFIRM] ?: d.minConfirmations).coerceIn(1, 3),
                confirmWindow = p[CONFIRM_WINDOW] ?: d.confirmWindow,
            ),
            notificationsEnabled = p[NOTIFY] ?: true,
            adjustPrices = p[ADJUST] ?: true,
            showWarningMarkers = p[SHOW_WARNINGS] ?: true,
        )
    }

    suspend fun saveParams(params: IndicatorParams) {
        require(params.validate().isEmpty())
        store.edit { p ->
            p[BB_PERIOD] = params.bbPeriod
            p[BB_MULT] = params.bbMultiplier
            p[BB_NEAR] = params.bbNearPercent
            p[KD_NEAR] = params.kdNearGap
            p[BB_BREAKOUT_VOL] = params.bbBreakoutVolumeRatio
            p[VOL_MA_SHORT] = params.volumeMaShort
            p[VOL_MA_LONG] = params.volumeMaLong
            p[KD_PERIOD] = params.kdPeriod
            p[KD_K_SMOOTH] = params.kdKSmoothing
            p[KD_D_SMOOTH] = params.kdDSmoothing
            p[KD_INITIAL] = params.kdInitial
            p[KD_OVERSOLD] = params.kdOversold
            p[KD_OVERBOUGHT] = params.kdOverbought
            p[MACD_FAST] = params.macdFast
            p[MACD_SLOW] = params.macdSlow
            p[MACD_SIGNAL] = params.macdSignal
            p[BB_SIGNALS] = params.bollingerSignals
            p[USE_BB] = params.useBollinger
            p[USE_BB_BREAKOUT] = params.useBollingerBreakout
            p[USE_KD] = params.useKd
            p[KD_MID_CROSS] = params.kdMidCross
            p[USE_MACD] = params.useMacd
            p[MACD_CROSS] = params.macdCross
            p[MACD_HIST_TURN] = params.macdHistogramTurn
            p[MIN_CONFIRM] = params.minConfirmations
            p[CONFIRM_WINDOW] = params.confirmWindow
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.edit { it[NOTIFY] = enabled }
    }

    suspend fun setAdjustPrices(enabled: Boolean) {
        store.edit { it[ADJUST] = enabled }
    }

    suspend fun setShowWarningMarkers(enabled: Boolean) {
        store.edit { it[SHOW_WARNINGS] = enabled }
    }

    private companion object {
        val BB_PERIOD = intPreferencesKey("bb_period")
        val BB_MULT = doublePreferencesKey("bb_mult")
        val BB_NEAR = doublePreferencesKey("bb_near")
        val KD_NEAR = doublePreferencesKey("kd_near")
        val SHOW_WARNINGS = booleanPreferencesKey("show_warnings")
        val BB_BREAKOUT_VOL = doublePreferencesKey("bb_breakout_vol")
        val VOL_MA_SHORT = intPreferencesKey("vol_ma_short")
        val VOL_MA_LONG = intPreferencesKey("vol_ma_long")
        val KD_PERIOD = intPreferencesKey("kd_period")
        val KD_K_SMOOTH = intPreferencesKey("kd_k_smooth")
        val KD_D_SMOOTH = intPreferencesKey("kd_d_smooth")
        val KD_INITIAL = doublePreferencesKey("kd_initial")
        val KD_OVERSOLD = doublePreferencesKey("kd_oversold")
        val KD_OVERBOUGHT = doublePreferencesKey("kd_overbought")
        val MACD_FAST = intPreferencesKey("macd_fast")
        val MACD_SLOW = intPreferencesKey("macd_slow")
        val MACD_SIGNAL = intPreferencesKey("macd_signal")
        val BB_SIGNALS = booleanPreferencesKey("bb_signals")
        val USE_BB = booleanPreferencesKey("use_bb")
        val USE_BB_BREAKOUT = booleanPreferencesKey("use_bb_breakout")
        val USE_KD = booleanPreferencesKey("use_kd")
        val KD_MID_CROSS = booleanPreferencesKey("kd_mid_cross")
        val USE_MACD = booleanPreferencesKey("use_macd")
        val MACD_CROSS = booleanPreferencesKey("macd_cross")
        val MACD_HIST_TURN = booleanPreferencesKey("macd_hist_turn")
        val MIN_CONFIRM = intPreferencesKey("min_confirm")
        val CONFIRM_WINDOW = intPreferencesKey("confirm_window")
        val NOTIFY = booleanPreferencesKey("notify")
        val ADJUST = booleanPreferencesKey("adjust_prices")
    }
}
