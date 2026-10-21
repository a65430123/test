package com.twstock.ta.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** 主畫面底部導覽：選股／自選股。 */
@Composable
fun MainBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.SCREENER,
            onClick = { onNavigate(Routes.SCREENER) },
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("選股") },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.WATCHLIST,
            onClick = { onNavigate(Routes.WATCHLIST) },
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text("自選股") },
        )
    }
}
