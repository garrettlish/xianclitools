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

/**
 * 亮色：奶油纸底 + 纯白卡 + 高饱和强调色。容器色用对应强调色的 100 级淡底，
 * on-container 用 900 级深色，两端都拉满对比。
 */
private val LightColors = lightColorScheme(
    primary = StatsAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = LedgerAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = TrackAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF14532D),
    error = ClayRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Paper,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Color(0xFFFBEDD8),
    onSurfaceVariant = InkSoft,
    surfaceDim = Color(0xFFEADFC8),
    surfaceBright = Color(0xFFFFFDF8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFEFAF2),
    surfaceContainer = Color(0xFFFAF1E0),
    surfaceContainerHigh = PaperDeep,
    surfaceContainerHighest = Color(0xFFF3E5CC),
    outline = LineSoft,
    outlineVariant = LineSoft,
)

/** 暗色：深梅紫夜底，强调色用 400 级亮色，暗底上照样跳得出来。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B2447),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF3B2508),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF0A2913),
    tertiaryContainer = Color(0xFF166534),
    onTertiaryContainer = Color(0xFFDCFCE7),
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF131117),
    onBackground = Color(0xFFF4F2EC),
    surface = Color(0xFF1C1A22),
    onSurface = Color(0xFFF4F2EC),
    surfaceVariant = Color(0xFF2A2732),
    onSurfaceVariant = Color(0xFFC7C2B8),
    surfaceDim = Color(0xFF0E0D12),
    surfaceBright = Color(0xFF312E3A),
    surfaceContainerLowest = Color(0xFF0C0B10),
    surfaceContainerLow = Color(0xFF191720),
    surfaceContainer = Color(0xFF1C1A22),
    surfaceContainerHigh = Color(0xFF262430),
    surfaceContainerHighest = Color(0xFF312E3A),
    outline = Color(0xFF3F3B4A),
    outlineVariant = Color(0xFF3F3B4A),
)

@Composable
fun XianclitoolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 关闭动态取色：六色强调色是跨页一致的设计常量，底色跟着壁纸变会破坏对比。
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
        shapes = PaperShapes,
        content = content
    )
}
