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
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = IndicatorParams()
        AppSettings(
            params = IndicatorParams(
                bbPeriod = p[BB_PERIOD] ?: d.bbPeriod,
                bbMultiplier = p[BB_MULT] ?: d.bbMultiplier,
                kdPeriod = p[KD_PERIOD] ?: d.kdPeriod,
                kdKSmoothing = p[KD_K_SMOOTH] ?: d.kdKSmoothing,
                kdDSmoothing = p[KD_D_SMOOTH] ?: d.kdDSmoothing,
                kdInitial = p[KD_INITIAL] ?: d.kdInitial,
                kdOversold = p[KD_OVERSOLD] ?: d.kdOversold,
                kdOverbought = p[KD_OVERBOUGHT] ?: d.kdOverbought,
                macdFast = p[MACD_FAST] ?: d.macdFast,
                macdSlow = p[MACD_SLOW] ?: d.macdSlow,
                macdSignal = p[MACD_SIGNAL] ?: d.macdSignal,
                useBollinger = p[USE_BB] ?: d.useBollinger,
                useKd = p[USE_KD] ?: d.useKd,
                useMacd = p[USE_MACD] ?: d.useMacd,
                minConfirmations = p[MIN_CONFIRM] ?: d.minConfirmations,
                confirmWindow = p[CONFIRM_WINDOW] ?: d.confirmWindow,
            ),
            notificationsEnabled = p[NOTIFY] ?: true,
            adjustPrices = p[ADJUST] ?: true,
        )
    }

    suspend fun saveParams(params: IndicatorParams) {
        require(params.validate().isEmpty())
        store.edit { p ->
            p[BB_PERIOD] = params.bbPeriod
            p[BB_MULT] = params.bbMultiplier
            p[KD_PERIOD] = params.kdPeriod
            p[KD_K_SMOOTH] = params.kdKSmoothing
            p[KD_D_SMOOTH] = params.kdDSmoothing
            p[KD_INITIAL] = params.kdInitial
            p[KD_OVERSOLD] = params.kdOversold
            p[KD_OVERBOUGHT] = params.kdOverbought
            p[MACD_FAST] = params.macdFast
            p[MACD_SLOW] = params.macdSlow
            p[MACD_SIGNAL] = params.macdSignal
            p[USE_BB] = params.useBollinger
            p[USE_KD] = params.useKd
            p[USE_MACD] = params.useMacd
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

    private companion object {
        val BB_PERIOD = intPreferencesKey("bb_period")
        val BB_MULT = doublePreferencesKey("bb_mult")
        val KD_PERIOD = intPreferencesKey("kd_period")
        val KD_K_SMOOTH = intPreferencesKey("kd_k_smooth")
        val KD_D_SMOOTH = intPreferencesKey("kd_d_smooth")
        val KD_INITIAL = doublePreferencesKey("kd_initial")
        val KD_OVERSOLD = doublePreferencesKey("kd_oversold")
        val KD_OVERBOUGHT = doublePreferencesKey("kd_overbought")
        val MACD_FAST = intPreferencesKey("macd_fast")
        val MACD_SLOW = intPreferencesKey("macd_slow")
        val MACD_SIGNAL = intPreferencesKey("macd_signal")
        val USE_BB = booleanPreferencesKey("use_bb")
        val USE_KD = booleanPreferencesKey("use_kd")
        val USE_MACD = booleanPreferencesKey("use_macd")
        val MIN_CONFIRM = intPreferencesKey("min_confirm")
        val CONFIRM_WINDOW = intPreferencesKey("confirm_window")
        val NOTIFY = booleanPreferencesKey("notify")
        val ADJUST = booleanPreferencesKey("adjust_prices")
    }
}
