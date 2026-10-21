package com.twstock.ta.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.twstock.core.model.ChipParty
import com.twstock.core.model.IndicatorParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val params: IndicatorParams = IndicatorParams(),
    val notificationsEnabled: Boolean = true,
    /** 以除權息還原價計算指標與繪圖 */
    val adjustPrices: Boolean = true,
    /** FinMind API token（分點券商資料用）；空字串表示未設定 */
    val finMindToken: String = "",
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = IndicatorParams()
        AppSettings(
            params = IndicatorParams(
                bbPeriod = p[BB_PERIOD] ?: d.bbPeriod,
                bbMultiplier = p[BB_MULT] ?: d.bbMultiplier,
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
                useChips = p[USE_CHIPS] ?: d.useChips,
                chipParty = p[CHIP_PARTY]?.let { name -> ChipParty.entries.firstOrNull { it.name == name } } ?: d.chipParty,
                chipStreakDays = p[CHIP_STREAK] ?: d.chipStreakDays,
                chipMinNet = p[CHIP_MIN_NET] ?: d.chipMinNet,
                minConfirmations = p[MIN_CONFIRM] ?: d.minConfirmations,
                confirmWindow = p[CONFIRM_WINDOW] ?: d.confirmWindow,
            ),
            notificationsEnabled = p[NOTIFY] ?: true,
            adjustPrices = p[ADJUST] ?: true,
            finMindToken = p[FINMIND_TOKEN] ?: "",
        )
    }

    suspend fun saveParams(params: IndicatorParams) {
        require(params.validate().isEmpty())
        store.edit { p ->
            p[BB_PERIOD] = params.bbPeriod
            p[BB_MULT] = params.bbMultiplier
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
            p[USE_CHIPS] = params.useChips
            p[CHIP_PARTY] = params.chipParty.name
            p[CHIP_STREAK] = params.chipStreakDays
            p[CHIP_MIN_NET] = params.chipMinNet
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

    suspend fun setFinMindToken(token: String) {
        store.edit { it[FINMIND_TOKEN] = token.trim() }
    }

    private companion object {
        val BB_PERIOD = intPreferencesKey("bb_period")
        val BB_MULT = doublePreferencesKey("bb_mult")
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
        val USE_CHIPS = booleanPreferencesKey("use_chips")
        val CHIP_PARTY = stringPreferencesKey("chip_party")
        val CHIP_STREAK = intPreferencesKey("chip_streak")
        val CHIP_MIN_NET = intPreferencesKey("chip_min_net")
        val FINMIND_TOKEN = stringPreferencesKey("finmind_token")
        val MIN_CONFIRM = intPreferencesKey("min_confirm")
        val CONFIRM_WINDOW = intPreferencesKey("confirm_window")
        val NOTIFY = booleanPreferencesKey("notify")
        val ADJUST = booleanPreferencesKey("adjust_prices")
    }
}
