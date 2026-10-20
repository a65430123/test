package com.twstock.ta.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 台股慣例：紅漲綠跌。 */
object MarketColors {
    val Up = Color(0xFFE53935)
    val Down = Color(0xFF2E9E4F)
    val Flat = Color(0xFF9E9E9E)
    val BollingerMid = Color(0xFFFF9800)
    val BollingerBand = Color(0xFF7E57C2)
    val K = Color(0xFFFF9800)
    val D = Color(0xFF1E88E5)
    val Dif = Color(0xFFFF9800)
    val Signal = Color(0xFF1E88E5)

    fun forChange(change: Double?): Color = when {
        change == null || change == 0.0 -> Flat
        change > 0 -> Up
        else -> Down
    }
}

@Composable
fun TwStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
