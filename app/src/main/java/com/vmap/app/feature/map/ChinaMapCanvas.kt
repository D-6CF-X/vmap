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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.vmap.app.core.audio.PianoTone
import com.vmap.app.core.map.ChinaMapData
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Section
import com.vmap.app.core.text.seriesOfEventName
import com.vmap.app.domain.model.Spot
import kotlinx.coroutines.launch
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
    /** 官方路线：历年档案里开过的场次，只铺一条淡线，不点亮。 */
    officialRoute: List<Spot> = emptyList(),
    /** 我的路线：我真的去过的地方，亮线 + 唯一的光点。 */
    myRoute: List<Spot> = emptyList(),
    routeMode: RouteMode = RouteMode.BOTH,
    accent: Color,
    mixed: Boolean = false,
    mixProgress: Float = 0f,
    onSpotClick: (Spot) -> Unit,
    animated: Boolean = true,
    isDark: Boolean = true,
    darkProgress: Float = if (isDark) 1f else 0f,
    /** 年份分层：地块按演出年份着色，越早越淡。 */
    yearLayers: Boolean = false,
    /** 只看某一年；null 表示全年份。 */
    focusYear: Int? = null,
    /** 官方巡演播放序列（按时间正序）：官方当年开过的场次。 */
    officialTour: List<Spot> = emptyList(),
    /** 我的巡演播放序列（按时间正序）：我当年真的去过的地方。 */
    mineTour: List<Spot> = emptyList(),
    /** 是否正在播放巡演动画（两条线同时开播，各走各的）。 */
    tourPlaying: Boolean = false,
    /** 播放轮次：每次点年份递增，用来重启动画（点同一年也能重播）。 */
    tourRunId: Int = 0,
    /** 两条线都播完的回调（用于复位外部状态）。 */
    onTourEnd: () -> Unit = {},
) {
    val context = LocalContext.current
    val provinces = remember { ChinaMapData.load(context) }
    val provinceBounds = remember { ChinaMapData.loadProvinceBounds(context) }
    val provinceBBoxes = remember(provinceBounds) {
        provinceBounds.associate { it.adcode to it.bounds }
    }
    val textMeasurer = rememberTextMeasurer()
    // 调色板只在主题过渡那 300ms 里变，平时缓存住，别每帧重建一次 data class
    val palette = remember(darkProgress) {
        when {
            darkProgress >= 0.999f -> DarkPalette
            darkProgress <= 0.001f -> LightPalette
            else -> blendPalette(LightPalette, DarkPalette, darkProgress)
        }
    }

    // 年份聚焦时，只把当年的记录当作"点亮"，其余视为未点亮
    val activeSpots = remember(spots, focusYear) {
        if (focusYear == null) spots else spots.filter { yearOf(it.performedAt) == focusYear }
    }
    // 两条线分开算：官方档案路线（淡）和我的路线（亮）
    val officialStops = remember(officialRoute, focusYear) {
        officialRoute
            .filter { it.adcode.isNotBlank() && (focusYear == null || yearOf(it.performedAt) == focusYear) }
            .sortedBy { it.performedAt }
    }
    val myStops = remember(myRoute, focusYear) {
        myRoute
            .filter { it.adcode.isNotBlank() && (focusYear == null || yearOf(it.performedAt) == focusYear) }
            .sortedBy { it.performedAt }
    }

    val visitedAdcodes = remember(activeSpots) {
        activeSpots.map { it.adcode }.filter { it.isNotBlank() }.toSet()
    }
    /** 我真的去过的城市：地块实色 + 立体城市名。 */
    val mineAdcodes = remember(activeSpots) {
        activeSpots.filter { it.mine }.map { it.adcode }.filter { it.isNotBlank() }.toSet()
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

    // ===== 巡演点亮动画状态机：官方线 / 我的线各一套，并行播放、互不打断 =====
    var officialPhase by remember { mutableStateOf<TourPhase>(TourPhase.Idle) }
    var officialLit by remember { mutableIntStateOf(0) }
    var minePhase by remember { mutableStateOf<TourPhase>(TourPhase.Idle) }
    var mineLit by remember { mutableIntStateOf(0) }

    // 换到新系列时弹出来的那条提示（系列名 + 起始时间，画的时候按时间算淡入淡出）
    var bannerText by remember { mutableStateOf("") }
    var bannerStart by remember { mutableLongStateOf(0L) }

    // 钢琴音源：每点亮一座城弹一个音，逐级升高
    val piano = remember { PianoTone() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { piano.release() }
    }

    /**
     * 播一条线：逐站 FLY（飞过去）→ BURST（扩散填满城市）→ FLASH（闪一下），末尾整体 RIPPLE。
     * [withSound] 为假时静音——两条线同时播，只让一条出声，不然两层钢琴会糊成一片。
     */
    suspend fun playTour(
        stops: List<Spot>,
        withSound: Boolean,
        setPhase: (TourPhase) -> Unit,
        setLit: (Int) -> Unit,
    ) {
        setPhase(TourPhase.Idle)
        setLit(0)
        val n = stops.size
        if (n == 0) return

        // 阶段时长（毫秒）。飞行 2s：一点就走，一年十几场也不会等太久；
        // Flash 留 0.9s，让到站后的「城市 + 演出名」有时间读完。
        val flyMs = 2000L
        val burstMs = 600L
        val flashMs = 900L
        val onlineMs = 1500L
        val rippleMs = 1400L

        var prevNanos = 0L
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
        var lastSeries = ""
        for (i in 0 until n) {
            val spot = stops[i]
            // 换系列了（比如从「流光协奏」走到「纯蓝幻乐」）：先弹一下系列名，
            // 只让官方线弹，我的线播的时候不重复
            if (withSound) {
                val series = seriesOfEventName(spot.title)
                if (series.isNotBlank() && series != lastSeries) {
                    lastSeries = series
                    bannerText = series
                    bannerStart = System.currentTimeMillis()
                }
            }
            if (spot.adcode.isBlank()) {
                runPhase(onlineMs) { t -> setPhase(TourPhase.Online(i, t)) }
                setLit(i + 1)
                continue
            }
            // FLY：从上一座已点亮的实体城市飞来（中间夹着线上活动也不影响连线）
            if (lastRealIndex >= 0) {
                runPhase(flyMs) { t -> setPhase(TourPhase.Fly(lastRealIndex, i, t)) }
            }
            val repeat = litCities.contains(spot.adcode)
            if (repeat) {
                // 去过第二次：光点掠过 + 轻轻一闪，不再整块扩散
                setLit(i + 1)
                if (withSound) runCatching { piano.playStep(toneStep++) }
                runPhase(400L) { t -> setPhase(TourPhase.Flash(i, t)) }
            } else {
                // BURST：蒙版式扩散填满这座城市
                runPhase(burstMs) { t -> setPhase(TourPhase.Burst(i, t)) }
                // FLASH：高亮闪一下 + 音效
                setLit(i + 1)
                if (withSound) runCatching { piano.playStep(toneStep++) }
                runPhase(flashMs) { t -> setPhase(TourPhase.Flash(i, t)) }
                litCities.add(spot.adcode)
            }
            lastRealIndex = i
        }

        // RIPPLE：整体波纹扩散
        runPhase(rippleMs) { t -> setPhase(TourPhase.Ripple(t)) }

        // 播放结束，保持全亮
        setPhase(TourPhase.Idle)
        setLit(n)
    }

    LaunchedEffect(tourPlaying, officialTour, mineTour, tourRunId) {
        if (!tourPlaying) {
            officialPhase = TourPhase.Idle
            minePhase = TourPhase.Idle
            officialLit = 0
            mineLit = 0
            return@LaunchedEffect
        }
        officialPhase = TourPhase.Idle
        minePhase = TourPhase.Idle
        officialLit = 0
        mineLit = 0

        // 两条线并行：官方那条走自己的站序，我的路线走自己的，互不抢占
        kotlinx.coroutines.coroutineScope {
            if (officialTour.isNotEmpty()) {
                launch {
                    playTour(
                        officialTour,
                        withSound = true,
                        setPhase = { officialPhase = it },
                        setLit = { officialLit = it },
                    )
                }
            }
            if (mineTour.isNotEmpty()) {
                launch {
                    playTour(
                        mineTour,
                        withSound = false,
                        setPhase = { minePhase = it },
                        setLit = { mineLit = it },
                    )
                }
            }
        }

        // 两条都播完才复位
        officialPhase = TourPhase.Idle
        minePhase = TourPhase.Idle
        onTourEnd()
    }

    // 静态光点的时钟：按屏幕刷新率（60fps）走，光点滑得顺。
    // 分组 / 变换 / 渐变的开销已经在前面砍掉了，直接每帧更新也扛得住。
    var flowTick by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val periodMs = 22_000L
        var startNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                flowTick = ((now - startNanos) / 1_000_000L % periodMs).toFloat() / periodMs
            }
        }
    }
    // 静态路线走一趟 22s：不快不慢，看得清它顺着哪条线往前挪
    val flow = if (animated) flowTick else 0.5f

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
    val tourTitleLayouts = remember(officialTour) {
        officialTour.associate { s ->
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
    // 换系列提示：系列名排版的缓存 + 上方的「新系列」小标
    val bannerLayouts = remember { HashMap<String, TextLayoutResult>() }
    val bannerTagLayout = remember {
        textMeasurer.measure(
            text = AnnotatedString("新 系 列"),
            style = TextStyle(fontSize = 9.sp, letterSpacing = 3.sp),
            constraints = Constraints(maxWidth = 240),
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

    // 日期标签的排版缓存（播放时每帧都要画，不能每帧重新 measure）
    val dateLayouts = remember { HashMap<String, TextLayoutResult>() }
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault()) }
    // 进度胶囊用短日期：年份在年份条上已经有了，胶囊里只留月日，两条线才不会挤成一坨
    val shortDateFormat = remember { java.text.SimpleDateFormat("MM.dd", java.util.Locale.getDefault()) }

    // 城市的虚拟坐标（墨卡托投影结果）缓存起来：GCJ02→WGS84 是三角函数，
    // 动画每帧要取几十次，算一次就够。
    val spotVirtuals = remember(officialTour, mineTour, officialRoute, myRoute) {
        val all = ArrayList<Spot>(officialTour.size + mineTour.size + officialRoute.size + myRoute.size)
        all.addAll(officialTour)
        all.addAll(mineTour)
        all.addAll(officialRoute)
        all.addAll(myRoute)
        all.distinctBy { it.id }.associate { s ->
            s.id to Offset(
                ChinaMapData.lngToX(s.point.asWgs84().lng),
                ChinaMapData.latToY(s.point.asWgs84().lat),
            )
        }
    }

    fun spotVirtual(spot: Spot): Offset = spotVirtuals[spot.id] ?: Offset(
        ChinaMapData.lngToX(spot.point.asWgs84().lng),
        ChinaMapData.latToY(spot.point.asWgs84().lat),
    )

    // ===== 巡演播放的派生状态，全部提到 Canvas 外面算 =====
    // 这些东西原来写在 onDraw 里，每帧都要重新跑一遍 filter + sort（370 个地块 × 2 次排序），
    // 播放动画时整块画布每帧重绘，光是这些集合运算就把主线程吃满了。
    // 提到 Composable 作用域后，只有「点亮的城市集合」真的变了才会重算。
    val isTouring = tourPlaying && (officialTour.isNotEmpty() || mineTour.isNotEmpty())
    val officialLitSet: Set<String> = remember(officialTour, officialLit, isTouring) {
        if (isTouring) {
            officialTour.take(officialLit).map { it.adcode }.filter { it.isNotBlank() }.toSet()
        } else {
            emptySet()
        }
    }
    val mineLitSet: Set<String> = remember(mineTour, mineLit, isTouring) {
        if (isTouring) {
            mineTour.take(mineLit).map { it.adcode }.filter { it.isNotBlank() }.toSet()
        } else {
            emptySet()
        }
    }
    val litAdcodesNow: Set<String> = remember(officialLitSet, mineLitSet, visitedAdcodes, isTouring) {
        if (isTouring) officialLitSet + mineLitSet else visitedAdcodes
    }
    /** 「我的」地块：播放时跟着我的路线走，静态时就是全部我去过的城市。 */
    val touringMine: Set<String> = if (isTouring) mineLitSet else mineAdcodes

    // 地块按面积从大到小排一次（省/市顺序固定），之后只按「点亮 / 未点亮」分组，不再每帧排序
    val provincesByArea = remember(provinces) {
        provinces.sortedByDescending { it.bounds.width * it.bounds.height }
    }
    // adcode → 地块：动画里每帧都要按城市找地块，别再线性搜 370 条
    val provinceByAdcode = remember(provinces) { provinces.associateBy { it.adcode } }
    val litCityProvinces = remember(provincesByArea, touringMine) {
        provincesByArea.filter { touringMine.contains(it.adcode) }
    }
    val plainCityProvinces = remember(provincesByArea, touringMine) {
        provincesByArea.filter { !touringMine.contains(it.adcode) }
    }

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

        fun dateLabelOf(spot: Spot): String = dateFormat.format(java.util.Date(spot.performedAt))

        /**
         * 日期小药丸：播放路线时标在光点 / 到站城市旁边，一眼看出这一站是哪天。
         * 深色底 + 细描边，白天夜里都读得清，不去抢城市名的风头。
         */
        fun drawDatePill(label: String, at: Offset, color: Color, alpha: Float, lift: Float = 0f) {
            if (alpha <= 0.02f) return
            val layout = dateLayouts.getOrPut(label) {
                textMeasurer.measure(
                    AnnotatedString(label),
                    style = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
                    // 进度胶囊要放「官方 2026.07.18 北京 3/10」，宽度给足才不会折成两行
                    constraints = Constraints(maxWidth = 420),
                )
            }
            val tw = layout.size.width.toFloat()
            val th = layout.size.height.toFloat()
            val padX = 5.5f
            val padY = 2.5f
            val w = tw + padX * 2
            val h = th + padY * 2
            var x = at.x - w / 2f
            var y = at.y - h - 6f - lift
            if (y < 4f) y = at.y + 8f + lift
            x = x.coerceIn(3f, (size.width - w - 3f).coerceAtLeast(3f))
            y = y.coerceIn(3f, (size.height - h - 3f).coerceAtLeast(3f))
            drawRoundRect(
                color = Color(0xFF0A1218).copy(alpha = 0.72f * alpha),
                topLeft = Offset(x, y),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
            )
            drawRoundRect(
                color = color.copy(alpha = 0.55f * alpha),
                topLeft = Offset(x, y),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f),
                style = Stroke(width = 1f),
            )
            drawText(layout, color = Color.White.copy(alpha = alpha), topLeft = Offset(x + padX, y + padY))
        }

        /**
         * 到站标签里的演出名：保留全名，只去掉和第一行重复的「· xx站」后缀。
         * 用户要看清这一场到底是什么演出，所以不再压缩成系列名。
         */
        fun eventTitleForPill(raw: String, city: String): String {
            var s = raw.trim()
            if (city.isNotBlank()) {
                // 注意用 ${city}：$city站 会被 Kotlin 当成变量名 city站
                s = s.replace("· ${city}站", "").replace("${city}站", "")
            }
            return s.trim().trim('·', ' ').ifBlank { raw }
        }

        /**
         * 到站标签：第一行城市（应援色）+ 日期，第二行演出全称（自动换行）。
         *
         * 框比原来大一圈、字号也放大一点——用户要能直接读出「这是哪一站、哪一场」。
         * [lift] 为负时标签挂在城市下方（两条线同一站时错开用）。
         */
        fun drawEventPill(
            spot: Spot,
            at: Offset,
            color: Color,
            alpha: Float,
            lift: Float,
            isMine: Boolean,
        ) {
            if (alpha <= 0.02f) return
            val city = spot.cityName.ifBlank { if (spot.adcode.isBlank()) "线上" else "—" }
            val date = shortDateFormat.format(java.util.Date(spot.performedAt))
            val full = eventTitleForPill(spot.title, spot.cityName)

            // 城市名用应援色、日期用白字，一眼看出是哪一站
            val headText = buildAnnotatedString {
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(city) }
                append("   ")
                withStyle(
                    SpanStyle(color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Medium),
                ) { append(date) }
            }
            val headLayout = dateLayouts.getOrPut("H2|$city|$date") {
                textMeasurer.measure(
                    headText,
                    style = TextStyle(fontSize = 11.sp, letterSpacing = 0.2.sp),
                    maxLines = 1,
                    constraints = Constraints(maxWidth = 560),
                )
            }
            // 我的线不重复写演出名（同一场），官方线才展开全称
            val subLayout = if (isMine || full.isBlank()) null else dateLayouts.getOrPut("S2|$full") {
                textMeasurer.measure(
                    AnnotatedString(full),
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal),
                    constraints = Constraints(maxWidth = 520),
                )
            }
            val padX = 10f
            val padY = 6f
            val gap = 2f
            val tw = maxOf(headLayout.size.width, subLayout?.size?.width ?: 0).toFloat()
            val th = headLayout.size.height + (subLayout?.let { it.size.height + gap } ?: 0f)
            val w = tw + padX * 2
            val h = th + padY * 2
            var x = at.x - w / 2f
            var y = if (lift >= 0f) at.y - h - 9f - lift else at.y + 10f - lift
            if (y < 4f) y = at.y + 10f
            x = x.coerceIn(3f, (size.width - w - 3f).coerceAtLeast(3f))
            y = y.coerceIn(3f, (size.height - h - 3f).coerceAtLeast(3f))
            drawRoundRect(
                color = Color(0xFF0A1218).copy(alpha = 0.86f * alpha),
                topLeft = Offset(x, y),
                size = Size(w, h),
                cornerRadius = CornerRadius(9f, 9f),
            )
            drawRoundRect(
                color = color.copy(alpha = 0.72f * alpha),
                topLeft = Offset(x, y),
                size = Size(w, h),
                cornerRadius = CornerRadius(9f, 9f),
                style = Stroke(width = 1.2f),
            )
            // 左侧应援色竖条：一眼分出这是官方（淡）还是我的（亮）
            drawRect(
                color = color.copy(alpha = 0.95f * alpha),
                topLeft = Offset(x + 1.5f, y + 5f),
                size = Size(2.5f, h - 10f),
            )
            // 头部用 span 自带颜色（城市=应援色），只靠 alpha 控制整体淡入淡出
            drawText(
                headLayout,
                topLeft = Offset(x + padX + 3f, y + padY),
                alpha = alpha,
            )
            if (subLayout != null) {
                drawText(
                    subLayout,
                    color = Color.White.copy(alpha = 0.80f),
                    topLeft = Offset(x + padX + 3f, y + padY + headLayout.size.height + gap),
                    alpha = alpha,
                )
            }
        }

        // 背景
        drawRect(palette.background)

        // 点亮集合 / 城市分组都在 Canvas 外面算好了（见上面的 remember），这里直接读

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

        // 所有地块/边界都在同一个变换里画：原来是每个 path 各包一次 withTransform，
        // 370 个地块就是 370 次 canvas save/restore，播放时每帧都做，白烧 GPU/CPU。
        withTransform({
            translate(dx, dy)
            scale(k, k, pivot = Offset.Zero)
        }) {
            provinces.forEach { p ->
                val visited = litAdcodesNow.contains(p.adcode)
                // 官方档案点亮的地方只铺一层淡色（官方来过），我自己去过的才是实色
                val isMine = touringMine.contains(p.adcode)
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
                when {
                    !visited -> drawPath(p.path, color = palette.unlitCity)

                    // 官方场次（我没去）：淡一层，和我的实色区分开
                    !isMine -> drawPath(
                        p.path,
                        color = yearTint(cityColor, years.lastOrNull()?.let { yearStrength(it) } ?: 1f)
                            .copy(alpha = if (yearLayers) 0.20f else 0.30f),
                    )

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
                        color = litEdge(cityColor).copy(alpha = if (isMine) 0.55f else 0.22f),
                        style = Stroke(width = (if (isMine) 1.1f else 0.8f) / k),
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

            // 地级市边界：放大后才画，缩小态只保留省级边界
            if (scale >= DETAIL_ZOOM) {
                provinces.forEach { p ->
                    drawPath(p.path, color = palette.cityBorder, style = Stroke(width = 1f / k))
                }
            }

            // 省级边界：始终画，勾勒省轮廓
            provinceBounds.forEach { b ->
                drawPath(b.path, color = palette.provinceBound, style = Stroke(width = 1.4f / k))
            }
        }

        // 年份刻度：去过两年以上的城市，在质心上方排一排小点，越早越淡。
        // 地块本身只能填一个颜色，历史年份靠这排点表达——一眼就能看出「这座城市我去过哪几年」。
        if (yearLayers) {
            litCityProvinces.forEach { p ->
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
        litCityProvinces.forEach { p ->
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
        plainCityProvinces.take(cityLimit).forEach { p ->
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

        // ===== 路线：官方档案（淡、细）与我的路线（亮、粗、唯一光点）=====
        // 线上活动没有坐标，连线时跳过，避免连到 (0,0)
        fun buildSegs(stops: List<Spot>): List<RouteSeg> {
            val segs = ArrayList<RouteSeg>(maxOf(0, stops.size - 1))
            for (i in 0 until stops.size - 1) {
                val from = stops[i]
                val to = stops[i + 1]
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
            return segs
        }

        // 官方路线：细而淡的虚线拱线 + 每个场次一个空心点，只交代「官方这一年开过哪些场」。
        // 播放动画时也保留，和我自己的路线叠在一起，一眼能分清哪条是官方、哪条是我走的。
        if (routeMode.showOfficial && officialStops.size >= 2) {
            buildSegs(officialStops).forEach { s ->
                drawLine(s.c1.copy(alpha = 0.08f), s.a, s.b, strokeWidth = 1f, cap = StrokeCap.Round)
                val segments = 30
                for (i in 0 until segments) {
                    // 间隔绘制 → 虚线，和我的实线区分
                    if (i % 3 == 2) continue
                    val t0 = i / segments.toFloat()
                    val t1 = (i + 1) / segments.toFloat()
                    val p0 = quadPoint(s.a, s.ctrl, s.b, t0)
                    val p1 = quadPoint(s.a, s.ctrl, s.b, t1)
                    val tm = (t0 + t1) / 2
                    val lift = 1f - kotlin.math.abs(tm - 0.5f) * 2f
                    drawLine(
                        lerp(s.c1, s.c2, tm).copy(alpha = 0.10f + 0.22f * lift),
                        p0,
                        p1,
                        strokeWidth = 0.8f + 1.2f * lift,
                        cap = StrokeCap.Butt,
                    )
                }
            }
            officialStops.forEach { stop ->
                val c = toScreen(spotVirtual(stop).x, spotVirtual(stop).y)
                drawCircle(cityColors(stop.adcode).first.copy(alpha = 0.50f), radius = 3.2f, center = c, style = Stroke(width = 1f))
            }
        }

        // 我的路线：亮实线 + 场次日期（一直是实线，播放官方路线时也留在图上）
        if (routeMode.showMine && myStops.size >= 1) {
            val segs = buildSegs(myStops)
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

            // 我的路线每个点标上日期，一眼看出「哪一年哪一天站在这里」。
            // 播放时先藏起来：那时候有到站标签，两套叠在一起会糊。
            if (!isTouring) {
                myStops.forEach { stop ->
                    val c = toScreen(spotVirtual(stop).x, spotVirtual(stop).y)
                    drawDatePill(dateLabelOf(stop), c, cityColors(stop.adcode).first, 0.92f)
                }
            }

            // 光点只有一个：沿整条路线按航段顺序走过去，不再「每段各一个点一起出发」。
            // 播放官方路线时，我的这条光点也继续走自己的，和官方动画分开显示（两条线互不干扰）。
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

        // ===== 换系列提示：地图上方飘一下系列名，2.4s 自动消失 =====
        // 播放到新的巡演系列时弹一次，配合到站标签，用户能立刻知道「换系列了」。
        if (bannerText.isNotBlank()) {
            val elapsed = System.currentTimeMillis() - bannerStart
            if (elapsed in 0..2400) {
                val a = when {
                    elapsed < 260 -> elapsed / 260f
                    elapsed > 1900 -> ((2400 - elapsed) / 500f).coerceIn(0f, 1f)
                    else -> 1f
                }
                if (a > 0.01f) {
                    val layout = bannerLayouts.getOrPut(bannerText) {
                        textMeasurer.measure(
                            AnnotatedString(bannerText),
                            style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                            constraints = Constraints(maxWidth = 720),
                        )
                    }
                    val tw = layout.size.width.toFloat()
                    val th = layout.size.height.toFloat()
                    val cx = size.width / 2f
                    // 放在顶部两条玻璃栏下面（0.30 屏高），别和年份条打架
                    val cy = size.height * 0.30f - (1f - a) * 6f
                    // 背后一条横向渐隐的深色带，把地图压下去，字才立得住
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to Color(0xFF08161E).copy(alpha = 0.74f * a),
                            1f to Color.Transparent,
                        ),
                        topLeft = Offset(0f, cy - th / 2f - 24f),
                        size = Size(size.width, th + 48f),
                    )
                    val tagLayout = bannerTagLayout
                    drawText(
                        tagLayout,
                        color = accent.copy(alpha = 0.92f * a),
                        topLeft = Offset(cx - tagLayout.size.width / 2f, cy - th / 2f - tagLayout.size.height - 5f),
                    )
                    drawText(
                        layout,
                        brush = Brush.horizontalGradient(
                            listOf(
                                lerp(accent, Color.White, 0.42f),
                                accent,
                                lerp(accent, Color(0xFF08161E), 0.18f),
                            ),
                            startX = cx - tw / 2f,
                            endX = cx + tw / 2f,
                        ),
                        topLeft = Offset(cx - tw / 2f, cy - th / 2f),
                        alpha = a,
                        shadow = Shadow(
                            color = accent.copy(alpha = 0.75f * a),
                            offset = Offset(0f, 0f),
                            blurRadius = 14f,
                        ),
                    )
                }
            }
        }

        // ===== 巡演点亮动画绘制：官方线一层、我的线一层，各播各的 =====
        if (isTouring) {
            // 屏幕坐标按城市算（线上活动没有坐标，不参与连线）
            fun spotPos(spot: Spot): Offset = toScreen(spotVirtual(spot).x, spotVirtual(spot).y)

            /**
             * 画一条巡演线的当前状态。
             *
             * [isMineLine] 决定这一层的浓度：我的路线更亮更粗、日期更清楚；官方线淡一档、细一档。
             * 两条线同时播的时候，一眼就能分出「官方开过的」和「我真的走到的」。
             */
            fun drawTourLayer(
                stops: List<Spot>,
                phase: TourPhase,
                litCount: Int,
                isMineLine: Boolean,
            ) {
                if (stops.isEmpty()) return
                val arcAlpha = if (isMineLine) 0.55f else 0.24f
                val lineScale = if (isMineLine) 1f else 0.70f
                val pillWeight = if (isMineLine) 1f else 0.58f
                // 官方标签挂城市上方（框更高，抬得更多）、我的挂下方：同站也不会叠在一起
                val pillLift = if (isMineLine) -8f else 74f

                // 1) 已走过的段：只连「已点亮的实体城市」，按时间顺序 1→2、2→3 串起来
                val litReal = stops.take(litCount).filter { it.adcode.isNotBlank() }
                for (i in 0 until litReal.size - 1) {
                    drawTourArc(
                        spotPos(litReal[i]),
                        spotPos(litReal[i + 1]),
                        cityColors(litReal[i].adcode).first,
                        alpha = arcAlpha,
                        color2 = cityColors(litReal[i + 1].adcode).second,
                        scale = lineScale,
                    )
                }

                // 当前站：给底部进度胶囊用
                val currentIdx = when (phase) {
                    is TourPhase.Fly -> phase.toIndex
                    is TourPhase.Burst -> phase.index
                    is TourPhase.Flash -> phase.index
                    is TourPhase.Online -> phase.index
                    is TourPhase.Ripple -> (stops.size - 1).coerceAtLeast(0)
                    else -> (litCount - 1).coerceAtLeast(0)
                }

                // 2) 飞行中：一条平滑拖尾 + 唯一光点。
                // 时间/演出信息只在「到达之后」出现（Burst + Flash），飞行途中不飘字，画面更干净。
                if (phase is TourPhase.Fly) {
                    val from = stops.getOrNull(phase.fromIndex)
                    val to = stops.getOrNull(phase.toIndex)
                    if (from != null && to != null) {
                        val a = spotPos(from)
                        val b = spotPos(to)
                        val ddx = b.x - a.x
                        val ddy = b.y - a.y
                        val len = hypot(ddx, ddy)
                        if (len > 1f) {
                            var nx = -ddy / len
                            var ny = ddx / len
                            if (ny > 0) { nx = -nx; ny = -ny }
                            val height = len * 0.28f
                            val ctrl = Offset((a.x + b.x) / 2 + nx * height, (a.y + b.y) / 2 + ny * height)
                            // 18 段足够画出弧线，44 段在动画里纯属浪费（每段还要算一次颜色插值）
                            val segs = 18
                            val trailSpan = 0.55f
                            val fromColor = cityColors(from.adcode).first
                            val toColor = cityColors(to.adcode).second
                            for (i in 0 until segs) {
                                val t0 = i / segs.toFloat()
                                val t1 = (i + 1) / segs.toFloat()
                                if (t0 >= phase.t) break
                                val behind = phase.t - (t0 + t1) / 2f
                                // 越靠后越淡，但保留底色，到站后正好接上已走过段的淡线
                                val near = (1f - behind / trailSpan).coerceIn(0f, 1f)
                                val alpha = (if (isMineLine) 0.42f else 0.26f) +
                                    (if (isMineLine) 0.55f else 0.34f) * near
                                val width = (1.3f + 2.6f * near) * lineScale
                                val segColor = lerp(fromColor, toColor, (t0 + t1) / 2f)
                                drawLine(
                                    segColor.copy(alpha = alpha.coerceAtMost(0.97f)),
                                    quadPoint(a, ctrl, b, t0),
                                    quadPoint(a, ctrl, b, t1),
                                    strokeWidth = width,
                                    cap = StrokeCap.Round,
                                )
                            }
                            // 光点：出发时淡入、到站前淡出，一次只有一个
                            val pos = quadPoint(a, ctrl, b, phase.t)
                            val headAlpha = minOf(
                                (phase.t / 0.12f).coerceIn(0f, 1f),
                                ((1f - phase.t) / 0.14f).coerceIn(0f, 1f),
                            )
                            if (headAlpha > 0.01f) {
                                val headColor = lerp(fromColor, toColor, phase.t)
                                drawCircle(headColor.copy(alpha = 0.32f * headAlpha), radius = 11f * lineScale, center = pos)
                                drawCircle(Color.White.copy(alpha = 0.98f * headAlpha), radius = 4.5f * lineScale, center = pos)
                            }
                        }
                    }
                }

                // 3) Burst：扩散填满整块城市。
                // 原来是「按地块裁剪 + 径向渐变 + 前沿高亮环」，每帧都要建 clipPath 和 Brush，
                // 是整段动画里最贵的一笔；现在直接铺一层半透明纯色，肉眼几乎看不出差别，开销接近零。
                if (phase is TourPhase.Burst) {
                    val spot = stops.getOrNull(phase.index)
                    val province = spot?.let { provinceByAdcode[it.adcode] }
                    if (spot != null && province != null) {
                        val base = cityAccent(spot.adcode)
                        val fillAlpha = (if (isMineLine) 0.85f else 0.42f) * phase.t
                        withTransform({
                            translate(dx, dy)
                            scale(k, k, pivot = Offset.Zero)
                        }) {
                            drawPath(province.path, color = litFill(base).copy(alpha = fillAlpha))
                        }
                        // 到站才出标签：城市 + 日期 + 演出名（Burst 后半程浮现）
                        drawEventPill(
                            spot,
                            spotPos(spot),
                            cityColors(spot.adcode).first,
                            ((phase.t - 0.45f) / 0.55f).coerceIn(0f, 1f) * pillWeight,
                            pillLift,
                            isMineLine,
                        )
                    }
                }

                // 3.5) 刚飞完的那一段：亮度从飞行态平滑沉到已走过段的淡色
                if (phase is TourPhase.Burst || phase is TourPhase.Flash) {
                    val idx = if (phase is TourPhase.Burst) phase.index else (phase as TourPhase.Flash).index
                    val t = if (phase is TourPhase.Burst) phase.t * 0.5f else 0.5f + (phase as TourPhase.Flash).t * 0.5f
                    val prevReal = stops.take(idx).lastOrNull { it.adcode.isNotBlank() }
                    val arrived = stops.getOrNull(idx)
                    if (prevReal != null && arrived != null) {
                        val hot = if (isMineLine) 0.92f else 0.55f
                        drawTourArc(
                            spotPos(prevReal),
                            spotPos(arrived),
                            cityColors(prevReal.adcode).first,
                            alpha = hot + (arcAlpha - hot) * t,
                            color2 = cityColors(arrived.adcode).second,
                            scale = lineScale,
                        )
                    }
                }

                // 4) Flash：到达后高亮闪一下；到站标签全程保持，末尾淡出
                if (phase is TourPhase.Flash) {
                    val spot = stops.getOrNull(phase.index)
                    val province = spot?.let { provinceByAdcode[it.adcode] }
                    if (province != null && spot != null) {
                        val flashAlpha = (1f - phase.t) * (if (isMineLine) 0.55f else 0.26f)
                        withTransform({
                            translate(dx, dy)
                            scale(k, k, pivot = Offset.Zero)
                        }) {
                            drawPath(province.path, color = Color.White.copy(alpha = flashAlpha))
                        }
                        // 前 60% 保持，后 40% 淡出——够读完城市和演出名
                        val pillAlpha = if (phase.t < 0.6f) 1f else ((1f - phase.t) / 0.4f).coerceIn(0f, 1f)
                        drawEventPill(
                            spot,
                            spotPos(spot),
                            cityColors(spot.adcode).first,
                            pillAlpha * pillWeight,
                            pillLift,
                            isMineLine,
                        )
                    }
                }

                // 4.5) 线上活动：没有实体城市，就在地图中央飘一条活动名（只有官方线会有）
                if (phase is TourPhase.Online && !isMineLine) {
                    val spot = stops.getOrNull(phase.index)
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

                // 5) Ripple：全部点亮后整体扩散一次。
                // 每座城只画一个环（原来两个），20 次描边降到 10 次，收尾那 1.6s 不再掉帧。
                if (phase is TourPhase.Ripple) {
                    val wave = phase.t
                    val spread = if (isMineLine) 1f else 0.75f
                    val ringColor = accent.copy(alpha = (1f - wave) * (if (isMineLine) 0.55f else 0.32f))
                    val ringWidth = 2.2f * (1f - wave * 0.5f) * spread
                    val radius = (8f + wave * 90f) * spread
                    stops.forEach { spot ->
                        if (spot.adcode.isBlank()) return@forEach
                        drawCircle(
                            ringColor,
                            radius = radius,
                            center = spotPos(spot),
                            style = Stroke(width = ringWidth),
                        )
                    }
                }

                // 6) 底部进度胶囊：这一层现在走到第几站 / 共几站，带日期和城市
                stops.getOrNull(currentIdx)?.let { s ->
                    val label = buildString {
                        append(if (isMineLine) "我的 " else "官方 ")
                        append(shortDateFormat.format(java.util.Date(s.performedAt)))
                        append(" ")
                        append(s.cityName.ifBlank { if (s.adcode.isBlank()) "线上" else s.title })
                        append("  ")
                        append(currentIdx + 1)
                        append("/")
                        append(stops.size)
                    }
                    // 两条线上下错开 44px，不然两个胶囊会叠在屏幕底部糊成一片
                    val yBase = size.height - if (isMineLine) 84f else 128f
                    drawDatePill(
                        label,
                        Offset(size.width / 2f, yBase),
                        if (isMineLine) accent else lerp(accent, Color.White, 0.25f),
                        0.96f,
                    )
                }
            }

            // 官方线先画（淡），我的线压在上面（亮）
            drawTourLayer(officialTour, officialPhase, officialLit, isMineLine = false)
            drawTourLayer(mineTour, minePhase, mineLit, isMineLine = true)
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
    scale: Float = 1f,
) {
    val ddx = b.x - a.x
    val ddy = b.y - a.y
    val len = hypot(ddx, ddy).takeIf { it > 0f } ?: return
    var nx = -ddy / len
    var ny = ddx / len
    if (ny > 0) { nx = -nx; ny = -ny }
    val height = len * 0.28f
    val ctrl = Offset((a.x + b.x) / 2 + nx * height, (a.y + b.y) / 2 + ny * height)
    // 16 段：一段弧线看不出棱角，比 28 段少一半绘制
    val segments = 16
    for (i in 0 until segments) {
        val t0 = i / segments.toFloat()
        val t1 = (i + 1) / segments.toFloat()
        val p0 = quadPoint(a, ctrl, b, t0)
        val p1 = quadPoint(a, ctrl, b, t1)
        val tm = (t0 + t1) / 2
        val lift = 1f - kotlin.math.abs(tm - 0.5f) * 2f
        val width = (0.7f + 3.5f * lift) * scale
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
 * 二次贝塞尔曲线上的点。航线就是一条干净的抛物线——
 * 之前叠了两条正弦让线"飘"，视觉上一直抖，还每帧多算两次 sin，已经去掉了。
 */
/**
 * 二次贝塞尔曲线上的点。航线就是一条干净的抛物线——
 * 之前叠了两条正弦让线"飘"，视觉上一直抖，还每帧多算两次 sin，已经去掉了。
 */
private fun quadPoint(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * a.x + 2 * u * t * c.x + t * t * b.x,
        u * u * a.y + 2 * u * t * c.y + t * t * b.y,
    )
}
