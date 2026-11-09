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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
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

/** 未点亮城市名的颜色（低饱和灰，和应援色区分）。 */
private val UnlitLabel = Color(0xFF5A6170)

/** 阴影色。 */
private val ShadowColor = Color(0x55000000)

/** 省会和计划单列市的 adcode。全国视图只显示这些 + 点亮城市的名字。 */
private val CAPITAL_ADCODES = setOf(
    "110000", "120000", "310000", "500000",
    "440100", "510100", "330100", "420100", "320100", "610100", "430100",
    "410100", "210100", "370100", "230100", "220100", "350100", "340100",
    "360100", "530100", "520100", "450100", "140100", "130100", "620100",
    "630100", "640100", "650100", "150100", "540100", "460100",
    "440300", "370200", "210200", "350200",
)

/**
 * 自绘中国地图。去过城市整块填应援色，所有城市名都显示（点亮的用应援色），
 * 点击任意城市地块有选中高亮。路线为 3D 抛物线航线，城市点带呼吸光晕。
 *
 * [animated] 为 false 时冻结动画——弹窗（详情/日期选择）盖在上面时传 false，
 * 避免后台地图每帧重绘拖慢对话框。
 */
@Composable
fun ChinaMapCanvas(
    modifier: Modifier,
    spots: List<Spot>,
    route: List<Spot>,
    section: Section,
    showRoute: Boolean,
    onSpotClick: (Spot) -> Unit,
    animated: Boolean = true,
) {
    val context = LocalContext.current
    val provinces = remember { ChinaMapData.load(context) }
    val textMeasurer = rememberTextMeasurer()
    val accent = section.accent

    val visitedAdcodes = remember(spots) {
        spots.map { it.adcode }.toSet()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectedAdcode by remember { mutableStateOf<String?>(null) }

    // 动画时钟。animated=false 时不读动画值，Canvas 不会被每帧失效。
    val animatedFlow = rememberInfiniteTransition(label = "map").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flow",
    )
    val flow = if (animated) animatedFlow.value else 0.5f

    // 所有城市名预测量缓存
    val labelLayouts = remember(provinces) {
        provinces.associate { p ->
            p.adcode to textMeasurer.measure(
                text = AnnotatedString(p.name),
                style = TextStyle(fontSize = 10.sp),
                constraints = Constraints(maxWidth = 200),
            )
        }
    }

    fun spotVirtual(spot: Spot): Offset = Offset(
        ChinaMapData.lngToX(spot.point.asWgs84().lng),
        ChinaMapData.latToY(spot.point.asWgs84().lat),
    )

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 10f)
                    val base = min(
                        size.width / ChinaMapData.VW,
                        size.height / ChinaMapData.VH,
                    )
                    val cx = (size.width - ChinaMapData.VW * base) / 2
                    val cy = (size.height - ChinaMapData.VH * base) / 2
                    val ratio = newScale / scale
                    if (ratio != 1f) {
                        offset = Offset(
                            centroid.x - cx - (centroid.x - cx - offset.x) * ratio,
                            centroid.y - cy - (centroid.y - cy - offset.y) * ratio,
                        )
                    }
                    offset += pan
                    scale = newScale
                }
            }
            .pointerInput(provinces, spots, scale, offset) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val base = min(w / ChinaMapData.VW, h / ChinaMapData.VH)
                    val dx = (w - ChinaMapData.VW * base) / 2 + offset.x
                    val dy = (h - ChinaMapData.VH * base) / 2 + offset.y
                    val k = base * scale
                    val vx = (tap.x - dx) / k
                    val vy = (tap.y - dy) / k

                    val hit = provinces.firstOrNull { it.contains(vx, vy) }
                    selectedAdcode = hit?.adcode
                    if (hit != null) {
                        val spot = spots.firstOrNull { it.adcode == hit.adcode }
                        if (spot != null) onSpotClick(spot)
                    }
                }
            },
    ) {
        val base = min(size.width / ChinaMapData.VW, size.height / ChinaMapData.VH)
        val dx = (size.width - ChinaMapData.VW * base) / 2 + offset.x
        val dy = (size.height - ChinaMapData.VH * base) / 2 + offset.y
        val k = base * scale

        fun toScreen(vx: Float, vy: Float) = Offset(vx * k + dx, vy * k + dy)

        // 城市地块：点亮填应援色，未点亮填表面色；选中的加高亮描边
        provinces.forEach { p ->
            val visited = visitedAdcodes.contains(p.adcode)
            val fill = if (visited) accent else UnlitProvince
            withTransform({
                translate(dx, dy)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawPath(p.path, color = fill)
                drawPath(p.path, color = ProvinceBorder, style = Stroke(width = 1f / k))
                if (p.adcode == selectedAdcode) {
                    drawPath(p.path, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 3f / k))
                }
            }
        }

        // 所有城市名：点亮用应援色，未点亮用低饱和灰。
        // 全国视图只显示点亮城市和省会（避免 370 个名字互相淹没），放大后展开全部。
        val showAll = scale >= 2f
        provinces.forEach { p ->
            val lit = visitedAdcodes.contains(p.adcode)
            if (!lit && !showAll && p.adcode !in CAPITAL_ADCODES) return@forEach
            val layout = labelLayouts[p.adcode] ?: return@forEach
            val c = toScreen(p.center.x, p.center.y)
            drawText(
                textLayoutResult = layout,
                color = when {
                    lit -> accent
                    showAll -> UnlitLabel.copy(alpha = 0.7f)
                    else -> UnlitLabel
                },
                topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f),
            )
        }

        // 路线：3D 抛物线（向上拱、中间粗亮两端细淡、地面投影、飞行光点拖尾）
        if (showRoute && route.size >= 2) {
            route.zipWithNext { from, to ->
                val a = toScreen(spotVirtual(from).x, spotVirtual(from).y)
                val b = toScreen(spotVirtual(to).x, spotVirtual(to).y)
                val ddx = b.x - a.x
                val ddy = b.y - a.y
                val len = hypot(ddx, ddy).takeIf { it > 0f } ?: return@zipWithNext

                var nx = -ddy / len
                var ny = ddx / len
                if (ny > 0) {
                    nx = -nx
                    ny = -ny
                }
                val height = len * 0.28f
                val ctrl = Offset((a.x + b.x) / 2 + nx * height, (a.y + b.y) / 2 + ny * height)

                drawLine(accent.copy(alpha = 0.15f), a, b, strokeWidth = 1.5f, cap = StrokeCap.Round)

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

                val t = flow
                val pos = quadPoint(a, ctrl, b, t)
                for (k in 1..3) {
                    val tt = (t - k * 0.04f).coerceIn(0f, 1f)
                    val tail = quadPoint(a, ctrl, b, tt)
                    val fade = (1f - k * 0.25f).coerceAtLeast(0f)
                    drawCircle(accent.copy(alpha = 0.4f * fade), radius = 4f * (1f - k * 0.2f), center = tail)
                }
                drawCircle(accent.copy(alpha = 0.22f), radius = 9f, center = pos)
                drawCircle(Color.White.copy(alpha = 0.95f), radius = 3.5f, center = pos)
            }
        }

        // 点亮城市的地标点：投影 + 呼吸光晕
        spots.forEachIndexed { index, spot ->
            val pos = toScreen(spotVirtual(spot).x, spotVirtual(spot).y)

            if (pos.x < -300f || pos.x > size.width + 300f ||
                pos.y < -300f || pos.y > size.height + 300f
            ) {
                return@forEachIndexed
            }

            val pulse = (flow + index.toFloat() / (spots.size.coerceAtLeast(1))) % 1f

            drawCircle(ShadowColor, radius = 11f, center = pos + Offset(0f, 3f))
            drawCircle(
                accent.copy(alpha = 0.22f * (1f - pulse)),
                radius = 10f + 7f * pulse,
                center = pos,
            )
            drawCircle(Color.White, radius = 10f, center = pos)
            drawCircle(accent, radius = 7.5f, center = pos)
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
