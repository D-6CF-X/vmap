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
private val GlassBaseDark = Color(0xFF10141D)
private val GlassBaseLight = Color(0xFFFBFCFE)

/**
 * 高透玻璃面板：薄雾面底 + 斜向反光 + 底色冷光 + 底部反光 + 内阴影 + 应援色投影。
 *
 * 用法：`Modifier.glassPanel(shape, isDark, accent)` 直接贴在容器上，
 * 里面再正常 `padding` 放内容即可，背景由这一层负责画。
 *
 * 因为底很薄，背后要有东西可折射（地图、光晕、壁纸），贴在纯色上会显得发灰。
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
    base: Float = 0.50f,
): Modifier {
    val s = strength.coerceIn(0f, 1f)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            // 应援色的投影，玻璃像是自己在发光
            ambientColor = accent.copy(alpha = 0.16f * s),
            spotColor = accent.copy(alpha = 0.30f * s),
        )
        .clip(shape)
        .drawBehind {
            // 1. 薄雾面底：透出后面的内容
            val baseAlpha = (if (isDark) base else base + 0.24f).coerceIn(0f, 1f) * s
            drawRect(
                if (isDark) GlassBaseDark.copy(alpha = baseAlpha)
                else GlassBaseLight.copy(alpha = baseAlpha),
            )

            // 2. 斜向反光带：左上打到右下的那道光，玻璃感主要靠它
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = (if (isDark) 0.17f else 0.60f) * s),
                    0.32f to Color.White.copy(alpha = (if (isDark) 0.06f else 0.20f) * s),
                    0.62f to Color.Transparent,
                    1f to Color.White.copy(alpha = (if (isDark) 0.035f else 0.14f) * s),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height * 0.92f),
                ),
            )

            // 3. 应援色冷光，从左上透进来
            drawRect(
                Brush.radialGradient(
                    0f to accent.copy(alpha = (if (isDark) 0.30f else 0.20f) * s),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.14f, -size.height * 0.02f),
                    radius = size.maxDimension * 0.92f,
                ),
            )

            // 3b. 左上角的一点白色反光，玻璃被光源扫到的那一小块
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = (if (isDark) 0.11f else 0.55f) * s),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.06f, size.height * 0.03f),
                    radius = size.minDimension * 0.85f,
                ),
            )

            // 4. 底部反光：玻璃下缘的一层微亮
            drawRect(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Color.White.copy(alpha = (if (isDark) 0.055f else 0.34f) * s),
                ),
            )

            // 5. 内阴影：顶部内侧压暗一点，做出玻璃厚度
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = (if (isDark) 0.14f else 0.06f) * s),
                    0.10f to Color.Transparent,
                ),
            )

            // 6. 顶边一条细高光，玻璃边缘的亮边
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0f),
                        Color.White.copy(alpha = (if (isDark) 0.62f else 0.95f) * s),
                        Color.White.copy(alpha = 0f),
                    ),
                    startX = size.width * 0.03f,
                    endX = size.width * 0.97f,
                ),
                start = Offset(size.width * 0.03f, 0.7f),
                end = Offset(size.width * 0.97f, 0.7f),
                strokeWidth = 1.5f,
            )
        }
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (if (isDark) 0.38f else 0.95f) * s),
                    Color.White.copy(alpha = (if (isDark) 0.08f else 0.42f) * s),
                    accent.copy(alpha = 0.40f * s),
                ),
            ),
            shape = shape,
        )
}
