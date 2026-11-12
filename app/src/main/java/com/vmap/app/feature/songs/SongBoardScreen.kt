package com.vmap.app.feature.songs

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.domain.model.Song
import com.vmap.app.domain.model.SongRating
import java.io.File

/**
 * 歌曲鉴赏：海报墙式的曲目排行。
 *
 * 曲目与曲绘来自网易云音乐曲库（曲绘优先取 B 站官方投稿封面），
 * 顶部可以按板块 / 年份 / 演唱会系列筛。
 *
 * 两种密度：
 * - 大图：每个评级一条横向大幅曲绘带（3:4 海报），适合慢慢看；
 * - 小图：整个页面变成一个网格，一屏能塞二三十首，横屏会自动摊成更多列。
 *
 * 长按曲绘拖动换位、换组，松手落库。没有曲绘的歌用应援色渐变 + 首字兜底。
 */
@Composable
fun SongBoardScreen(
    viewModel: SongBoardViewModel,
    isDark: Boolean,
    accent: Color,
) {
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()
    val allSongs by viewModel.songs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    var compact by remember { mutableStateOf(prefs.getBoolean(KEY_COMPACT, true)) }

    // 筛选：板块 / 年份 / 演唱会系列 / 单场。组合起来就是「某年某轮某站」的排行。
    // 用 rememberSaveable：横竖屏切换会重建 Activity，普通 remember 会让筛选选项全归零。
    var seriesFilter by rememberSaveable { mutableStateOf("") }
    var yearFilter by rememberSaveable { mutableStateOf(0) }
    var tourFilter by rememberSaveable { mutableStateOf("") }
    // 选中具体某一场（存档案 id）：用来把排行定位到「纯蓝幻乐 长沙场」
    var sessionPick by rememberSaveable { mutableStateOf<String?>(null) }

    // 当前系列下的场次（城市 + 日期），选中系列后才会显示
    val concertEvents = remember(context) {
        com.vmap.app.data.archive.ConcertArchive.load(context)
    }
    val tourSessions = remember(tourFilter, concertEvents) {
        if (tourFilter.isBlank()) {
            emptyList()
        } else {
            concertEvents
                .filter { !it.online }
                .filter { e ->
                    // 档案里写好的 tour 优先，其次才从演出名里推
                    (e.tour.ifBlank { com.vmap.app.core.text.seriesOfEventName(e.name) }) == tourFilter
                }
                .sortedByDescending { it.dateLabel }
        }
    }

    val years = remember(allSongs) {
        allSongs.asSequence()
            .flatMap { it.years.split(',').asSequence() }
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .sorted()
            .toList()
    }
    val tours = remember(allSongs) {
        allSongs.asSequence()
            .flatMap { it.tour.split(',').asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }
    // 选中某一场时，看看有没有这一场的节目单（setlists.json）
    val activeSetlist = remember(sessionPick, concertEvents, context) {
        val e = tourSessions.firstOrNull { it.id == sessionPick }
            ?: return@remember null
        com.vmap.app.data.song.SetlistArchive.find(
            context,
            e.tour.ifBlank { com.vmap.app.core.text.seriesOfEventName(e.name) },
            e.city,
            e.year,
        )
    }

    val songs = remember(allSongs, seriesFilter, yearFilter, tourFilter, activeSetlist) {
        val base = allSongs.filter { s ->
            (seriesFilter.isBlank() || s.series == seriesFilter) &&
                (yearFilter == 0 || s.years.split(',').any { it.trim() == yearFilter.toString() }) &&
                (tourFilter.isBlank() || s.tour.split(',').any { it.trim() == tourFilter })
        }
        val setlist = activeSetlist ?: return@remember base
        // 有节目单：按当晚的曲目顺序排，一首曲目对应库里一首歌（同名取登场最多的）
        val byLoose = base.groupBy { com.vmap.app.core.text.looseSongTitle(it.title) }
        setlist.tracks.mapNotNull { track ->
            byLoose[com.vmap.app.core.text.looseSongTitle(track)]?.maxByOrNull { it.perfCount }
        }
    }
    val filtering = seriesFilter.isNotBlank() || yearFilter != 0 || tourFilter.isNotBlank()

    // 按评级分组，组内按手动顺序；再按评级的 orderIndex 排序（S 在上）。
    // 空档也要显示：不然没有曲子的评级档从界面上消失，用户没法把歌拖进去。
    val grouped = remember(ratings, songs) {
        ratings.sortedBy { it.orderIndex }
            .map { r -> r to songs.filter { it.ratingId == r.id }.sortedBy { it.orderIndex } }
    }
    // 未归属任何评级（理论上不该出现）的歌也兜住
    val ratingIds = ratings.map { it.id }.toSet()
    val unrated = remember(songs, ratingIds) { songs.filter { it.ratingId !in ratingIds } }

    val rows: List<Pair<SongRating, List<Song>>> = remember(grouped, unrated, filtering) {
        buildList {
            addAll(grouped)
            if (unrated.isNotEmpty()) {
                add(
                    SongRating(name = "未分组", color = 0xFF8E9BAA, orderIndex = Int.MAX_VALUE) to
                        unrated.sortedBy { it.orderIndex },
                )
            }
        }
    }

    // 拖动坐标系都走窗口坐标
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<Song?>(null) }
    var dragFrom by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    var editing by remember { mutableStateOf<Song?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showRatings by remember { mutableStateOf(false) }

    /**
     * 松手落位。大图 / 小图共用一套逻辑：
     * 先按落点的 y 找它属于哪个评级（最后一个标题条在它上方的），
     * 再按阅读顺序数一下落点前有几张卡。
     */
    fun commitDrag(song: Song) {
        val center = dragFrom + dragDelta
        val targetId = rowBounds.entries
            .filter { it.value.top <= center.y }
            .maxByOrNull { it.value.top }?.key ?: return

        val tierIds = rows.firstOrNull { it.first.id == targetId }?.second?.map { it.id }?.toSet() ?: return
        val cards = cardBounds.filterKeys { it in tierIds }
        // 空档也能落：没有卡可数，就放到第 0 位（「拖到这里」占位块的位置）
        if (cards.isEmpty()) {
            viewModel.moveSong(song.id, targetId, 0)
            return
        }

        // tier 墙是二维网格：先比行（y 差超过半格算换行），同一行内再比列
        val before = cards.count { (_, r) ->
            if (kotlin.math.abs(r.center.y - center.y) > r.height * 0.6f) {
                r.center.y < center.y
            } else {
                r.center.x < center.x
            }
        }
        val sameRow = song.ratingId == targetId
        val current = rows.firstOrNull { it.first.id == targetId }
            ?.second?.indexOfFirst { it.id == song.id } ?: -1
        val target = if (sameRow && current >= 0 && before > current) before - 1 else before
        if (sameRow && target == current) return
        viewModel.moveSong(song.id, targetId, target)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        // 小图模式的密度：横屏一页正好 5 个评级（一行一个评级，行高按屏高均分），
        // 竖屏不强制分页，卡片保持 64dp 左右，一屏能看到六七个评级。
        // header 的真实高度是量出来的——筛选行多一行少一行都不会把「一页 5 个」算歪。
        var headerHeight by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val topPad = if (landscape) 62.dp else 92.dp
        // 横屏底部不需要留那么多空白（没有悬浮按钮），留 56dp 就够，
        // 空出来的高度全给评级行，卡片才不至于小成一排邮票
        val bottomPad = if (landscape) 56.dp else 112.dp

        // tier 墙的格子：按像素算列数（Dp 除法容易差一格），余量摊平成格宽。
        // 目标格宽横竖屏统一 64dp，横屏屏宽大 → 列数自然变多，但格子大小一致，
        // 不会出现「横屏是另一种墙」的观感。
        val wallSidePad = 16.dp
        val wallLeft = 46.dp
        val wallGap = 2.dp
        val wallTargetCell = 64.dp
        val (wallColumns, wallCell) = with(density) {
            val availPx = maxWidth.toPx() - wallSidePad.toPx() - wallLeft.toPx() - wallGap.toPx()
            val gapPx = wallGap.toPx()
            val targetPx = wallTargetCell.toPx()
            val cols = ((availPx + gapPx) / (targetPx + gapPx)).toInt().coerceIn(3, 24)
            cols to ((availPx - gapPx * (cols - 1)) / cols).toDp()
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topPad,
                bottom = bottomPad,
            ),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalItemSpacing = 0.dp,
        ) {
            item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { headerHeight = it.size.height },
                ) {
                    GradientTitle(text = "歌曲鉴赏", accent = accent, fontSize = 24.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            // 选了具体某一站：有节目单就按节目单，没有就说明是「这一轮」
                            sessionPick != null -> {
                                val s = tourSessions.firstOrNull { it.id == sessionPick }
                                val md = s?.let { com.vmap.app.core.text.monthDayOf(it.dateLabel) } ?: ""
                                if (activeSetlist != null) {
                                    "${s?.city ?: ""} $md · 按现场歌单 ${songs.size}/${activeSetlist.tracks.size} 首"
                                } else {
                                    "${s?.city ?: ""} $md · ${songs.size} 首 · 同一轮各站曲目相通"
                                }
                            }

                            filtering -> "${songs.size} / ${allSongs.size} 首 · 按演唱会登场次数归档"
                            else -> "${allSongs.size} 首 · 封面取 B 站（官方 > 最高播放）· 长按拖动排序"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(9.dp))

                    // 板块 + 工具挤在一行横向滚动，横屏时省下一整行高度
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item(key = "board-all") { FilterChip("全部板块", seriesFilter.isBlank(), accent, isDark) { seriesFilter = "" } }
                        item(key = "board-t") { FilterChip("依你随行", seriesFilter == "TIANYI", accent, isDark) { seriesFilter = "TIANYI" } }
                        item(key = "board-m") { FilterChip("因音而遇", seriesFilter == "MIKU", accent, isDark) { seriesFilter = "MIKU" } }
                        item(key = "density") {
                            DensityChip(
                                compact = compact,
                                accent = accent,
                                isDark = isDark,
                                onToggle = {
                                    compact = !compact
                                    prefs.edit().putBoolean(KEY_COMPACT, compact).apply()
                                    cardBounds.clear()
                                    rowBounds.clear()
                                },
                            )
                        }
                        item(key = "add") { ToolChip("＋ 歌曲", accent, isDark) { showAdd = true } }
                        item(key = "import") { ToolChip("导入", accent, isDark) { showImport = true } }
                        item(key = "rating") { ToolChip("评级", accent, isDark) { showRatings = true } }
                    }
                    Spacer(Modifier.height(6.dp))

                    // 年份
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item(key = "year-all") {
                            FilterChip("全部年份", yearFilter == 0, accent, isDark) { yearFilter = 0 }
                        }
                        years.forEach { y ->
                            item(key = "year-$y") {
                                FilterChip("$y", yearFilter == y, accent, isDark) {
                                    yearFilter = if (yearFilter == y) 0 else y
                                }
                            }
                        }
                    }

                    // 演唱会系列（横竖屏都显示，横竖屏内容一致；高度不够靠滚动）
                    if (tours.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item(key = "tour-all") {
                                FilterChip("全部系列", tourFilter.isBlank(), accent, isDark) {
                                    tourFilter = ""
                                    sessionPick = null
                                }
                            }
                            tours.forEach { t ->
                                item(key = "tour-$t") {
                                    FilterChip(t, tourFilter == t, accent, isDark) {
                                        tourFilter = if (tourFilter == t) "" else t
                                        sessionPick = null
                                    }
                                }
                            }
                        }
                    }

                    // 选了系列之后，再往下钻一层：这一轮巡演的具体场次（城市 + 日期）
                    // 点某一场就把年份锁到那一年，等于看「这一站这一轮唱的歌」的排行
                    if (tourSessions.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item(key = "sess-all") {
                                FilterChip("全部场次", sessionPick == null, accent, isDark) {
                                    sessionPick = null
                                    yearFilter = 0
                                }
                            }
                            tourSessions.forEach { e ->
                                item(key = "sess-${e.id}") {
                                    FilterChip(
                                        label = "${e.city} ${com.vmap.app.core.text.monthDayOf(e.dateLabel)}",
                                        selected = sessionPick == e.id,
                                        accent = accent,
                                        isDark = isDark,
                                    ) {
                                        if (sessionPick == e.id) {
                                            sessionPick = null
                                            yearFilter = 0
                                        } else {
                                            sessionPick = e.id
                                            yearFilter = e.year
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            rows.forEach { (rating, list) ->
                if (compact) {
                    // 小图：tier list 那样一块一块——左边色条，右边封面密排折行
                    item(key = "row-${rating.id}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.5.dp),
                        ) {
                            TierWallRow(
                                rating = rating,
                                songs = list,
                                cellSize = wallCell,
                                columns = wallColumns,
                                isDark = isDark,
                                draggingId = dragging?.id,
                                dragDelta = dragDelta,
                                onBounds = { rowBounds[rating.id] = it },
                                onCardBounds = { id, rect -> cardBounds[id] = rect },
                                onDragStart = { song, from ->
                                    dragging = song
                                    dragFrom = from
                                    dragDelta = Offset.Zero
                                },
                                onDrag = { song, amount ->
                                    dragDelta += amount
                                    commitDrag(song)
                                },
                                onDragEnd = {
                                    dragging = null
                                    dragDelta = Offset.Zero
                                },
                                onCardClick = { editing = it },
                            )
                        }
                    }
                } else {
                    item(key = "row-${rating.id}", span = StaggeredGridItemSpan.FullLine) {
                        TierLabel(
                            rating = rating,
                            count = list.size,
                            isDark = isDark,
                            compact = false,
                            onBounds = { rowBounds[rating.id] = it },
                        )
                    }
                    item(key = "band-${rating.id}", span = StaggeredGridItemSpan.FullLine) {
                        TierRow(
                            songs = list,
                            ratingColor = Color(rating.color),
                            compact = false,
                            yearFilter = yearFilter,
                            isDark = isDark,
                            draggingId = dragging?.id,
                            dragDelta = dragDelta,
                            onCardBounds = { id, rect -> cardBounds[id] = rect },
                            onDragStart = { song, from ->
                                dragging = song
                                dragFrom = from
                                dragDelta = Offset.Zero
                            },
                            onDrag = { song, amount ->
                                dragDelta += amount
                                commitDrag(song)
                            },
                            onDragEnd = {
                                dragging = null
                                dragDelta = Offset.Zero
                            },
                            onCardClick = { editing = it },
                        )
                    }
                }
            }

            if (rows.isEmpty()) {
                item(key = "no-rating", span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        text = "还没有评级，点上面的「评级」加一个",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp),
                    )
                }
            }
        }
    }

    if (showAdd) {
        SongEditDialog(
            song = null,
            ratings = ratings,
            accent = accent,
            onDismiss = { showAdd = false },
            onSave = { title, artist, plays, ratingId, coverUri, _ ->
                viewModel.addSong(title, artist, plays, ratingId, coverUri)
                showAdd = false
            },
            onDelete = null,
        )
    }

    editing?.let { song ->
        SongEditDialog(
            song = song,
            ratings = ratings,
            accent = accent,
            onDismiss = { editing = null },
            onSave = { title, artist, plays, ratingId, coverUri, removeCover ->
                viewModel.updateSong(song, title, artist, plays, ratingId, coverUri, removeCover)
                editing = null
            },
            onDelete = {
                viewModel.deleteSong(song)
                editing = null
            },
            onPickAltCover = { alt ->
                viewModel.setCover(song, alt)
            },
        )
    }

    if (showImport) {
        ImportDialog(
            ratings = ratings,
            accent = accent,
            onDismiss = { showImport = false },
            onImport = { text, ratingId ->
                viewModel.importSongText(text, ratingId)
                showImport = false
            },
        )
    }

    if (showRatings) {
        RatingDialog(
            ratings = ratings,
            accent = accent,
            isDark = isDark,
            onAdd = { name, color -> viewModel.addRating(name, color) },
            onDelete = viewModel::deleteRating,
            onRecolor = viewModel::changeRatingColor,
            onDismiss = { showRatings = false },
        )
    }
}

/** 评级标题条：色块 + 档名 + 数量，顺带把这一档的窗口坐标报给拖动逻辑。 */
@Composable
private fun TierLabel(
    rating: SongRating,
    count: Int,
    isDark: Boolean,
    compact: Boolean = false,
    onBounds: (Rect) -> Unit = {},
) {
    val color = Color(rating.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .padding(
                start = if (compact) 10.dp else 16.dp,
                end = 16.dp,
                top = if (compact) 6.dp else 10.dp,
                bottom = if (compact) 3.dp else 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(color.copy(alpha = 0.30f))
                .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
                .padding(horizontal = 7.dp, vertical = if (compact) 0.dp else 2.dp),
        ) {
            Text(
                text = rating.name,
                color = if (isDark) lerp(color, Color.White, 0.45f) else lerp(color, Color(0xFF08161E), 0.30f),
                fontSize = if (compact) 12.sp else 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = "$count 首",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = if (compact) 10.sp else 11.sp,
        )
    }
}

/** 一个评级的一整行：横向滚动的曲绘卡片。小图=小方块，大图=3:4 海报。 */
@Composable
private fun TierRow(
    songs: List<Song>,
    ratingColor: Color,
    compact: Boolean,
    yearFilter: Int,
    isDark: Boolean,
    draggingId: String?,
    dragDelta: Offset,
    onCardBounds: (String, Rect) -> Unit,
    onDragStart: (Song, Offset) -> Unit,
    onDrag: (Song, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onCardClick: (Song) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        if (songs.isEmpty()) {
            item(key = "empty") { EmptySlot(ratingColor) }
        }
        songs.forEach { song ->
            item(key = song.id) {
                if (compact) {
                    CompactSongCard(
                        song = song,
                        rank = songs.indexOf(song) + 1,
                        ratingColor = ratingColor,
                        isDark = isDark,
                        isDragging = draggingId == song.id,
                        dragDelta = if (draggingId == song.id) dragDelta else Offset.Zero,
                        onBounds = { onCardBounds(song.id, it) },
                        onDragStart = { from -> onDragStart(song, from) },
                        onDrag = { amount -> onDrag(song, amount) },
                        onDragEnd = onDragEnd,
                        onClick = { onCardClick(song) },
                    )
                } else {
                    SongPosterCard(
                        song = song,
                        rank = songs.indexOf(song) + 1,
                        yearFilter = yearFilter,
                        ratingColor = ratingColor,
                        isDark = isDark,
                        isDragging = draggingId == song.id,
                        dragDelta = if (draggingId == song.id) dragDelta else Offset.Zero,
                        onBounds = { onCardBounds(song.id, it) },
                        onDragStart = { from -> onDragStart(song, from) },
                        onDrag = { amount -> onDrag(song, amount) },
                        onDragEnd = onDragEnd,
                        onClick = { onCardClick(song) },
                    )
                }
            }
        }
    }
}

/** 一张曲绘海报卡：3:4 竖版大图，文字压在图上。 */
@Composable
private fun SongPosterCard(
    song: Song,
    rank: Int,
    yearFilter: Int,
    ratingColor: Color,
    isDark: Boolean,
    isDragging: Boolean,
    dragDelta: Offset,
    onBounds: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    Column(
        modifier = Modifier
            .width(132.dp)
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationX = dragDelta.x
                translationY = dragDelta.y
                if (isDragging) {
                    scaleX = 1.06f
                    scaleY = 1.06f
                }
            }
            .onGloballyPositioned { coords ->
                onBounds(coords.boundsInWindow())
                center = coords.boundsInWindow().center
            }
            .pointerInput(song.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart(center) },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(20.dp))
                .glassPanel(
                    shape = RoundedCornerShape(20.dp),
                    isDark = isDark,
                    accent = ratingColor,
                    elevation = if (isDragging) 24.dp else 12.dp,
                    base = if (isDragging) 0.16f else 0.06f,
                ),
        ) {
            if (song.coverPath.isNotBlank()) {
                CoverImage(path = song.coverPath, modifier = Modifier.fillMaxSize())
            } else {
                CoverFallback(title = song.title, color = ratingColor, isDark = isDark)
            }

            // 底部渐变暗角：歌名 + 歌手 + 次数都压在这块，保证可读
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = when {
                    song.perfCount > 0 -> "演唱 ${song.perfCount} 场"
                    song.plays > 0 -> "▶ ${formatPlays(song.plays)}"
                    else -> ""
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = meta,
                            color = ratingColor.takeIf { isDark } ?: Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        val yearTag = if (yearFilter != 0) {
                            yearFilter
                        } else {
                            song.years.split(',').mapNotNull { it.trim().toIntOrNull() }.maxOrNull()
                        }
                        if (yearTag != null) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "$yearTag",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }

            // 左上角：评级名次角标
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = rank.toString().padStart(2, '0'),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 有 B 站来源的视频，右上角给个小角标
            if (song.linkUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = biliBadge(song.linkLabel),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 8.sp,
                    )
                }
            }
        }
    }
}

/**
 * 小图模式的一个评级块，照 tier list 的样子做的：
 * 左边一条纯色评级栏（铺满整块高度），右边封面正方形密排、按列折行，不写歌名。
 *
 * 这里不用 FlowRow：它按「实际宽度」自己折行，算出来的列数经常差一格。
 * 直接 chunked(columns) 手动分行，列数由外层按像素算好，所见即所得。
 */
@Composable
private fun TierWallRow(
    rating: SongRating,
    songs: List<Song>,
    cellSize: Dp,
    columns: Int,
    isDark: Boolean,
    draggingId: String?,
    dragDelta: Offset,
    onBounds: (Rect) -> Unit,
    onCardBounds: (String, Rect) -> Unit,
    onDragStart: (Song, Offset) -> Unit,
    onDrag: (Song, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onCardClick: (Song) -> Unit,
) {
    val color = Color(rating.color)
    val gap = 2.dp
    val rows = ((songs.size + columns - 1) / columns).coerceAtLeast(1)
    val blockHeight = cellSize * rows + gap * (rows - 1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(blockHeight)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) },
    ) {
        // 评级栏：整条铺满，字靠近顶部（块很长时居中会跑到屏幕中间去）
        Box(
            modifier = Modifier
                .width(46.dp)
                .fillMaxHeight()
                .background(color)
                .padding(top = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = rating.name,
                color = Color(0xFF161616),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(gap))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .size(cellSize)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.10f))
                        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("拖到这里", color = color.copy(alpha = 0.85f), fontSize = 8.sp)
                }
            }
            songs.chunked(columns).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    chunk.forEach { song ->
                        key(song.id) {
                            TierCell(
                                song = song,
                                size = cellSize,
                                ratingColor = color,
                                isDark = isDark,
                                isDragging = draggingId == song.id,
                                dragDelta = if (draggingId == song.id) dragDelta else Offset.Zero,
                                onBounds = { onCardBounds(song.id, it) },
                                onDragStart = { from -> onDragStart(song, from) },
                                onDrag = { amount -> onDrag(song, amount) },
                                onDragEnd = onDragEnd,
                                onClick = { onCardClick(song) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** tier list 里的一格：只有方形封面，不放歌名，尽量把信息压成「看图认歌」。 */
@Composable
private fun TierCell(
    song: Song,
    size: Dp,
    ratingColor: Color,
    isDark: Boolean,
    isDragging: Boolean,
    dragDelta: Offset,
    onBounds: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var center by remember { mutableStateOf(Offset.Zero) }
    // 上次上报的窗口 rect：只有真的变了才写 state / 通知上层，
    // 否则滚动时 297 个格子反复写同一个 map 会把主线程拖垮。
    var lastRect by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                if (isDragging) {
                    translationX = dragDelta.x
                    translationY = dragDelta.y
                    alpha = 0.86f
                    scaleX = 1.06f
                    scaleY = 1.06f
                }
            }
            .zIndex(if (isDragging) 2f else 0f)
            .clip(RoundedCornerShape(3.dp))
            .onGloballyPositioned {
                val b = it.boundsInWindow()
                if (b != lastRect) {
                    lastRect = b
                    onBounds(b)
                    center = b.center
                }
            }
            .pointerInput(song.id) {
                // 长按才拖：短按是打开编辑，直接拖会和列表滚动打架
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart(center) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                )
            }
            .clickable(onClick = onClick),
    ) {
        if (song.coverPath.isNotBlank()) {
            AsyncImage(
                model = remember(song.coverPath) { File(context.filesDir, song.coverPath) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                ratingColor.copy(alpha = 0.60f),
                                lerp(ratingColor, Color.Black, 0.55f).copy(alpha = 0.85f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = song.title.take(1).ifBlank { "曲" },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (song.linkUrl.isNotBlank()) {
            Text(
                text = if (song.linkLabel.contains("官方")) "官" else "B",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 6.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 2.dp, vertical = 0.5.dp),
            )
        }
    }
}

/** 小图模式的一格：方形曲绘 + 一行歌名，一屏能放很多。 */
@Composable
private fun CompactSongCard(
    song: Song,
    rank: Int,
    ratingColor: Color,
    isDark: Boolean,
    isDragging: Boolean,
    dragDelta: Offset,
    onBounds: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    size: Dp = 56.dp,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    var lastRect by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier = Modifier
            .width(size)
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationX = dragDelta.x
                translationY = dragDelta.y
                if (isDragging) {
                    scaleX = 1.05f
                    scaleY = 1.05f
                }
            }
            .onGloballyPositioned { coords ->
                val b = coords.boundsInWindow()
                if (b != lastRect) {
                    lastRect = b
                    onBounds(b)
                    center = b.center
                }
            }
            .pointerInput(song.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart(center) },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .glassPanel(
                    shape = RoundedCornerShape(10.dp),
                    isDark = isDark,
                    accent = ratingColor,
                    elevation = if (isDragging) 18.dp else 3.dp,
                    base = if (isDragging) 0.16f else 0.05f,
                ),
        ) {
            if (song.coverPath.isNotBlank()) {
                CoverImage(path = song.coverPath, modifier = Modifier.fillMaxSize())
            } else {
                CoverFallback(title = song.title, color = ratingColor, isDark = isDark)
            }
            // 底部只留一条薄暗角，压住名次
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
            )
            Text(
                text = rank.toString().padStart(2, '0'),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 3.dp),
            )
            if (song.linkUrl.isNotBlank()) {
                Text(
                    text = biliBadgeCompact(song.linkLabel),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 7.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .padding(horizontal = 2.5.dp, vertical = 0.5.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = song.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 9.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 1.dp),
        )
    }
}

@Composable
private fun CoverImage(path: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = remember(path) { File(context.filesDir, path) }
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** 没有曲绘时的兜底：应援色渐变 + 歌名首字，做成整张海报的观感。 */
@Composable
private fun CoverFallback(title: String, color: Color, isDark: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        color.copy(alpha = if (isDark) 0.55f else 0.42f),
                        lerp(color, Color.Black, if (isDark) 0.45f else 0.15f).copy(alpha = 0.75f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(1).ifBlank { "曲" },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptySlot(
    color: Color,
    modifier: Modifier = Modifier.size(132.dp).aspectRatio(3f / 4f),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "拖到这里",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            fontSize = 11.sp,
        )
    }
}

/** 筛选用的小胶囊：选中态点亮应援色，未选中只留一圈淡边。 */
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = if (isDark) 0.28f else 0.20f) else Color.Transparent,
            )
            .border(1.dp, accent.copy(alpha = if (selected) 0.72f else 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = if (selected) {
                if (isDark) lerp(accent, Color.White, 0.42f) else lerp(accent, Color(0xFF08161E), 0.52f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            },
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 小工具胶囊。 */
@Composable
private fun ToolChip(label: String, accent: Color, isDark: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = if (isDark) 0.16f else 0.14f))
            .border(1.dp, accent.copy(alpha = 0.42f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (isDark) lerp(accent, Color.White, 0.35f) else lerp(accent, Color(0xFF08161E), 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 密度开关：小图（网格）/ 大图（海报带）。 */
@Composable
private fun DensityChip(compact: Boolean, accent: Color, isDark: Boolean, onToggle: () -> Unit) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = if (isDark) 0.16f else 0.14f))
            .border(1.dp, accent.copy(alpha = 0.42f), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (compact) Icons.Filled.GridView else Icons.Filled.ViewAgenda,
            contentDescription = if (compact) "切换大图" else "切换小图",
            tint = if (isDark) lerp(accent, Color.White, 0.35f) else lerp(accent, Color(0xFF08161E), 0.55f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (compact) "小图" else "大图",
            color = if (isDark) lerp(accent, Color.White, 0.35f) else lerp(accent, Color(0xFF08161E), 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatPlays(plays: Int): String = when {
    plays >= 10000 -> "${plays / 10000}.${(plays % 10000) / 1000} 万"
    else -> "$plays"
}

/** 来源角标：官方投稿 / 播放最高的那支 / 只有搜索入口。 */
private fun biliBadge(label: String): String = when {
    label.contains("官方") -> "B站官方"
    label.contains("搜索") -> "B站搜索"
    label.contains("最高") -> "B站热门"
    else -> "B站"
}

/** 小图卡片的角标只有一格，用单字。 */
private fun biliBadgeCompact(label: String): String = when {
    label.contains("官方") -> "官"
    label.contains("搜索") -> "搜"
    else -> "B"
}

private const val UI_PREFS = "vmap_ui"
private const val KEY_COMPACT = "songs_compact"
