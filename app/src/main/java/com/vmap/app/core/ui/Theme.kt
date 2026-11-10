package com.vmap.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vmap.app.domain.model.Section

/** 洛天依应援色 */
val TianyiBlue = Color(0xFF66CCFF)

/** 初音未来应援色 */
val MikuGreen = Color(0xFF39C5BB)

/** 板块对应的主线色，地图点亮、时间线、按钮统一从这里取。 */
val Section.accent: Color
    get() = when (this) {
        Section.TIANYI -> TianyiBlue
        Section.MIKU -> MikuGreen
    }

private val DarkColors = darkColorScheme(
    primary = TianyiBlue,
    onPrimary = Color(0xFF04202E),
    secondary = MikuGreen,
    onSecondary = Color(0xFF04211F),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF23272F),
    onSurfaceVariant = Color(0xFFA8B0BE),
    outline = Color(0xFF3A404B),
)

private val LightColors = lightColorScheme(
    primary = TianyiBlue,
    onPrimary = Color(0xFF04202E),
    secondary = MikuGreen,
    onSecondary = Color(0xFF04211F),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1A1D22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D22),
    surfaceVariant = Color(0xFFEAEDF1),
    onSurfaceVariant = Color(0xFF5A6170),
    outline = Color(0xFFB7BEC7),
)

/**
 * 白天 / 夜间两套配色。[darkTheme] 为 false 时用浅色，地图画布跟着切。
 */
@Composable
fun VMapTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
