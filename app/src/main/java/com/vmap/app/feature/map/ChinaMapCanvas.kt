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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.vmap.app.core.map.ChinaMapData
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import kotlin.math.hypot
import kotlin.math.min

/** 地图配色。白天 / 夜间各一套，应援色 [accent] 由板块决定、两种模式共用。 */
private data class MapPalette(
    val background: Color,
    val unlitCity: Color,
    val cityBorder: Color,
    val provinceBound: Color,
    val label: Color,
    val shadow: Color,
)

private val DarkPalette = MapPalette(
    background = Color(0xFF0F1115),
    unlitCity = Color(0xFF23272F),
    cityBorder = Color(0xFF10131A),
    provinceBound = Color(0xFF39404D),
    label = Color(0xFF5A6170),
    shadow = Color(0x55000000),
)

private val LightPalette = MapPalette(
    background = Color(0xFFF5F6F8),
    unlitCity = Color(0xFFE7EBF0),
    cityBorder = Color(0xFFD9DEE5),
    provinceBound = Color(0xFFA9B1BD),
    label = Color(0xFF6B7480),
    shadow = Color(0x1A000000),
)

/** 缩放超过这个值才显示地级市边界与未点亮城市名，避免全国视图糊成一团。 */
private const val DETAIL_ZOOM = 2f

/**
 * 自绘中国地图。去过城市整块填应援色，点击城市有选中高亮，路线为 3D 抛物线航线。
 *
 * 信息分层：全国视图只显示省级边界、省名、点亮城市；放大到 [DETAIL_ZOOM] 以上
 * 才展开地级市边界与未点亮城市名，保持缩小态干净。
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
    isDark: Boolean = true,
) {
    val context = LocalContext.current
    val provinces = remember { ChinaMapData.load(context) }
    val provinceBounds = remember { ChinaMapData.loadProvinceBounds(context) }
    val provinceBBoxes = remember(provinceBounds) {
        provinceBounds.associate { it.adcode to it.bounds }
    }
    val textMeasurer = rememberTextMeasurer()
    val accent = section.accent
    val palette = if (isDark) DarkPalette else LightPalette

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

    // 省级名字（全国视图只显示省名用）
    val provinceLabels = remember { ChinaMapData.loadProvinceLabels(context) }
    val provinceLayouts = remember(provinceLabels) {
        provinceLabels.associate { label ->
            label.name to textMeasurer.measure(
                text = AnnotatedString(label.name),
                style = TextStyle(fontSize = 13.sp),
                constraints = Constraints(maxWidth = 300),
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

        // 背景
        drawRect(palette.background)

        // 城市地块填充：点亮填应援色，未点亮填表面色
        provinces.forEach { p ->
            val visited = visitedAdcodes.contains(p.adcode)
            val fill = if (visited) accent else palette.unlitCity
            withTransform({
                translate(dx, dy)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawPath(p.path, color = fill)
                if (p.adcode == selectedAdcode) {
                    drawPath(p.path, color = accent, style = Stroke(width = 2.5f / k))
                }
            }
        }

        // 地级市边界：放大后才画，缩小态只保留省级边界
        if (scale >= DETAIL_ZOOM) {
            provinces.forEach { p ->
                withTransform({
                    translate(dx, dy)
                    scale(k, k, pivot = Offset.Zero)
                }) {
                    drawPath(p.path, color = palette.cityBorder, style = Stroke(width = 1f / k))
                }
            }
        }

        // 省级边界：始终画，勾勒省轮廓
        provinceBounds.forEach { b ->
            withTransform({
                translate(dx, dy)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawPath(b.path, color = palette.provinceBound, style = Stroke(width = 1.4f / k))
            }
        }

        // 文字避让：已占用的屏幕矩形，重叠的名字跳过（学高德的标注避让）
        val occupied = ArrayList<Rect>()

        fun tryPlace(
            layout: TextLayoutResult,
            labelV: Offset,
            color: Color,
            bbox: Rect,
            minRatio: Float,
        ): Boolean {
            val tl = toScreen(bbox.left, bbox.top)
            val br = toScreen(bbox.right, bbox.bottom)
            val tw = layout.size.width.toFloat()
            val th = layout.size.height.toFloat()
            if (br.x - tl.x < tw * minRatio || br.y - tl.y < th * 1.6f) return false
            val pad = 3f
            val c = toScreen(labelV.x, labelV.y)
            // 候选位置：质心优先，其次上下左右微调，学高德的多候选避让，尽量容纳更多名字
            val step = th * 0.5f
            val candidates = listOf(
                c,
                Offset(c.x, c.y - step),
                Offset(c.x, c.y + step),
                Offset(c.x - step, c.y),
                Offset(c.x + step, c.y),
            )
            for (cc in candidates) {
                val x = (cc.x - tw / 2f).coerceIn(tl.x, (br.x - tw).coerceAtLeast(tl.x))
                val y = (cc.y - th / 2f).coerceIn(tl.y, (br.y - th).coerceAtLeast(tl.y))
                val rect = Rect(x - pad, y - pad, x + tw + pad, y + th + pad)
                if (occupied.none { it.overlaps(rect) }) {
                    occupied.add(rect)
                    drawText(layout, color = color, topLeft = Offset(x, y))
                    return true
                }
            }
            return false
        }

        // 标注顺序：点亮城市 > 省名（按面积降序）> 未点亮城市（按面积降序），重叠自动抽稀
        provinces.filter { visitedAdcodes.contains(it.adcode) }
            .sortedByDescending { it.bounds.width * it.bounds.height }
            .forEach { p ->
                val layout = labelLayouts[p.adcode] ?: return@forEach
                tryPlace(layout, p.label, accent, p.bounds, 1.05f)
            }

        provinceLabels
            .sortedByDescending { provinceBBoxes[it.adcode]?.let { r -> r.width * r.height } ?: 0f }
            .forEach { label ->
                val layout = provinceLayouts[label.name] ?: return@forEach
                val bbox = provinceBBoxes[label.adcode] ?: return@forEach
                tryPlace(layout, Offset(label.x, label.y), palette.label, bbox, 1.05f)
            }

        if (scale >= DETAIL_ZOOM) {
            provinces.filter { !visitedAdcodes.contains(it.adcode) }
                .sortedByDescending { it.bounds.width * it.bounds.height }
                .forEach { p ->
                    val layout = labelLayouts[p.adcode] ?: return@forEach
                    tryPlace(layout, p.label, palette.label.copy(alpha = 0.7f), p.bounds, 1.45f)
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

            drawCircle(palette.shadow, radius = 11f, center = pos + Offset(0f, 3f))
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
