package com.vmap.app.feature.map

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

/** 未点亮省份的填充色（暗色主题下的表面色）。 */
private val UnlitProvince = Color(0xFF23272F)

/** 省界描边，用背景色制造分隔感。 */
private val ProvinceBorder = Color(0xFF0F1115)

/** 城市名文字颜色。 */
private val LabelColor = Color(0xFFE6E8EE)

/**
 * 自绘中国地图：省份按是否点亮填充应援色，城市点带名字，支持双指缩放和拖动。
 *
 * 不用任何地图 SDK 的原因：目标效果就是一张静态行政区图（区域点亮 + 城市标注），
 * 瓦片地图反而带来网络依赖、坐标系混用和 Android 16 的 so 兼容问题。
 * 一份 400KB 的边界数据 + Canvas，全离线、全可控。
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

        // 路线连线与方向箭头
        if (showRoute && route.size >= 2) {
            val arrow = Path().apply {
                moveTo(9f, 0f)
                lineTo(-9f, 6f)
                lineTo(-4f, 0f)
                lineTo(-9f, -6f)
                close()
            }
            route.zipWithNext { from, to ->
                val a = toScreen(spotVirtual(from).x, spotVirtual(from).y)
                val b = toScreen(spotVirtual(to).x, spotVirtual(to).y)
                drawLine(accent, a, b, strokeWidth = 3f)
                val mid = (a + b) / 2f
                val angle = Math.toDegrees(
                    atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()),
                ).toFloat()
                withTransform({
                    translate(mid.x, mid.y)
                    rotate(angle, pivot = Offset.Zero)
                }) {
                    drawPath(arrow, color = accent)
                }
            }
        }

        // 城市点 + 城市名
        spots.forEach { spot ->
            val pos = toScreen(spotVirtual(spot).x, spotVirtual(spot).y)
            drawCircle(Color.White, radius = 12f, center = pos)
            drawCircle(accent, radius = 9f, center = pos)
            drawText(
                textMeasurer = textMeasurer,
                text = spot.cityName,
                topLeft = pos + Offset(16f, -12f),
                style = labelStyle,
            )
        }
    }
}
