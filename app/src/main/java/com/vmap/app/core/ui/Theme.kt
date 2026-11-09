package com.vmap.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

private val VMapColors = darkColorScheme(
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

/**
 * 固定暗色。应援色在亮底上会发灰，舞台上本来就是暗的。
 */
@Composable
fun VMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VMapColors, content = content)
}
