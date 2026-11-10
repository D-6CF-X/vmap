package com.vmap.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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

/**
 * 日间配色：冷雾蓝而不是纯白。
 * 纯白背景配应援蓝会发飘、看久了刺眼，这里整体往蓝灰压一档，
 * surface 也留一点蓝（#F8FBFE），卡片和背景之间靠蓝调分层次。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F92C8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1FA79E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFEDF2F8),
    onBackground = Color(0xFF16202B),
    surface = Color(0xFFF8FBFE),
    onSurface = Color(0xFF16202B),
    surfaceVariant = Color(0xFFE3EAF3),
    onSurfaceVariant = Color(0xFF586A7C),
    outline = Color(0xFFC3CEDB),
)

/** 主题切换的过渡时长，日夜两套颜色按这个时间插值。 */
const val THEME_TRANSITION_MS = 420

/**
 * 日夜主题。[darkTheme] 变化时颜色不是硬切，而是 420ms 插值过渡，
 * 地图画布和玻璃面板也读同一套动画中的颜色，所以整体是一起变过去的。
 */
@Composable
fun VMapTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val target = if (darkTheme) DarkColors else LightColors
    val spec = tween<Color>(durationMillis = THEME_TRANSITION_MS)

    val scheme = target.copy(
        primary = animateColorAsState(target.primary, spec, label = "primary").value,
        onPrimary = animateColorAsState(target.onPrimary, spec, label = "onPrimary").value,
        secondary = animateColorAsState(target.secondary, spec, label = "secondary").value,
        background = animateColorAsState(target.background, spec, label = "background").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "onBackground").value,
        surface = animateColorAsState(target.surface, spec, label = "surface").value,
        onSurface = animateColorAsState(target.onSurface, spec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(target.onSurfaceVariant, spec, label = "onSurfaceVariant").value,
        outline = animateColorAsState(target.outline, spec, label = "outline").value,
    )

    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
