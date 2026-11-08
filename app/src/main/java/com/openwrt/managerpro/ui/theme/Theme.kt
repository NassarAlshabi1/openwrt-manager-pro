package com.openwrt.managerpro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Cyan = Color(0xFF22D3EE)
private val CyanSoft = Color(0xFF67E8F9)
private val Bg = Color(0xFF080B10)
private val Surface = Color(0xFF0E131A)
private val SurfaceVariant = Color(0xFF171E27)
private val OnBg = Color(0xFFE6EDF3)
private val OnSurfaceMuted = Color(0xFF9FB1C1)
private val Outline = Color(0xFF2A3644)
private val Green = Color(0xFF4ADE80)
private val Amber = Color(0xFFFBBF24)
private val Red = Color(0xFFF87171)

private val DarkScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF04252B),
    primaryContainer = Color(0xFF0E4A55),
    onPrimaryContainer = Color(0xFFB8F1FB),
    secondary = CyanSoft,
    onSecondary = Color(0xFF04252B),
    tertiary = Green,
    background = Bg,
    onBackground = OnBg,
    surface = Surface,
    onSurface = OnBg,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    error = Red,
    onError = Color(0xFF2B0505),
    outline = Outline,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    secondary = Color(0xFF155E75),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF10161D),
    surface = Color.White,
    onSurface = Color(0xFF10161D),
    surfaceVariant = Color(0xFFE7EDF2),
    onSurfaceVariant = Color(0xFF46555F),
    error = Color(0xFFB91C1C),
    outline = Color(0xFFC4CFD6),
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
