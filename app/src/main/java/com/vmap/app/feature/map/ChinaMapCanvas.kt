package com.vmap.app.feature.map

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.vmap.app.core.map.ChinaMapData
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

private val UnlitProvince = Color(0xFF23272F)
private val ProvinceBorder = Color(0xFF0F1115)
private val LabelColor = Color(0xFFE6E8EE)

/** 阴影色，用在点、线下方制造立体感。 */
private val ShadowColor = Color(0x55000000)

/**
 * 自绘中国地图：去过省份整块填应援色，城市点带呼吸光晕与投影，
 * 路线为弧线，箭头沿路径流动。全部 Canvas 绘制，离线可用。
 */
@Composable
fun ChinaMapCanvas(
    modifier: Modifier,
    spots: List<Spot>,
    route: List<Spot>,
    section: Section,
    showRoute: Boolean,
    onSpotClick: (Spot) -> Unit,
) {
    val context = LocalContext.current
    val provinces = remember { ChinaMapData.load(context) }
    val textMeasurer = rememberTextMeasurer()
    val accent = section.accent

    val visitedPrefixes = remember(spots) {
        spots.map { it.adcode.take(2) }.toSet()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // 单个动画时钟，同时驱动路线流动和城市呼吸，相位错开
    val transition = rememberInfiniteTransition(label = "map")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flow",
    )

    val labelStyle = TextStyle(color = LabelColor, fontSize = 13.sp)

    fun spotVirtual(spot: Spot): Offset = Offset(
        ChinaMapData.lngToX(spot.point.asWgs84().lng),
        ChinaMapData.latToY(spot.point.asWgs84().lat),
    )

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 10f)
                    offset += pan
                }
            }
            .pointerInput(spots, scale, offset) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val base = min(w / ChinaMapData.VW, h / ChinaMapData.VH)
                    val dx = (w - ChinaMapData.VW * base) / 2 + offset.x
                    val dy = (h - ChinaMapData.VH * base) / 2 + offset.y
                    val k = base * scale
                    val hit = spots.minByOrNull { spot ->
                        val v = spotVirtual(spot)
                        hypot(v.x * k + dx - tap.x, v.y * k + dy - tap.y)
                    }
                    if (hit != null) {
                        val v = spotVirtual(hit)
                        if (hypot(v.x * k + dx - tap.x, v.y * k + dy - tap.y) < 60f) {
                            onSpotClick(hit)
                        }
                    }
                }
            },
    ) {
        val base = min(size.width / ChinaMapData.VW, size.height / ChinaMapData.VH)
        val dx = (size.width - ChinaMapData.VW * base) / 2 + offset.x
        val dy = (size.height - ChinaMapData.VH * base) / 2 + offset.y
        val k = base * scale

        fun toScreen(vx: Float, vy: Float) = Offset(vx * k + dx, vy * k + dy)

        // 省份：点亮填应援色，未点亮填表面色
        provinces.forEach { p ->
            val visited = visitedPrefixes.contains(p.adcode.take(2))
            val fill = if (visited) accent else UnlitProvince
            p.paths.forEach { path ->
                withTransform({
                    translate(dx, dy)
                    scale(k, k, pivot = Offset.Zero)
                }) {
                    drawPath(path, color = fill)
                    drawPath(path, color = ProvinceBorder, style = Stroke(width = 1f / k))
                }
            }
        }

        // 路线：3D 抛物线（向上拱、中间粗亮两端细淡、地面投影、飞行光点拖尾）
        if (showRoute && route.size >= 2) {
            route.zipWithNext { from, to ->
                val a = toScreen(spotVirtual(from).x, spotVirtual(from).y)
                val b = toScreen(spotVirtual(to).x, spotVirtual(to).y)
                val ddx = b.x - a.x
                val ddy = b.y - a.y
                val len = hypot(ddx, ddy).takeIf { it > 0f } ?: return@zipWithNext

                // 控制点：统一向上拱（负 y），拱高随距离，模拟航线抬升
                var nx = -ddy / len
                var ny = ddx / len
                if (ny > 0) {
                    nx = -nx
                    ny = -ny
                }
                val height = len * 0.28f
                val ctrl = Offset((a.x + b.x) / 2 + nx * height, (a.y + b.y) / 2 + ny * height)

                // 地面投影：两点间一条淡线，制造「抛物线浮在空中」的高度差
                drawLine(accent.copy(alpha = 0.15f), a, b, strokeWidth = 1.5f, cap = StrokeCap.Round)

                // 分段画抛物线：越靠近弧顶越粗越亮，模拟 3D 纵深
                val segments = 36
                for (i in 0 until segments) {
                    val t0 = i / segments.toFloat()
                    val t1 = (i + 1) / segments.toFloat()
                    val p0 = quadPoint(a, ctrl, b, t0)
                    val p1 = quadPoint(a, ctrl, b, t1)
                    val tm = (t0 + t1) / 2
                    val lift = 1f - kotlin.math.abs(tm - 0.5f) * 2f
                    val width = 0.7f + 4.5f * lift
                    val alpha = 0.30f + 0.70f * lift
                    drawLine(accent.copy(alpha = alpha), p0, p1, strokeWidth = width, cap = StrokeCap.Round)
                }

                // 飞行光点沿抛物线滑动，带拖尾
                val t = flow
                val pos = quadPoint(a, ctrl, b, t)
                for (k in 1..5) {
                    val tt = (t - k * 0.035f).coerceIn(0f, 1f)
                    val tail = quadPoint(a, ctrl, b, tt)
                    val fade = (1f - k * 0.18f).coerceAtLeast(0f)
                    drawCircle(accent.copy(alpha = 0.45f * fade), radius = 5f * (1f - k * 0.16f), center = tail)
                }
                drawCircle(accent.copy(alpha = 0.30f), radius = 10f, center = pos)
                drawCircle(Color.White.copy(alpha = 0.95f), radius = 4f, center = pos)
            }
        }

        // 城市点：投影 + 呼吸光晕 + 高光，制造立体感
        spots.forEachIndexed { index, spot ->
            val pos = toScreen(spotVirtual(spot).x, spotVirtual(spot).y)
            val pulse = (flow + index.toFloat() / (spots.size.coerceAtLeast(1))) % 1f

            // 投影
            drawCircle(ShadowColor, radius = 13f, center = pos + Offset(0f, 4f))
            // 呼吸光晕（放大淡出）
            drawCircle(
                accent.copy(alpha = 0.35f * (1f - pulse)),
                radius = 12f + 10f * pulse,
                center = pos,
            )
            // 主体
            drawCircle(Color.White, radius = 12f, center = pos)
            drawCircle(accent, radius = 9f, center = pos)
            // 左上高光，模拟球面反光
            drawCircle(Color.White.copy(alpha = 0.75f), radius = 3f, center = pos + Offset(-3f, -3f))
            // 城市名
            drawText(
                textMeasurer = textMeasurer,
                text = spot.cityName,
                topLeft = pos + Offset(16f, -12f),
                style = labelStyle,
            )
        }
    }
}

/** 二次贝塞尔曲线上的点。 */
private fun quadPoint(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2 * u * t * c.x + t * t * b.x,
        u * u * a.y + 2 * u * t * c.y + t * t * b.y,
    )
}
