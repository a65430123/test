package com.twstock.ta

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twstock.ta.data.settings.AppSettings
import com.twstock.ta.notification.SignalNotifier
import com.twstock.ta.ui.navigation.AppNavHost
import com.twstock.ta.ui.theme.TwStockTheme
import com.twstock.ta.ui.theme.isDark

class MainActivity : ComponentActivity() {

    private var pendingStockCode by mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) pendingStockCode = intent.stockCode()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SignalNotifier.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val settingsFlow = (application as TaStockApp).container.settingsRepository.settings
        setContent {
            // 設定載入前先用預設（深色），避免閃白
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = settings.themeMode.isDark()

            // 狀態列、導覽列圖示顏色跟著 App 的外觀，而非系統設定
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            TwStockTheme(darkTheme = dark) {
                AppNavHost(
                    pendingStockCode = pendingStockCode,
                    onPendingConsumed = { pendingStockCode = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.stockCode()?.let { pendingStockCode = it }
    }

    private fun Intent?.stockCode(): String? = this?.getStringExtra(SignalNotifier.EXTRA_STOCK_CODE)
}
