package com.twstock.ta

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.twstock.ta.notification.SignalNotifier
import com.twstock.ta.ui.navigation.AppNavHost
import com.twstock.ta.ui.theme.TwStockTheme

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

        setContent {
            TwStockTheme {
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
