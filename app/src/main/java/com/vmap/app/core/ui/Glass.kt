package com.vmap.app.core.ui

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 玻璃面板的雾面基色：暗色偏冷灰蓝，亮色接近纯白。 */
private val GlassBaseDark = Color(0xFF141821)
private val GlassBaseLight = Color(0xFFFBFCFE)

/**
 * 玻璃质感面板：半透明雾面底色 + 顶部受光渐变 + 应援色冷光 + 玻璃亮边 + 外投影。
 *
 * 用法：`Modifier.glassPanel(shape, isDark, accent)` 直接贴在容器上，
 * 里面再正常 `padding` 放内容即可，背景由这一层负责画。
 *
 * @param strength 整体强度，0f 完全透明（用于淡出动画）。
 * @param base 雾面底色的不透明度，越小越透。
 */
fun Modifier.glassPanel(
    shape: Shape,
    isDark: Boolean,
    accent: Color,
    elevation: Dp = 18.dp,
    strength: Float = 1f,
    base: Float = 0.72f,
): Modifier {
    val s = strength.coerceIn(0f, 1f)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color(0xFF000000),
            spotColor = if (isDark) Color(0xFF000000) else Color(0x33000000),
        )
        .clip(shape)
        .drawBehind {
            // 1. 雾面底：让身后的地图隐约透出来
            val baseAlpha = (if (isDark) base else base + 0.16f).coerceIn(0f, 1f) * s
            drawRect(
                if (isDark) GlassBaseDark.copy(alpha = baseAlpha)
                else GlassBaseLight.copy(alpha = baseAlpha),
            )
            // 2. 顶部受光的竖向渐变（上亮下沉，玻璃的厚度感）
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = (if (isDark) 0.14f else 0.55f) * s),
                    0.42f to Color.White.copy(alpha = (if (isDark) 0.05f else 0.16f) * s),
                    1f to Color.White.copy(alpha = (if (isDark) 0.015f else 0.28f) * s),
                ),
            )
            // 3. 应援色冷光，从左上方透进来
            drawRect(
                Brush.radialGradient(
                    0f to accent.copy(alpha = (if (isDark) 0.22f else 0.15f) * s),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.16f, -size.height * 0.04f),
                    radius = size.maxDimension * 0.95f,
                ),
            )
            // 4. 顶边一条细高光，玻璃边缘的亮边
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = (if (isDark) 0.5f else 0.95f) * s),
                        Color.White.copy(alpha = 0f),
                    ),
                    startX = size.width * 0.04f,
                    endX = size.width * 0.96f,
                ),
                start = Offset(size.width * 0.04f, 0.7f),
                end = Offset(size.width * 0.96f, 0.7f),
                strokeWidth = 1.4f,
            )
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (if (isDark) 0.30f else 0.95f) * s),
                    Color.White.copy(alpha = (if (isDark) 0.06f else 0.40f) * s),
                    accent.copy(alpha = 0.32f * s),
                ),
            ),
            shape = shape,
        )
}
