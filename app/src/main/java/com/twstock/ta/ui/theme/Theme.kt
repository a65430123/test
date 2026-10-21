package com.twstock.ta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.twstock.core.model.SignalIndicator
import com.twstock.ta.data.settings.ThemeMode

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
    val ExRight = Color(0xFF8D6E63)
    val VolumeMaShort = Color(0xFFFF9800)
    val VolumeMaLong = Color(0xFF1E88E5)

    fun forIndicator(indicator: SignalIndicator): Color = when (indicator) {
        SignalIndicator.BOLLINGER -> BollingerBand
        SignalIndicator.KD -> K
        SignalIndicator.MACD -> D
    }

    fun forChange(change: Double?): Color = when {
        change == null || change == 0.0 -> Flat
        change > 0 -> Up
        else -> Down
    }
}

/** 深色：近黑底、略帶藍的中性灰，K 線紅綠與指標線在深底上保持清楚。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA6F2),
    onPrimary = Color(0xFF0D1017),
    primaryContainer = Color(0xFF262D4C),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFB4BBD4),
    onSecondary = Color(0xFF1E2332),
    secondaryContainer = Color(0xFF262D4C),
    onSecondaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6E9F0),
    surface = Color(0xFF0B0E14),
    onSurface = Color(0xFFE6E9F0),
    surfaceVariant = Color(0xFF1C2130),
    onSurfaceVariant = Color(0xFF9AA2B4),
    surfaceContainerLowest = Color(0xFF080A0F),
    surfaceContainerLow = Color(0xFF11141C),
    surfaceContainer = Color(0xFF141822),
    surfaceContainerHigh = Color(0xFF1C2130),
    surfaceContainerHighest = Color(0xFF242A3A),
    outline = Color(0xFF3A4152),
    outlineVariant = Color(0xFF2B3141),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2B3A8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E5F6),
    onPrimaryContainer = Color(0xFF101A55),
    secondaryContainer = Color(0xFFE1E5F6),
    onSecondaryContainer = Color(0xFF101A55),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF161A23),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF161A23),
    surfaceVariant = Color(0xFFEEF1F7),
    onSurfaceVariant = Color(0xFF5B6273),
    surfaceContainer = Color(0xFFEEF1F7),
    surfaceContainerHigh = Color(0xFFE6EAF2),
    outline = Color(0xFFB7BDCB),
    outlineVariant = Color(0xFFD7DBE4),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun TwStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
