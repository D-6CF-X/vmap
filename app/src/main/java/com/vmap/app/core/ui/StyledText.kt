package com.vmap.app.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * 霓虹渐变立体字：白→应援色垂直渐变（顶部反光、底部收暗），
 * 再加一层应援色发光阴影，比纯平面单色字更有质感、更醒目。
 */
fun gradientTextStyle(accent: Color, fontSize: TextUnit): TextStyle =
    TextStyle(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White,
                accent,
                accent.copy(alpha = 0.82f),
            ),
        ),
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        shadow = Shadow(
            color = accent.copy(alpha = 0.55f),
            offset = Offset(0f, 2f),
            blurRadius = 10f,
        ),
    )

@Composable
fun GradientTitle(
    text: String,
    accent: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = gradientTextStyle(accent, fontSize), modifier = modifier)
}
