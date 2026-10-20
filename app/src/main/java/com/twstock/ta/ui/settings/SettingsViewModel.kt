package com.twstock.ta.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twstock.core.model.IndicatorParams
import com.twstock.ta.data.settings.SettingsRepository
import com.twstock.ta.worker.SignalCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 設定頁表單，數值以字串保存以便使用者編輯。 */
data class SettingsForm(
    val bbPeriod: String = "",
    val bbMultiplier: String = "",
    val kdPeriod: String = "",
    val kdKSmoothing: String = "",
    val kdDSmoothing: String = "",
    val kdInitial: String = "",
    val kdOversold: String = "",
    val kdOverbought: String = "",
    val macdFast: String = "",
    val macdSlow: String = "",
    val macdSignal: String = "",
    val useBollinger: Boolean = true,
    val useKd: Boolean = true,
    val useMacd: Boolean = true,
    val minConfirmations: String = "",
    val confirmWindow: String = "",
    val notificationsEnabled: Boolean = true,
) {
    fun toParams(): IndicatorParams? = runCatching {
        IndicatorParams(
            bbPeriod = bbPeriod.trim().toInt(),
            bbMultiplier = bbMultiplier.trim().toDouble(),
            kdPeriod = kdPeriod.trim().toInt(),
            kdKSmoothing = kdKSmoothing.trim().toInt(),
            kdDSmoothing = kdDSmoothing.trim().toInt(),
            kdInitial = kdInitial.trim().toDouble(),
            kdOversold = kdOversold.trim().toDouble(),
            kdOverbought = kdOverbought.trim().toDouble(),
            macdFast = macdFast.trim().toInt(),
            macdSlow = macdSlow.trim().toInt(),
            macdSignal = macdSignal.trim().toInt(),
            useBollinger = useBollinger,
            useKd = useKd,
            useMacd = useMacd,
            minConfirmations = minConfirmations.trim().toInt(),
            confirmWindow = confirmWindow.trim().toInt(),
        )
    }.getOrNull()

    companion object {
        fun from(p: IndicatorParams, notify: Boolean) = SettingsForm(
            bbPeriod = p.bbPeriod.toString(),
            bbMultiplier = p.bbMultiplier.toString(),
            kdPeriod = p.kdPeriod.toString(),
            kdKSmoothing = p.kdKSmoothing.toString(),
            kdDSmoothing = p.kdDSmoothing.toString(),
            kdInitial = p.kdInitial.toString(),
            kdOversold = p.kdOversold.toString(),
            kdOverbought = p.kdOverbought.toString(),
            macdFast = p.macdFast.toString(),
            macdSlow = p.macdSlow.toString(),
            macdSignal = p.macdSignal.toString(),
            useBollinger = p.useBollinger,
            useKd = p.useKd,
            useMacd = p.useMacd,
            minConfirmations = p.minConfirmations.toString(),
            confirmWindow = p.confirmWindow.toString(),
            notificationsEnabled = notify,
        )
    }
}

data class SettingsUiState(
    val loaded: Boolean = false,
    val form: SettingsForm = SettingsForm(),
    val errors: List<String> = emptyList(),
    val message: String? = null,
)

class SettingsViewModel(
    private val app: Application,
    private val repo: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = repo.settings.first()
            _state.value = SettingsUiState(loaded = true, form = SettingsForm.from(s.params, s.notificationsEnabled))
        }
    }

    fun update(transform: (SettingsForm) -> SettingsForm) {
        _state.update { it.copy(form = transform(it.form), errors = emptyList()) }
    }

    fun resetDefaults() {
        _state.update {
            it.copy(form = SettingsForm.from(IndicatorParams(), it.form.notificationsEnabled), errors = emptyList())
        }
    }

    fun save() {
        val form = _state.value.form
        val params = form.toParams()
        val errors = params?.validate() ?: listOf("請確認所有欄位皆為有效數字")
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            repo.saveParams(params!!)
            repo.setNotificationsEnabled(form.notificationsEnabled)
            _state.update { it.copy(message = "已儲存設定") }
        }
    }

    fun checkNow() {
        SignalCheckWorker.runNow(app)
        _state.update { it.copy(message = "已開始檢查自選股訊號") }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }
}
