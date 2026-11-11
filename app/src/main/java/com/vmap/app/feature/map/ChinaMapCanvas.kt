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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.vmap.app.core.audio.PianoTone
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
    /** 年份分层：地块按演出年份着色，越早越淡。 */
    yearLayers: Boolean = false,
    /** 只看某一年；null 表示全年份。 */
    focusYear: Int? = null,
    /** 巡演播放序列（按时间正序）。非空且 [tourPlaying] 为真时播放逐城点亮动画。 */
    tourSpots: List<Spot> = emptyList(),
    /** 是否正在播放巡演动画。 */
    tourPlaying: Boolean = false,
    /** 播放轮次：每次点年份递增，用来重启动画（点同一年也能重播）。 */
    tourRunId: Int = 0,
    /** 动画播放完毕回调（用于复位外部状态）。 */
    onTourEnd: () -> Unit = {},
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

    // 年份聚焦时，只把当年的记录当作"点亮"，其余视为未点亮
    val activeSpots = remember(spots, focusYear) {
        if (focusYear == null) spots else spots.filter { yearOf(it.performedAt) == focusYear }
    }
    val activeRoute = remember(route, focusYear) {
        if (focusYear == null) route else route.filter { yearOf(it.performedAt) == focusYear }
    }

    val visitedAdcodes = remember(activeSpots) {
        activeSpots.map { it.adcode }.filter { it.isNotBlank() }.toSet()
    }

    // 每个城市去过的年份（升序去重），决定地块的年份渐变。线上活动没有城市，排除。
    val cityYears = remember(activeSpots) {
        activeSpots.filter { it.adcode.isNotBlank() }
            .groupBy { it.adcode }
            .mapValues { (_, v) -> v.map { yearOf(it.performedAt) }.distinct().sorted() }
    }
    // 全局年份范围，用于把年份映射成深浅
    val yearMin = remember(activeSpots) { activeSpots.minOfOrNull { yearOf(it.performedAt) } ?: 0 }
    val yearMax = remember(activeSpots) { activeSpots.maxOfOrNull { yearOf(it.performedAt) } ?: 0 }

    /** 年份强度：最早 0（最淡），最近 1（最浓）。只有一年时给满。 */
    fun yearStrength(year: Int): Float {
        if (yearMax <= yearMin) return 1f
        return ((year - yearMin).toFloat() / (yearMax - yearMin)).coerceIn(0f, 1f)
    }

    // 综合模式下每个城市按自己归属的板块上色；同城两板块都有就用两色的中间值
    val citySections = remember(activeSpots) {
        activeSpots.filter { it.adcode.isNotBlank() }
            .groupBy { it.adcode }
            .mapValues { (_, v) -> v.map { s -> s.section }.distinct() }
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

    /**
     * 综合模式下的双色渐变：两个板块都去过的城市，从依你随行的天依蓝渐变到因音而遇的初音绿；
     * 只有一个板块的城市就用自己的应援色（渐变两端同色，等于纯色）。
     * 返回 null 表示走单色填充。
     */
    fun cityGradient(adcode: String): Pair<Color, Color>? {
        if (!mixed) return null
        val secs = citySections[adcode].orEmpty()
        val c1 = secs.getOrNull(0)?.accent ?: accent
        val c2 = secs.getOrNull(1)?.accent ?: c1
        return lerp(c1, accent, 1f - mixProgress) to lerp(c2, accent, 1f - mixProgress)
    }

    /** 城市的"两端色"：综合模式下就是天依蓝→初音绿，普通模式两端同色。 */
    fun cityColors(adcode: String): Pair<Color, Color> =
        cityGradient(adcode) ?: (cityAccent(adcode) to cityAccent(adcode))

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectedAdcode by remember { mutableStateOf<String?>(null) }

    // ===== 巡演点亮动画状态机 =====
    var tourPhase by remember { mutableStateOf<TourPhase>(TourPhase.Idle) }
    // 已完成点亮（burst+flash）的城市数；这些城市显示为实色
    var tourLitCount by remember { mutableIntStateOf(0) }

    // 钢琴音源：每点亮一座城弹一个音，逐级升高
    val piano = remember { PianoTone() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { piano.release() }
    }

    // 播放动画驱动：用 withFrameNanos 手动推进，每个阶段持续一段时间后切换
    LaunchedEffect(tourPlaying, tourSpots, tourRunId) {
        if (!tourPlaying || tourSpots.isEmpty()) {
            tourPhase = TourPhase.Idle
            tourLitCount = 0
            return@LaunchedEffect
        }
        tourPhase = TourPhase.Idle
        tourLitCount = 0

        val n = tourSpots.size
        var prevNanos = 0L
        // 阶段时长（毫秒）。飞行放慢一点，光点走得太快根本看不清轨迹
        val flyMs = 1500L
        val burstMs = 800L
        val flashMs = 450L
        val onlineMs = 1700L
        val rippleMs = 1900L

        fun playTone(step: Int) {
            // 钢琴音，逐城上行：第一座城 C4，往后一个个音往上爬
            runCatching { piano.playStep(step) }
        }

        /** 跑一个阶段到结束，[emit] 每帧收到 0..1 的进度。 */
        suspend fun runPhase(durationMs: Long, emit: (Float) -> Unit) {
            var t = 0f
            prevNanos = 0L
            emit(0f)
            while (t < 1f) {
                withFrameNanos { now ->
                    if (prevNanos == 0L) prevNanos = now
                    val dtMs = (now - prevNanos) / 1_000_000f
                    prevNanos = now
                    t = (t + dtMs / durationMs).coerceAtMost(1f)
                    emit(t)
                }
            }
        }

        // 逐场推进。线上活动没有城市：不飞、不点亮，只飘一条活动名。
        // 同一座城第二次出现不再重新扩散点亮，只是光点掠过一下，避免"重复点亮"。
        var lastRealIndex = -1
        val litCities = HashSet<String>()
        var toneStep = 0
        for (i in 0 until n) {
            val spot = tourSpots[i]
            if (spot.adcode.isBlank()) {
                runPhase(onlineMs) { t -> tourPhase = TourPhase.Online(i, t) }
                tourLitCount = i + 1
                continue
            }
            // FLY：从上一座已点亮的实体城市飞来（中间夹着线上活动也不影响连线）
            if (lastRealIndex >= 0) {
                runPhase(flyMs) { t -> tourPhase = TourPhase.Fly(lastRealIndex, i, t) }
            }
            val repeat = litCities.contains(spot.adcode)
            if (repeat) {
                // 去过第二次：光点掠过 + 轻轻一闪，不再整块扩散
                tourLitCount = i + 1
                playTone(toneStep++)
                runPhase(380L) { t -> tourPhase = TourPhase.Flash(i, t) }
            } else {
                // BURST：蒙版式扩散填满这座城市
                runPhase(burstMs) { t -> tourPhase = TourPhase.Burst(i, t) }
                // FLASH：高亮闪一下 + 音效
                tourLitCount = i + 1
                playTone(toneStep++)
                runPhase(flashMs) { t -> tourPhase = TourPhase.Flash(i, t) }
                litCities.add(spot.adcode)
            }
            lastRealIndex = i
        }

        // RIPPLE：整体波纹扩散
        runPhase(rippleMs) { t -> tourPhase = TourPhase.Ripple(t) }

        // 播放结束，保持全亮
        tourPhase = TourPhase.Idle
        tourLitCount = n
        onTourEnd()
    }

    // 动画时钟。animated=false 时不读动画值，Canvas 不会被每帧失效。
    // 整条路线走一趟 5.2s，慢一点才看得出光点在往哪走。
    val animatedFlow = rememberInfiniteTransition(label = "map").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "flow",
    )
    val flow = if (animated) animatedFlow.value else 0.5f

    // 所有城市名预测量缓存（字号小，学高德不抢戏）
    val labelLayouts = remember(provinces) {
        provinces.associate { p ->
            p.adcode to textMeasurer.measure(
                text = AnnotatedString(shortCityName(p.name)),
                style = TextStyle(fontSize = 9.sp),
                constraints = Constraints(maxWidth = 200),
            )
        }
    }

    // 点亮城市名：比普通标注更大更粗，填充渐变在绘制时按字高生成（见 tryPlace 的 emboss 分支）
    val litLabelLayouts = remember(provinces) {
        provinces.associate { p ->
            p.adcode to textMeasurer.measure(
                text = AnnotatedString(shortCityName(p.name)),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                ),
                constraints = Constraints(maxWidth = 220),
            )
        }
    }

    // 缩小态的城市名：小一号，但必须有——全国视野也能认出各座城市
    val smallCityLayouts = remember(provinces) {
        provinces.associate { p ->
            p.adcode to textMeasurer.measure(
                text = AnnotatedString(shortCityName(p.name)),
                style = TextStyle(fontSize = 7.5.sp),
                constraints = Constraints(maxWidth = 200),
            )
        }
    }

    // 线上活动的名字（巡演时飘出来的那条），按场次预先量好
    val tourTitleLayouts = remember(tourSpots) {
        tourSpots.associate { s ->
            s.id to textMeasurer.measure(
                text = AnnotatedString(s.title),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                constraints = Constraints(maxWidth = 640),
            )
        }
    }
    // 「线上演出」小标签
    val onlineTagLayout = remember {
        textMeasurer.measure(
            text = AnnotatedString("线上演出"),
            style = TextStyle(fontSize = 9.sp, letterSpacing = 2.sp),
            constraints = Constraints(maxWidth = 200),
        )
    }

    // 省级名字（全国视图只显示省名用）
    val provinceLabels = remember { ChinaMapData.loadProvinceLabels(context) }
    val provinceLayouts = remember(provinceLabels) {
        provinceLabels.associate { label ->
            label.name to textMeasurer.measure(
                text = AnnotatedString(shortCityName(label.name)),
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
            .pointerInput(provinces, activeSpots, scale, offset) {
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
                        val spot = activeSpots.firstOrNull { it.adcode == hit.adcode }
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

        // 巡演播放时，点亮集合由动画进度决定（已完成 burst 的城市才点亮），路线也只画已走过的段
        val isTouring = tourPlaying && tourSpots.isNotEmpty()
        val litAdcodesNow: Set<String> = if (isTouring) {
            tourSpots.take(tourLitCount).map { it.adcode }.filter { it.isNotBlank() }.toSet()
        } else {
            visitedAdcodes
        }

        // 城市地块填充：点亮填该城市归属板块的应援色，未点亮填表面色
        // 日间应援色在浅灰地图上偏淡，压深一档才看得出来被点亮
        fun litFill(color: Color) = lerp(lerp(color, Color(0xFF08161E), 0.20f), color, darkProgress)
        fun litEdge(color: Color) = lerp(
            lerp(color, Color(0xFF08161E), 0.42f),
            lerp(color, Color.White, 0.58f),
            darkProgress,
        )
        /**
         * 年份色：用透明度表达年份，不往表面色里混灰。
         * 最浓就是应援色本身（alpha 1），最早 52% 半透——叠在深色底上自然变淡，而不是被灰色拉脏。
         */
        fun yearTint(color: Color, strength: Float): Color =
            litFill(color).copy(alpha = 0.52f + 0.48f * strength)

        provinces.forEach { p ->
            val visited = litAdcodesNow.contains(p.adcode)
            val cityColor = if (visited) cityAccent(p.adcode) else palette.unlitCity
            val years = cityYears[p.adcode].orEmpty()
            // 综合模式下，两板块都去过的城市从蓝渐变到绿（斜向，像一块双色亚克力）
            val grad = if (visited) cityGradient(p.adcode) else null
            val gradBrush = if (grad != null && grad.first != grad.second) {
                Brush.linearGradient(
                    colors = listOf(litFill(grad.first), litFill(grad.second)),
                    start = Offset(p.bounds.left, p.bounds.top),
                    end = Offset(p.bounds.right, p.bounds.bottom),
                )
            } else {
                null
            }
            withTransform({
                translate(dx, dy)
                scale(k, k, pivot = Offset.Zero)
            }) {
                when {
                    !visited -> drawPath(p.path, color = palette.unlitCity)

                    // 年份模式：地块用「最近一年」的浓度上色，历史年份的深浅交给城市上方的年份刻度点
                    yearLayers && gradBrush == null -> drawPath(
                        p.path,
                        color = yearTint(cityColor, years.lastOrNull()?.let { yearStrength(it) } ?: 1f),
                    )

                    gradBrush != null -> drawPath(
                        p.path,
                        brush = gradBrush,
                        alpha = if (yearLayers) {
                            0.52f + 0.48f * (years.lastOrNull()?.let { yearStrength(it) } ?: 1f)
                        } else {
                            1f
                        },
                    )

                    else -> drawPath(p.path, color = litFill(cityColor))
                }
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

        // 年份刻度：去过两年以上的城市，在质心上方排一排小点，越早越淡。
        // 地块本身只能填一个颜色，历史年份靠这排点表达——一眼就能看出「这座城市我去过哪几年」。
        if (yearLayers) {
            provinces.filter { visitedAdcodes.contains(it.adcode) }.forEach { p ->
                val ys = cityYears[p.adcode].orEmpty()
                if (ys.size < 2) return@forEach
                val c = toScreen(p.label.x, p.label.y)
                val gap = 7f
                val r = 2.8f
                val y = c.y - 24f
                val startX = c.x - (ys.size - 1) * gap / 2f
                ys.forEachIndexed { i, yv ->
                    val dotColor = lerp(
                        palette.unlitCity,
                        cityAccent(p.adcode),
                        0.40f + 0.60f * yearStrength(yv),
                    )
                    drawCircle(dotColor, radius = r, center = Offset(startX + i * gap, y))
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
            val pad = if (emboss != null) 2f else 7f
            val minX = 6f
            val maxX = (size.width - tw - 6f).coerceAtLeast(6f)
            val minY = 6f
            val maxY = (size.height - th - 6f).coerceAtLeast(6f)

            // 点亮城市名是"必须有"的信息：质心撞车时依次试上/下/左/右几个候选位，
            // 都放不下才放弃。之前的实现只试质心一个位置，上海这种挨着杭州的小地块名字就被整条丢了。
            val candidates: List<Offset> = if (emboss != null) {
                listOf(
                    Offset(0f, 0f),
                    Offset(0f, -th - 3f),
                    Offset(0f, th + 3f),
                    Offset(-tw * 0.55f, 0f),
                    Offset(tw * 0.55f, 0f),
                    Offset(0f, -th - 14f),
                    Offset(0f, th + 14f),
                    Offset(-tw * 0.95f, -th * 0.6f),
                    Offset(tw * 0.95f, -th * 0.6f),
                    Offset(-tw * 0.95f, th * 0.8f),
                    Offset(tw * 0.95f, th * 0.8f),
                )
            } else {
                listOf(Offset.Zero)
            }

            var placed: Rect? = null
            for (off in candidates) {
                val x: Float
                val y: Float
                if (emboss != null) {
                    x = (c.x + off.x - tw / 2f).coerceIn(minX, maxX)
                    y = (c.y + off.y - th / 2f).coerceIn(minY, maxY)
                } else {
                    val tl = toScreen(bbox.left, bbox.top)
                    val br = toScreen(bbox.right, bbox.bottom)
                    if (br.x - tl.x < tw * minRatio || br.y - tl.y < th * 1.8f) return false
                    x = (c.x - tw / 2f).coerceIn(tl.x, (br.x - tw).coerceAtLeast(tl.x))
                    y = (c.y - th / 2f).coerceIn(tl.y, (br.y - th).coerceAtLeast(tl.y))
                }
                val rect = Rect(x - pad, y - pad, x + tw + pad, y + th + pad)
                if (occupied.none { it.overlaps(rect) }) {
                    placed = rect
                    break
                }
            }
            if (placed == null) return false
            occupied.add(placed)
            val x = placed.left + pad
            val y = placed.top + pad

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
        fun embossFor(color: Color, color2: Color = color): Emboss {
            // 综合模式下名字本身也是双色：上半段偏天依蓝、下半段偏初音绿
            val nightFill = listOf(
                Color(0xFFFBFEFF),
                lerp(color, Color.White, 0.62f),
                lerp(color2, Color.White, 0.18f),
                color2,
            )
            val dayFill = listOf(
                lerp(color, Color(0xFF08161E), 0.82f),
                lerp(color, Color(0xFF08161E), 0.55f),
                lerp(color2, Color(0xFF08161E), 0.28f),
                lerp(color2, Color(0xFF08161E), 0.18f),
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
        provinces.filter { litAdcodesNow.contains(it.adcode) }
            .sortedByDescending { it.bounds.width * it.bounds.height }
            .forEach { p ->
                val layout = litLabelLayouts[p.adcode] ?: return@forEach
                val base = cityAccent(p.adcode)
                // 年份模式：名字也跟着年份变淡，早期城市不抢眼
                val color = if (yearLayers) {
                    val latest = cityYears[p.adcode]?.lastOrNull()
                    if (latest != null) {
                        lerp(lerp(base, palette.unlitCity, 0.40f), base, yearStrength(latest))
                    } else {
                        base
                    }
                } else {
                    base
                }
                val two = cityGradient(p.adcode)
                tryPlace(
                    layout,
                    p.label,
                    color,
                    p.bounds,
                    1f,
                    emboss = if (two != null) embossFor(two.first, two.second) else embossFor(color),
                )
            }

        // 城市名：任何时候都要有，只是大小和数量随缩放变化。
        // 缩小态用 7.5sp 小字、只画面积较大的城市；放大态换 9sp 并逐步放开数量。
        // 先画城市名（用户要的城市信息），省名只在缩得很小时补位，撞上城市名就让路。
        val cityLimit = (28 + scale * 22f).toInt().coerceIn(32, 110)
        val cityLayout = if (scale < DETAIL_ZOOM) smallCityLayouts else labelLayouts
        val cityMinRatio = if (scale < DETAIL_ZOOM) 1.0f else 1.7f
        provinces.filter { !litAdcodesNow.contains(it.adcode) }
            .sortedByDescending { it.bounds.width * it.bounds.height }
            .take(cityLimit)
            .forEach { p ->
                val layout = cityLayout[p.adcode] ?: return@forEach
                tryPlace(layout, p.label, palette.label.copy(alpha = 0.58f), p.bounds, cityMinRatio, shadow = plainShadow)
            }

        // 缩得很小时，省名作为更大的层级补在空位上
        if (scale < 1.45f) {
            provinceLabels
                .sortedByDescending { provinceBBoxes[it.adcode]?.let { r -> r.width * r.height } ?: 0f }
                .forEach { label ->
                    val layout = provinceLayouts[label.name] ?: return@forEach
                    val bbox = provinceBBoxes[label.adcode] ?: return@forEach
                    tryPlace(layout, Offset(label.x, label.y), palette.label, bbox, 1.2f, shadow = plainShadow)
                }
        }

        // 路线：3D 抛物线（向上拱、中间粗亮两端细淡、地面投影、飞行光点拖尾）
        // 巡演播放时默认路线不画，改由下面的动画绘制「已走过的段」
        // 线上活动没有坐标，连线时跳过，避免连到 (0,0)
        val routeStops = activeRoute.filter { it.adcode.isNotBlank() }
        if (showRoute && !isTouring && routeStops.size >= 2) {
            // 先把每一段的几何算出来：画线要用，下面「单个光点巡游整条路线」也要用
            val segs = ArrayList<RouteSeg>(routeStops.size - 1)
            for (i in 0 until routeStops.size - 1) {
                val from = routeStops[i]
                val to = routeStops[i + 1]
                val a = toScreen(spotVirtual(from).x, spotVirtual(from).y)
                val b = toScreen(spotVirtual(to).x, spotVirtual(to).y)
                val ddx = b.x - a.x
                val ddy = b.y - a.y
                val len = hypot(ddx, ddy)
                if (len <= 0.5f) continue
                var nx = -ddy / len
                var ny = ddx / len
                if (ny > 0) {
                    nx = -nx
                    ny = -ny
                }
                val height = len * 0.28f
                val ctrl = Offset((a.x + b.x) / 2 + nx * height, (a.y + b.y) / 2 + ny * height)
                // 综合模式下每段航线从起点城市的颜色渐变到终点城市的颜色（天依蓝 ↔ 初音绿）
                segs.add(
                    RouteSeg(a, ctrl, b, cityColors(from.adcode).first, cityColors(to.adcode).second, len),
                )
            }

            segs.forEach { s ->
                drawLine(s.c1.copy(alpha = 0.15f), s.a, s.b, strokeWidth = 1.5f, cap = StrokeCap.Round)
                val segments = 36
                for (i in 0 until segments) {
                    val t0 = i / segments.toFloat()
                    val t1 = (i + 1) / segments.toFloat()
                    val p0 = quadPoint(s.a, s.ctrl, s.b, t0)
                    val p1 = quadPoint(s.a, s.ctrl, s.b, t1)
                    val tm = (t0 + t1) / 2
                    val lift = 1f - kotlin.math.abs(tm - 0.5f) * 2f
                    val width = 0.7f + 4.5f * lift
                    val alpha = 0.30f + 0.70f * lift
                    drawLine(lerp(s.c1, s.c2, tm).copy(alpha = alpha), p0, p1, strokeWidth = width, cap = StrokeCap.Round)
                }
            }

            // 光点只有一个：沿整条路线按航段顺序走过去，不再「每段各一个点一起出发」。
            // 首尾各留一小段做淡入淡出，循环回到起点时不生硬。
            val totalLen = segs.sumOf { it.len.toDouble() }.toFloat()
            if (totalLen > 1f) {
                val head = flow
                val alpha = minOf(
                    (head / 0.07f).coerceIn(0f, 1f),
                    ((1f - head) / 0.10f).coerceIn(0f, 1f),
                )
                if (alpha > 0.01f) {
                    fun sample(dist: Float): Pair<Offset, Color>? {
                        var d = dist
                        for (s in segs) {
                            if (d <= s.len || s === segs.last()) {
                                val lt = (d / s.len).coerceIn(0f, 1f)
                                return quadPoint(s.a, s.ctrl, s.b, lt) to lerp(s.c1, s.c2, lt)
                            }
                            d -= s.len
                        }
                        return null
                    }
                    val d0 = head * totalLen
                    for (k in 1..3) {
                        val prev = sample(d0 - k * 15f) ?: continue
                        val fade = (1f - k * 0.26f).coerceAtLeast(0f)
                        drawCircle(prev.second.copy(alpha = 0.42f * fade * alpha), radius = 4.4f * fade, center = prev.first)
                    }
                    sample(d0)?.let { (pos, col) ->
                        drawCircle(col.copy(alpha = 0.24f * alpha), radius = 9f, center = pos)
                        drawCircle(Color.White.copy(alpha = 0.95f * alpha), radius = 3.5f, center = pos)
                    }
                }
            }
        }

        // ===== 巡演点亮动画绘制 =====
        if (isTouring) {
            // 屏幕坐标按城市算（线上活动没有坐标，不参与连线）
            fun spotPos(spot: Spot): Offset = toScreen(
                spotVirtual(spot).x,
                spotVirtual(spot).y,
            )

            // 1) 已走过的段：只连「已点亮的实体城市」，按时间顺序 1→2、2→3 串起来
            val litReal = tourSpots.take(tourLitCount).filter { it.adcode.isNotBlank() }
            for (i in 0 until litReal.size - 1) {
                drawTourArc(
                    spotPos(litReal[i]),
                    spotPos(litReal[i + 1]),
                    cityColors(litReal[i].adcode).first,
                    alpha = 0.40f,
                    color2 = cityColors(litReal[i + 1].adcode).second,
                )
            }

            val phase = tourPhase

            // 2) 飞行中的段：拖尾从光点往后逐渐变淡，远处的轨迹沉成已走过段的淡色，
            //    所以不会出现「线一下全亮、到站一下全删」的突兀感。
            val flyFrom = (phase as? TourPhase.Fly)?.let { tourSpots.getOrNull(it.fromIndex) }
            val flyTo = (phase as? TourPhase.Fly)?.let { tourSpots.getOrNull(it.toIndex) }
            // 切换年份时旧阶段的索引可能已经越界，取不到就这帧不画
            if (phase is TourPhase.Fly && flyFrom != null && flyTo != null) {
                val a = spotPos(flyFrom)
                val b = spotPos(flyTo)
                val ddx = b.x - a.x
                val ddy = b.y - a.y
                val len = hypot(ddx, ddy)
                if (len > 1f) {
                    var nx = -ddy / len
                    var ny = ddx / len
                    if (ny > 0) { nx = -nx; ny = -ny }
                    val height = len * 0.28f
                    val ctrl = Offset((a.x + b.x) / 2 + nx * height, (a.y + b.y) / 2 + ny * height)
                    // 只画已经飞过的一段，并让亮度随「离光点多远」衰减
                    val segs = 44
                    val trailSpan = 0.55f
                    val amp = (len * 0.05f).coerceIn(3f, 30f)
                    for (i in 0 until segs) {
                        val t0 = i / segs.toFloat()
                        val t1 = (i + 1) / segs.toFloat()
                        if (t0 >= phase.t) break
                        val behind = phase.t - (t0 + t1) / 2f
                        // 越靠后越淡，但保留 0.42 的底色，到站后正好接上已走过段的淡线
                        val near = (1f - behind / trailSpan).coerceIn(0f, 1f)
                        val alpha = 0.42f + 0.55f * near
                        val width = 1.3f + 2.6f * near
                        val segColor = lerp(
                            cityColors(flyFrom.adcode).first,
                            cityColors(flyTo.adcode).second,
                            (t0 + t1) / 2f,
                        )
                        drawLine(
                            segColor.copy(alpha = alpha.coerceAtMost(0.97f)),
                            flightPoint(a, ctrl, b, t0, amp),
                            flightPoint(a, ctrl, b, t1, amp),
                            strokeWidth = width,
                            cap = StrokeCap.Round,
                        )
                    }
                    // 飞行光点：线上唯一的一个白芯光点（就是"3D立体箭头"，不带多余拖尾圆）
                    // 出发时淡入、到站前淡出，配合「一次只有一个点」——不会和上一程的点同时出现在地图上
                    val pos = flightPoint(a, ctrl, b, phase.t, amp)
                    val headAlpha = minOf(
                        (phase.t / 0.12f).coerceIn(0f, 1f),
                        ((1f - phase.t) / 0.14f).coerceIn(0f, 1f),
                    )
                    val headColor = lerp(
                        cityColors(flyFrom.adcode).first,
                        cityColors(flyTo.adcode).second,
                        phase.t,
                    )
                    if (headAlpha > 0.01f) {
                        drawCircle(headColor.copy(alpha = 0.32f * headAlpha), radius = 11f, center = pos)
                        drawCircle(Color.White.copy(alpha = 0.98f * headAlpha), radius = 4.5f, center = pos)
                    }
                }
            }

            // 3) Burst：蒙版式扩散——以城市地块路径为蒙版，点亮色从质心向外铺满整块城市
            if (phase is TourPhase.Burst) {
                val spot = tourSpots.getOrNull(phase.index)
                val province = spot?.let { s -> provinces.firstOrNull { it.adcode == s.adcode } }
                if (spot != null && province != null) {
                    // 城市质心用虚拟坐标，与 province.path 同系
                    val vc = spotVirtual(spot)
                    val halfW = (province.bounds.right - province.bounds.left) / 2f
                    val halfH = (province.bounds.bottom - province.bounds.top) / 2f
                    val coverR = hypot(halfW, halfH)
                    val r = (coverR * phase.t).coerceAtLeast(1f)
                    val fill = litFill(cityAccent(spot.adcode))
                    withTransform({
                        translate(dx, dy)
                        scale(k, k, pivot = Offset.Zero)
                    }) {
                        // 用城市路径裁剪，扩散圆只在城市范围内显形：颜色从中心一圈圈铺满整块地块
                        clipPath(province.path) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to fill,
                                    0.85f to fill,
                                    1f to fill.copy(alpha = 0.92f),
                                    center = vc,
                                    radius = r,
                                ),
                                radius = r,
                                center = vc,
                            )
                            // 扩散前沿的高亮环，标出正在覆盖的边界
                            drawCircle(
                                accent.copy(alpha = 0.85f * (1f - phase.t * 0.4f)),
                                radius = (r * 0.98f).coerceAtLeast(1f),
                                center = vc,
                                style = Stroke(width = 2.2f / k),
                            )
                        }
                    }
                }
            }

            // 3.5) 刚飞完的那一段：亮度从飞行态平滑沉到已走过段的淡色，不会一下被删掉
            if (phase is TourPhase.Burst || phase is TourPhase.Flash) {
                val idx = if (phase is TourPhase.Burst) phase.index else (phase as TourPhase.Flash).index
                val t = if (phase is TourPhase.Burst) phase.t * 0.5f else 0.5f + (phase as TourPhase.Flash).t * 0.5f
                val prevReal = tourSpots.take(idx).lastOrNull { it.adcode.isNotBlank() }
                val arrived = tourSpots.getOrNull(idx)
                if (prevReal != null && arrived != null) {
                    drawTourArc(
                        spotPos(prevReal),
                        spotPos(arrived),
                        cityColors(prevReal.adcode).first,
                        alpha = 0.92f + (0.40f - 0.92f) * t,
                        color2 = cityColors(arrived.adcode).second,
                    )
                }
            }

            // 4) Flash：到达后高亮闪一下
            if (phase is TourPhase.Flash) {
                val spot = tourSpots.getOrNull(phase.index)
                val province = spot?.let { s -> provinces.firstOrNull { it.adcode == s.adcode } }
                if (province != null) {
                    // 白色闪光，先亮后淡
                    val flashAlpha = (1f - phase.t) * 0.8f
                    withTransform({
                        translate(dx, dy)
                        scale(k, k, pivot = Offset.Zero)
                    }) {
                        drawPath(province.path, color = Color.White.copy(alpha = flashAlpha))
                        drawPath(
                            province.path,
                            color = accent.copy(alpha = 1f - phase.t),
                            style = Stroke(width = (2.5f + 2f * (1f - phase.t)) / k),
                        )
                    }
                }
            }

            // 4.5) 线上活动：没有实体城市，就在地图中央飘一条活动名，浮现—停留—淡出
            if (phase is TourPhase.Online) {
                val spot = tourSpots.getOrNull(phase.index)
                val layout = spot?.let { tourTitleLayouts[it.id] }
                if (layout != null) {
                    val appear = (phase.t / 0.20f).coerceIn(0f, 1f)
                    val vanish = ((1f - phase.t) / 0.30f).coerceIn(0f, 1f)
                    val alpha = appear * vanish
                    val rise = (1f - appear) * 14f - (1f - vanish) * 10f
                    val tw = layout.size.width.toFloat()
                    val th = layout.size.height.toFloat()
                    val cx = size.width / 2f
                    val cy = size.height * 0.44f + rise
                    val x = cx - tw / 2f
                    val y = cy - th / 2f
                    // 背后一层柔光，让白字压在复杂地图上也看得清
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to accent.copy(alpha = 0.30f * alpha),
                            1f to Color.Transparent,
                            center = Offset(cx, cy + th / 2),
                            radius = (tw * 0.9f).coerceAtLeast(24f),
                        ),
                        radius = (tw * 0.9f).coerceAtLeast(24f),
                        center = Offset(cx, cy + th / 2),
                    )
                    // 「线上演出」小标签，居中排在活动名上方
                    val tagLayout = onlineTagLayout
                    drawText(
                        tagLayout,
                        color = accent.copy(alpha = 0.85f * alpha),
                        topLeft = Offset(cx - tagLayout.size.width / 2f, y - tagLayout.size.height - 8f),
                    )
                    drawText(
                        layout,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f + 0.45f * darkProgress),
                                accent.copy(alpha = 1f),
                            ),
                            startY = y,
                            endY = y + th,
                        ),
                        topLeft = Offset(x, y),
                        alpha = alpha,
                        shadow = Shadow(
                            color = accent.copy(alpha = 0.75f * alpha),
                            offset = Offset(0f, 0f),
                            blurRadius = 12f,
                        ),
                    )
                }
            }

            // 5) Ripple：全部点亮后整体波纹扩散（线上活动没有坐标，不参与）
            if (phase is TourPhase.Ripple) {
                tourSpots.filter { it.adcode.isNotBlank() }.forEach { spot ->
                    val c = spotPos(spot)
                    val wave = phase.t
                    // 从每座城市发出的扩散波纹环
                    drawCircle(
                        accent.copy(alpha = (1f - wave) * 0.6f),
                        radius = 8f + wave * 90f,
                        center = c,
                        style = Stroke(width = 2.5f * (1f - wave * 0.5f)),
                    )
                    drawCircle(
                        accent.copy(alpha = (1f - wave) * 0.35f),
                        radius = 8f + wave * 55f,
                        center = c,
                        style = Stroke(width = 1.5f * (1f - wave * 0.5f)),
                    )
                }
            }
        }

        // 点亮城市不再画圆点：靠地块填色 + 立体城市名表达，点位留给路线端点
    }
}

/** 一段航线的绘制参数：起点、控制点、终点、两端颜色、长度（屏幕像素）。 */
private data class RouteSeg(
    val a: Offset,
    val ctrl: Offset,
    val b: Offset,
    val c1: Color,
    val c2: Color,
    val len: Float,
)

/** 画一条抛物线航段（用于巡演动画里「已走过的段」）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTourArc(
    a: Offset,
    b: Offset,
    color: Color,
    alpha: Float,
    color2: Color = color,
) {
    val ddx = b.x - a.x
    val ddy = b.y - a.y
    val len = hypot(ddx, ddy).takeIf { it > 0f } ?: return
    var nx = -ddy / len
    var ny = ddx / len
    if (ny > 0) { nx = -nx; ny = -ny }
    val height = len * 0.28f
    val ctrl = Offset((a.x + b.x) / 2 + nx * height, (a.y + b.y) / 2 + ny * height)
    val segments = 28
    val amp = (len * 0.03f).coerceIn(1.5f, 16f)
    for (i in 0 until segments) {
        val t0 = i / segments.toFloat()
        val t1 = (i + 1) / segments.toFloat()
        val p0 = flightPoint(a, ctrl, b, t0, amp)
        val p1 = flightPoint(a, ctrl, b, t1, amp)
        val tm = (t0 + t1) / 2
        val lift = 1f - kotlin.math.abs(tm - 0.5f) * 2f
        val width = 0.7f + 3.5f * lift
        val segAlpha = (0.30f + 0.70f * lift) * alpha
        drawLine(
            lerp(color, color2, tm).copy(alpha = segAlpha),
            p0,
            p1,
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 巡演点亮动画阶段：FLY(飞到下一城) → BURST(粒子扩散填城) → FLASH(高亮闪) → 下一城… → RIPPLE(整体波纹)。
 */
private sealed interface TourPhase {
    data object Idle : TourPhase
    data class Fly(val fromIndex: Int, val toIndex: Int, val t: Float) : TourPhase
    data class Burst(val index: Int, val t: Float) : TourPhase
    data class Flash(val index: Int, val t: Float) : TourPhase
    /** 线上活动：不点亮地块，只在地图中央把活动名浮现出来再淡掉。 */
    data class Online(val index: Int, val t: Float) : TourPhase
    data class Ripple(val t: Float) : TourPhase
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

/** 时间戳转年份。 */
private fun yearOf(timestamp: Long): Int {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = timestamp
    return cal.get(java.util.Calendar.YEAR)
}

/** 行政区后缀，地图标注上去掉——「巴音郭楞蒙古自治州」写成「巴音郭楞」才放得下。 */
private val CITY_SUFFIXES = listOf("特别行政区", "自治州", "自治县", "地区", "市", "盟", "县")

/** 自治州里的民族段，如「阿坝·藏族羌族·自治州」，截掉只剩地名。 */
private val ETHNIC_SEGMENTS = listOf(
    "藏族", "羌族", "土家族", "苗族", "彝族", "壮族", "布依族", "侗族", "哈尼族", "傣族",
    "白族", "景颇族", "傈僳族", "回族", "朝鲜族", "蒙古", "哈萨克", "柯尔克孜", "维吾尔",
    "锡伯", "裕固", "东乡", "保安", "撒拉", "土族", "达斡尔", "鄂温克", "鄂伦春", "满族",
    "瑶族", "畲族", "黎族", "仡佬族", "水族", "毛南族", "仫佬族", "京族", "普米族", "怒族",
    "独龙族", "阿昌族", "德昂族", "基诺族", "布朗族", "拉祜族", "佤族", "纳西族",
)

/** 城市标注用的短名：去后缀、去民族段。 */
private fun shortCityName(raw: String): String {
    var n = raw
    CITY_SUFFIXES.firstOrNull { n.endsWith(it) }?.let { n = n.dropLast(it.length) }
    ETHNIC_SEGMENTS.forEach { e ->
        val i = n.indexOf(e)
        if (i >= 1) n = n.substring(0, i)
    }
    return n.ifBlank { raw }
}

/**
 * 抛物线 + 不规则飘移：两条不同频率的正弦叠在一起，相位由端点坐标决定，
 * 所以每段航线摆动都不一样；两端用 sin(πt) 收敛回城市，不会飞到城外面。
 */
private fun flightPoint(a: Offset, ctrl: Offset, b: Offset, t: Float, amp: Float): Offset {
    val p = quadPoint(a, ctrl, b, t)
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = kotlin.math.hypot(dx, dy)
    if (len < 0.001f) return p
    val nx = -dy / len
    val ny = dx / len
    val seed = a.x * 0.013f + b.y * 0.017f
    val w = kotlin.math.sin(t * 5.9f + seed) * 0.68f +
        kotlin.math.sin(t * 11.7f + seed * 2.1f) * 0.32f
    val off = w * amp * kotlin.math.sin(Math.PI.toFloat() * t)
    return Offset(p.x + nx * off, p.y + ny * off)
}

/** 二次贝塞尔曲线上的点。 */
private fun quadPoint(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2 * u * t * c.x + t * t * b.x,
        u * u * a.y + 2 * u * t * c.y + t * t * b.y,
    )
}
