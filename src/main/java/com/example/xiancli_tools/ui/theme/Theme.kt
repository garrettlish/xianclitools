package com.example.xiancli_tools.ui.theme

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

private val LightColors = lightColorScheme(
    primary = StatsAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E8F7),
    onPrimaryContainer = Color(0xFF10395C),
    secondary = LedgerAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E4D6),
    onSecondaryContainer = Color(0xFF3A2A1C),
    tertiary = TrackAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1EBD9),
    onTertiaryContainer = Color(0xFF24331F),
    error = ClayRed,
    onError = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1ECE3),
    onSurfaceVariant = InkSoft,
    surfaceDim = Color(0xFFE7E0D5),
    surfaceBright = Color(0xFFFFFDF9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF9F4),
    surfaceContainer = Color(0xFFF8F3EB),
    surfaceContainerHigh = PaperDeep,
    surfaceContainerHighest = Color(0xFFEDE6DA),
    outline = LineSoft,
    outlineVariant = LineSoft,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB3E3),
    onPrimary = Color(0xFF10233A),
    primaryContainer = Color(0xFF1D3B57),
    onPrimaryContainer = Color(0xFFD3E4F6),
    secondary = Color(0xFFC2A184),
    onSecondary = Color(0xFF2A1D12),
    secondaryContainer = Color(0xFF3E2F22),
    onSecondaryContainer = Color(0xFFEBDCCB),
    tertiary = Color(0xFF8FBF95),
    onTertiary = Color(0xFF15281A),
    tertiaryContainer = Color(0xFF2A3B2B),
    onTertiaryContainer = Color(0xFFD5E8D4),
    error = Color(0xFFE8A08C),
    onError = Color(0xFF3A1208),
    background = Color(0xFF171614),
    onBackground = Color(0xFFEDE8E0),
    surface = Color(0xFF201F1C),
    onSurface = Color(0xFFEDE8E0),
    surfaceVariant = Color(0xFF2B2925),
    onSurfaceVariant = Color(0xFFC4BDB2),
    surfaceDim = Color(0xFF121110),
    surfaceBright = Color(0xFF3B3833),
    surfaceContainerLowest = Color(0xFF0F0E0D),
    surfaceContainerLow = Color(0xFF1B1A18),
    surfaceContainer = Color(0xFF201F1C),
    surfaceContainerHigh = Color(0xFF2A2824),
    surfaceContainerHighest = Color(0xFF35322D),
    outline = Color(0xFF3A3730),
    outlineVariant = Color(0xFF3A3730),
)

@Composable
fun XianclitoolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 关闭动态取色：五类强调色是跨页一致的设计常量，底色跟着壁纸变会破坏对比。
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
