package com.vmap.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * 霓虹渐变立体字。
 *
 * 夜间是白→应援色（顶部反光、底部收暗）+ 应援色发光；
 * 日间反过来用深蓝→应援色，否则白字压在浅色背景上直接消失。
 * 明暗从主题 surface 的亮度读，日夜切换时跟着过渡。
 */
@Composable
fun gradientTextStyle(accent: Color, fontSize: TextUnit): TextStyle {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val deep = Color(0xFF08161E)

    val colors = if (dark) {
        listOf(Color.White, accent, accent.copy(alpha = 0.82f))
    } else {
        listOf(
            lerp(accent, deep, 0.86f),
            lerp(accent, deep, 0.48f),
            lerp(accent, deep, 0.26f),
        )
    }

    return TextStyle(
        brush = Brush.verticalGradient(colors),
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        shadow = if (dark) {
            Shadow(
                color = accent.copy(alpha = 0.55f),
                offset = Offset(0f, 2f),
                blurRadius = 10f,
            )
        } else {
            Shadow(
                color = Color(0xFF0A2430).copy(alpha = 0.16f),
                offset = Offset(0f, 1.5f),
                blurRadius = 6f,
            )
        },
    )
}

@Composable
fun GradientTitle(
    text: String,
    accent: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = gradientTextStyle(accent, fontSize), modifier = modifier)
}
