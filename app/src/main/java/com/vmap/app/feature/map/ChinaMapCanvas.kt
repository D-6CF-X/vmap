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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
    background = Color(0xFFEFF3F8),
    unlitCity = Color(0xFFE4EAF2),
    cityBorder = Color(0xFFD6DEE8),
    provinceBound = Color(0xFFAEB9C6),
    label = Color(0xFF68758A),
    shadow = Color(0x14000000),
)

/** 日夜切换时按进度混合两套配色，切换过程是渐变而不是闪一下。 */
private fun blendPalette(light: MapPalette, dark: MapPalette, t: Float) = MapPalette(
    background = lerp(light.background, dark.background, t),
    unlitCity = lerp(light.unlitCity, dark.unlitCity, t),
    cityBorder = lerp(light.cityBorder, dark.cityBorder, t),
    provinceBound = lerp(light.provinceBound, dark.provinceBound, t),
    label = lerp(light.label, dark.label, t),
    shadow = lerp(light.shadow, dark.shadow, t),
)

/** 缩放低于此值显示省名、高于此值显示城市名（互斥，学高德：缩小看省、放大看城）。 */
private const val DETAIL_ZOOM = 2.2f

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
    accent: Color,
    mixed: Boolean = false,
    mixProgress: Float = 0f,
    showRoute: Boolean,
    onSpotClick: (Spot) -> Unit,
    animated: Boolean = true,
    isDark: Boolean = true,
    darkProgress: Float = if (isDark) 1f else 0f,
) {
    val context = LocalContext.current
    val provinces = remember { ChinaMapData.load(context) }
    val provinceBounds = remember { ChinaMapData.loadProvinceBounds(context) }
    val provinceBBoxes = remember(provinceBounds) {
        provinceBounds.associate { it.adcode to it.bounds }
    }
    val textMeasurer = rememberTextMeasurer()
    val palette = when {
        darkProgress >= 0.999f -> DarkPalette
        darkProgress <= 0.001f -> LightPalette
        else -> blendPalette(LightPalette, DarkPalette, darkProgress)
    }

    val visitedAdcodes = remember(spots) {
        spots.map { it.adcode }.toSet()
    }

    // 综合模式下每个城市按自己归属的板块上色；同城两板块都有就用两色的中间值
    val citySections = remember(spots) {
        spots.groupBy { it.adcode }.mapValues { (_, v) -> v.map { s -> s.section }.distinct() }
    }

    /** 单个城市的点亮色：单板块模式就是应援色，综合模式按归属混合。 */
    fun cityAccent(adcode: String): Color {
        if (!mixed) return accent
        val secs = citySections[adcode].orEmpty()
        val own = when {
            secs.isEmpty() -> accent
            secs.size > 1 -> lerp(Section.TIANYI.accent, Section.MIKU.accent, 0.5f)
            else -> secs.first().accent
        }
        return lerp(accent, own, mixProgress)
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

    // 所有城市名预测量缓存（字号小，学高德不抢戏）
    val labelLayouts = remember(provinces) {
        provinces.associate { p ->
            p.adcode to textMeasurer.measure(
                text = AnnotatedString(p.name),
                style = TextStyle(fontSize = 9.sp),
                constraints = Constraints(maxWidth = 200),
            )
        }
    }

    // 点亮城市名：比普通标注更大更粗，填充渐变在绘制时按字高生成（见 tryPlace 的 emboss 分支）
    val litLabelLayouts = remember(provinces) {
        provinces.associate { p ->
            p.adcode to textMeasurer.measure(
                text = AnnotatedString(p.name),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                ),
                constraints = Constraints(maxWidth = 220),
            )
        }
    }

    // 省级名字（全国视图只显示省名用）
    val provinceLabels = remember { ChinaMapData.loadProvinceLabels(context) }
    val provinceLayouts = remember(provinceLabels) {
        provinceLabels.associate { label ->
            label.name to textMeasurer.measure(
                text = AnnotatedString(label.name),
                style = TextStyle(fontSize = 11.sp),
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

        // 城市地块填充：点亮填该城市归属板块的应援色，未点亮填表面色
        // 日间应援色在浅灰地图上偏淡，压深一档才看得出来被点亮
        fun litFill(color: Color) = lerp(lerp(color, Color(0xFF08161E), 0.20f), color, darkProgress)
        fun litEdge(color: Color) = lerp(
            lerp(color, Color(0xFF08161E), 0.42f),
            lerp(color, Color.White, 0.58f),
            darkProgress,
        )
        provinces.forEach { p ->
            val visited = visitedAdcodes.contains(p.adcode)
            val cityColor = if (visited) cityAccent(p.adcode) else palette.unlitCity
            val fill = if (visited) litFill(cityColor) else palette.unlitCity
            withTransform({
                translate(dx, dy)
                scale(k, k, pivot = Offset.Zero)
            }) {
                drawPath(p.path, color = fill)
                if (visited) {
                    // 亮边：点亮地块像一块有厚度的亚克力，而不是扁平色块
                    drawPath(
                        p.path,
                        color = litEdge(cityColor).copy(alpha = 0.55f),
                        style = Stroke(width = 1.1f / k),
                    )
                }
                if (p.adcode == selectedAdcode) {
                    drawPath(
                        p.path,
                        color = if (visited) cityColor else accent,
                        style = Stroke(width = 2.5f / k),
                    )
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

        /** 放不下就放弃——宁可少一个名字，也不让标注叠成一坨。 */
        fun tryPlace(
            layout: TextLayoutResult,
            labelV: Offset,
            color: Color,
            bbox: Rect,
            minRatio: Float,
            outline: Color? = null,
            shadow: Shadow? = null,
            emboss: Emboss? = null,
        ): Boolean {
            val tw = layout.size.width.toFloat()
            val th = layout.size.height.toFloat()
            val c = toScreen(labelV.x, labelV.y)

            val x: Float
            val y: Float
            if (emboss != null) {
                // 点亮城市名只用质心一个位置：允许轻微溢出地块（小地块也认得出是哪个市），
                // 但绝不换位置硬塞，撞上别的标注整条不画。
                x = (c.x - tw / 2f).coerceIn(6f, (size.width - tw - 6f).coerceAtLeast(6f))
                y = (c.y - th / 2f).coerceIn(6f, (size.height - th - 6f).coerceAtLeast(6f))
            } else {
                val tl = toScreen(bbox.left, bbox.top)
                val br = toScreen(bbox.right, bbox.bottom)
                if (br.x - tl.x < tw * minRatio || br.y - tl.y < th * 1.8f) return false
                x = (c.x - tw / 2f).coerceIn(tl.x, (br.x - tw).coerceAtLeast(tl.x))
                y = (c.y - th / 2f).coerceIn(tl.y, (br.y - th).coerceAtLeast(tl.y))
            }
            val pad = if (emboss != null) 2f else 7f
            val rect = Rect(x - pad, y - pad, x + tw + pad, y + th + pad)
            if (occupied.any { it.overlaps(rect) }) return false
            occupied.add(rect)

            if (emboss != null) {
                // 立体招牌字：外发光 → 挤出厚度（向下堆叠）→ 渐变字面 → 顶部高光
                // 日间的字是深色的，外发光要收着，否则 10sp 的小字会被糊成一团
                drawText(
                    layout,
                    color = emboss.glow.copy(alpha = 0.09f + 0.17f * darkProgress),
                    topLeft = Offset(x, y),
                    shadow = Shadow(
                        emboss.glow.copy(alpha = 0.30f + 0.45f * darkProgress),
                        Offset(0f, 1f),
                        2.5f + 3.5f * darkProgress,
                    ),
                )
                val step = (th / 26f).coerceIn(0.8f, 1.8f)
                for (i in 2 downTo 1) {
                    drawText(
                        layout,
                        color = emboss.depth.copy(alpha = 0.5f + 0.5f * darkProgress),
                        topLeft = Offset(x, y + step * i),
                    )
                }
                drawText(
                    layout,
                    brush = Brush.verticalGradient(emboss.fillColors, startY = 0f, endY = th),
                    topLeft = Offset(x, y),
                )
                drawText(
                    layout,
                    brush = Brush.verticalGradient(
                        // 日间的字是深蓝的，白色高光会把字糊灰，几乎关掉
                        0f to Color.White.copy(alpha = 0.04f + 0.20f * darkProgress),
                        0.5f to Color.Transparent,
                        startY = 0f,
                        endY = th,
                    ),
                    topLeft = Offset(x, y),
                )
                return true
            }
            // 描边层（白边/深边），让应援色文字在复杂背景上更清晰
            if (outline != null) {
                drawText(layout, color = outline, topLeft = Offset(x, y), drawStyle = Stroke(width = 3f))
            }
            drawText(layout, color = color, topLeft = Offset(x, y), shadow = shadow)
            return true
        }

        // 学高德：省名和城市名互斥——缩小看省，放大看城。
        // 点亮城市名：夜间是白→应援色的招牌字，日间换成深蓝字面（白字压在浅色地块上会消失），
        // 两套按主题进度插值；综合模式下每座城市用自己板块的颜色。
        fun embossFor(color: Color): Emboss {
            val nightFill = listOf(
                Color(0xFFFBFEFF),
                lerp(color, Color.White, 0.78f),
                lerp(color, Color.White, 0.34f),
                color,
            )
            val dayFill = listOf(
                lerp(color, Color(0xFF08161E), 0.88f),
                lerp(color, Color(0xFF08161E), 0.62f),
                lerp(color, Color(0xFF08161E), 0.30f),
                lerp(color, Color(0xFF08161E), 0.16f),
            )
            return Emboss(
                fillColors = nightFill.indices.map { i -> lerp(dayFill[i], nightFill[i], darkProgress) },
                depth = lerp(
                    lerp(color, Color.Black, 0.62f),
                    lerp(color, Color(0xFF06131C), 0.74f),
                    darkProgress,
                ),
                // 夜间是应援色外发光，日间换成深蓝的"投影描边"，浅底上才立得住
                glow = lerp(
                    lerp(color, Color(0xFF0B1F2B), 0.70f),
                    lerp(color, Color.White, 0.22f),
                    darkProgress,
                ),
            )
        }

        val plainShadow = Shadow(
            color = Color.Black.copy(alpha = 0.25f + 0.25f * darkProgress),
            offset = Offset(0f, 1.5f),
            blurRadius = 4f,
        )
        provinces.filter { visitedAdcodes.contains(it.adcode) }
            .sortedByDescending { it.bounds.width * it.bounds.height }
            .forEach { p ->
                val layout = litLabelLayouts[p.adcode] ?: return@forEach
                val color = cityAccent(p.adcode)
                tryPlace(layout, p.label, color, p.bounds, 1f, emboss = embossFor(color))
            }

        if (scale < DETAIL_ZOOM) {
            provinceLabels
                .sortedByDescending { provinceBBoxes[it.adcode]?.let { r -> r.width * r.height } ?: 0f }
                .forEach { label ->
                    val layout = provinceLayouts[label.name] ?: return@forEach
                    val bbox = provinceBBoxes[label.adcode] ?: return@forEach
                    tryPlace(layout, Offset(label.x, label.y), palette.label, bbox, 1.2f, shadow = plainShadow)
                }
        } else {
            // 放大态的城市名限量显示：按面积排重要度，只画前 N 个，放得越大画得越多。
            // 全画出来会糊成一片，学高德宁缺毋滥。
            val limit = (20 + (scale - DETAIL_ZOOM) * 12f).toInt().coerceIn(20, 70)
            provinces.filter { !visitedAdcodes.contains(it.adcode) }
                .sortedByDescending { it.bounds.width * it.bounds.height }
                .take(limit)
                .forEach { p ->
                    val layout = labelLayouts[p.adcode] ?: return@forEach
                    tryPlace(layout, p.label, palette.label.copy(alpha = 0.55f), p.bounds, 1.7f, shadow = plainShadow)
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

                // 综合模式下每条航段用起点城市的板块色，两块板各走各的线
                val seg = if (mixed) lerp(accent, from.section.accent, mixProgress) else accent

                drawLine(seg.copy(alpha = 0.15f), a, b, strokeWidth = 1.5f, cap = StrokeCap.Round)

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
                    drawLine(seg.copy(alpha = alpha), p0, p1, strokeWidth = width, cap = StrokeCap.Round)
                }

                val t = flow
                val pos = quadPoint(a, ctrl, b, t)
                for (k in 1..3) {
                    val tt = (t - k * 0.04f).coerceIn(0f, 1f)
                    val tail = quadPoint(a, ctrl, b, tt)
                    val fade = (1f - k * 0.25f).coerceAtLeast(0f)
                    drawCircle(seg.copy(alpha = 0.4f * fade), radius = 4f * (1f - k * 0.2f), center = tail)
                }
                drawCircle(seg.copy(alpha = 0.22f), radius = 9f, center = pos)
                drawCircle(Color.White.copy(alpha = 0.95f), radius = 3.5f, center = pos)
            }
        }

        // 点亮城市不再画圆点：靠地块填色 + 立体城市名表达，点位留给路线端点
    }
}

/**
 * 立体字的材质：字面渐变色、挤出厚度色（暗）、外发光色。
 * 具体渐变在绘制时按字高生成，避免渐变跨整块画布。
 */
private data class Emboss(
    val fillColors: List<Color>,
    val depth: Color,
    val glow: Color,
)

/** 二次贝塞尔曲线上的点。 */
private fun quadPoint(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2 * u * t * c.x + t * t * b.x,
        u * u * a.y + 2 * u * t * c.y + t * t * b.y,
    )
}
