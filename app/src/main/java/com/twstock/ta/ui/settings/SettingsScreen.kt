package com.twstock.ta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twstock.ta.ui.AppViewModelProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val f = state.form

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("指標設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("價格資料") {
                SwitchRow("除權息還原價", f.adjustPrices) { v -> vm.update { it.copy(adjustPrices = v) } }
                Hint("開啟後，除權息日之前的價格依「參考價 ÷ 除權息前收盤價」向前還原，避免除權息缺口造成假訊號。自選股清單的現價不受影響。")
            }

            Section("布林通道") {
                SwitchRow("納入訊號判斷", f.useBollinger) { v -> vm.update { it.copy(useBollinger = v) } }
                FieldRow {
                    NumberField("均線週期", f.bbPeriod, Modifier.weight(1f)) { v -> vm.update { it.copy(bbPeriod = v) } }
                    NumberField("標準差倍數", f.bbMultiplier, Modifier.weight(1f), decimal = true) { v ->
                        vm.update { it.copy(bbMultiplier = v) }
                    }
                }
            }

            Section("KD 隨機指標") {
                SwitchRow("納入訊號判斷", f.useKd) { v -> vm.update { it.copy(useKd = v) } }
                FieldRow {
                    NumberField("RSV 週期", f.kdPeriod, Modifier.weight(1f)) { v -> vm.update { it.copy(kdPeriod = v) } }
                    NumberField("初始值", f.kdInitial, Modifier.weight(1f), decimal = true) { v ->
                        vm.update { it.copy(kdInitial = v) }
                    }
                }
                FieldRow {
                    NumberField("K 平滑係數", f.kdKSmoothing, Modifier.weight(1f)) { v -> vm.update { it.copy(kdKSmoothing = v) } }
                    NumberField("D 平滑係數", f.kdDSmoothing, Modifier.weight(1f)) { v -> vm.update { it.copy(kdDSmoothing = v) } }
                }
                Hint("K = 前日K × (n-1)/n + RSV × 1/n；n = 3 即 2/3、1/3")
                FieldRow {
                    NumberField("超賣門檻", f.kdOversold, Modifier.weight(1f), decimal = true) { v ->
                        vm.update { it.copy(kdOversold = v) }
                    }
                    NumberField("超買門檻", f.kdOverbought, Modifier.weight(1f), decimal = true) { v ->
                        vm.update { it.copy(kdOverbought = v) }
                    }
                }
            }

            Section("MACD") {
                SwitchRow("納入訊號判斷", f.useMacd) { v -> vm.update { it.copy(useMacd = v) } }
                FieldRow {
                    NumberField("快線 EMA", f.macdFast, Modifier.weight(1f)) { v -> vm.update { it.copy(macdFast = v) } }
                    NumberField("慢線 EMA", f.macdSlow, Modifier.weight(1f)) { v -> vm.update { it.copy(macdSlow = v) } }
                    NumberField("訊號線", f.macdSignal, Modifier.weight(1f)) { v -> vm.update { it.copy(macdSignal = v) } }
                }
            }

            Section("訊號確認") {
                FieldRow {
                    NumberField("確認指標數", f.minConfirmations, Modifier.weight(1f)) { v ->
                        vm.update { it.copy(minConfirmations = v) }
                    }
                    NumberField("確認視窗（日）", f.confirmWindow, Modifier.weight(1f)) { v ->
                        vm.update { it.copy(confirmWindow = v) }
                    }
                }
                Hint("在視窗天數內，需有指定數量的不同指標同時給出同方向訊號才發出進出場訊號")
                SwitchRow("收盤後推播通知", f.notificationsEnabled) { v -> vm.update { it.copy(notificationsEnabled = v) } }
            }

            state.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = vm::resetDefaults, modifier = Modifier.weight(1f)) { Text("恢復預設") }
                Button(onClick = vm::save, modifier = Modifier.weight(1f)) { Text("儲存") }
            }
            OutlinedButton(onClick = vm::checkNow, modifier = Modifier.fillMaxWidth()) { Text("立即檢查自選股訊號") }
            Hint("本 App 訊號僅為技術指標之機械式判斷，不構成任何投資建議。")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FieldRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
