package com.twstock.ta.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.twstock.ta.ui.detail.StockDetailScreen
import com.twstock.ta.ui.settings.SettingsScreen
import com.twstock.ta.ui.watchlist.WatchlistScreen

object Routes {
    const val WATCHLIST = "watchlist"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{code}"
    fun detail(code: String) = "detail/${Uri.encode(code)}"
}

/**
 * @param pendingStockCode 從推播點入時要開啟的股票，處理後呼叫 [onPendingConsumed]。
 */
@Composable
fun AppNavHost(pendingStockCode: String?, onPendingConsumed: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(pendingStockCode) {
        if (pendingStockCode != null) {
            nav.navigate(Routes.detail(pendingStockCode)) { launchSingleTop = true }
            onPendingConsumed()
        }
    }

    NavHost(navController = nav, startDestination = Routes.WATCHLIST) {
        composable(Routes.WATCHLIST) {
            WatchlistScreen(
                onOpenStock = { nav.navigate(Routes.detail(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("code") { type = NavType.StringType }),
        ) {
            StockDetailScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
