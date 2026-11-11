package com.vmap.app.feature.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * 曲目与曲绘来自网易云音乐的历年演唱会歌单（首次进入自动导入），
 * 顶部可以按板块 / 年份 / 演唱会系列筛，筛完就是「某年某轮巡演」的排行。
 * 每个评级一组横向滚动的大幅曲绘卡（3:4 竖版），曲绘是绝对主体，
 * 歌名 / 歌手 / 登场次数用渐变暗角压在图上，评级用左上角色块角标表示。
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

    // 筛选：板块 / 年份 / 演唱会系列。三者都能单独用，组合起来就是「某年某轮巡演」的排行。
    var seriesFilter by remember { mutableStateOf("") }
    var yearFilter by remember { mutableStateOf(0) }
    var tourFilter by remember { mutableStateOf("") }

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
    val songs = remember(allSongs, seriesFilter, yearFilter, tourFilter) {
        allSongs.filter { s ->
            (seriesFilter.isBlank() || s.series == seriesFilter) &&
                (yearFilter == 0 || s.years.split(',').any { it.trim() == yearFilter.toString() }) &&
                (tourFilter.isBlank() || s.tour.split(',').any { it.trim() == tourFilter })
        }
    }
    val filtering = seriesFilter.isNotBlank() || yearFilter != 0 || tourFilter.isNotBlank()

    // 按评级分组，组内按手动顺序；再按评级的 orderIndex 排序（S 在上）
    val grouped = remember(ratings, songs) {
        ratings.sortedBy { it.orderIndex }
            .map { r -> r to songs.filter { it.ratingId == r.id }.sortedBy { it.orderIndex } }
            .filter { (_, list) -> list.isNotEmpty() || !filtering }
    }
    // 未归属任何评级（理论上不该出现）的歌也兜住
    val ratingIds = ratings.map { it.id }.toSet()
    val unrated = remember(songs, ratingIds) { songs.filter { it.ratingId !in ratingIds } }

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

    fun commitDrag(song: Song) {
        val center = dragFrom + dragDelta
        val targetId = rowBounds.entries.firstOrNull { it.value.contains(center) }?.key ?: return
        val targetList = grouped.firstOrNull { it.first.id == targetId }?.second ?: return

        val known = targetList.mapIndexedNotNull { idx, s ->
            cardBounds[s.id]?.let { idx to it }
        }.filter { (_, r) ->
            val row = rowBounds[targetId] ?: return@filter false
            r.center.y >= row.top && r.center.y <= row.bottom
        }
        if (known.isEmpty()) return

        val before = known.count { it.second.center.x < center.x }
        val raw = known.getOrNull(before)?.first ?: (known.last().first + 1)

        val sameRow = song.ratingId == targetId
        val current = targetList.indexOfFirst { it.id == song.id }
        val target = if (sameRow && raw > current) raw - 1 else raw
        if (sameRow && target == current) return
        viewModel.moveSong(song.id, targetId, target)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 96.dp, bottom = 120.dp),
        ) {
            item(key = "header", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    GradientTitle(text = "歌曲鉴赏", accent = accent, fontSize = 24.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (filtering) {
                            "${songs.size} / ${allSongs.size} 首 · 按演唱会登场次数归档"
                        } else {
                            "${allSongs.size} 首 · 曲目与曲绘来自网易云音乐 · 长按曲绘拖动排序"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(10.dp))

                    // 板块：天依 / 初音 / 全部
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip("全部板块", seriesFilter.isBlank(), accent, isDark) { seriesFilter = "" }
                        FilterChip("依你随行", seriesFilter == "TIANYI", accent, isDark) { seriesFilter = "TIANYI" }
                        FilterChip("因音而遇", seriesFilter == "MIKU", accent, isDark) { seriesFilter = "MIKU" }
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

                    // 演唱会系列
                    if (tours.size > 1) {
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item(key = "tour-all") {
                                FilterChip("全部系列", tourFilter.isBlank(), accent, isDark) { tourFilter = "" }
                            }
                            tours.forEach { t ->
                                item(key = "tour-$t") {
                                    FilterChip(t, tourFilter == t, accent, isDark) {
                                        tourFilter = if (tourFilter == t) "" else t
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolChip("＋ 歌曲", accent, isDark) { showAdd = true }
                        ToolChip("导入", accent, isDark) { showImport = true }
                        ToolChip("评级", accent, isDark) { showRatings = true }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            grouped.forEach { (rating, list) ->
                item(key = "row-${rating.id}", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                    TierRow(
                        rating = rating,
                        songs = list,
                        yearFilter = yearFilter,
                        isDark = isDark,
                        draggingId = dragging?.id,
                        dragDelta = dragDelta,
                        onRowBounds = { rowBounds[rating.id] = it },
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

            if (unrated.isNotEmpty()) {
                item(key = "row-unrated", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                    TierRow(
                        rating = SongRating(name = "未分组", color = 0xFF8E9BAA, orderIndex = Int.MAX_VALUE),
                        songs = unrated,
                        yearFilter = yearFilter,
                        isDark = isDark,
                        draggingId = dragging?.id,
                        dragDelta = dragDelta,
                        onRowBounds = { rowBounds["unrated"] = it },
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

            if (grouped.isEmpty() && unrated.isEmpty()) {
                item(key = "no-rating", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
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

/** 一行 = 顶部评级标题条 + 横向大幅曲绘带。 */
@Composable
private fun TierRow(
    rating: SongRating,
    songs: List<Song>,
    yearFilter: Int,
    isDark: Boolean,
    draggingId: String?,
    dragDelta: Offset,
    onRowBounds: (Rect) -> Unit,
    onCardBounds: (String, Rect) -> Unit,
    onDragStart: (Song, Offset) -> Unit,
    onDrag: (Song, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onCardClick: (Song) -> Unit,
) {
    val color = Color(rating.color)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onRowBounds(it.boundsInWindow()) },
    ) {
        // 评级标题条：色块 + 档名 + 数量
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 6.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.30f))
                    .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = rating.name,
                    color = if (isDark) lerp(color, Color.White, 0.45f) else lerp(color, Color(0xFF08161E), 0.30f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${songs.size} 首",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp,
            )
        }

        // 横向曲绘带：大幅竖版海报卡
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (songs.isEmpty()) {
                item(key = "empty-${rating.id}") {
                    EmptySlot(color)
                }
            }
            songs.forEach { song ->
                item(key = song.id) {
                    SongPosterCard(
                        song = song,
                        rank = songs.indexOf(song) + 1,
                        yearFilter = yearFilter,
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
        Spacer(Modifier.height(6.dp))
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
        }
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
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptySlot(color: Color) {
    Box(
        modifier = Modifier
            .width(132.dp)
            .aspectRatio(3f / 4f)
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
private fun ToolChip(label: String, accent: Color, isDark: Boolean, onClick: () -> Unit) {    val shape = CircleShape
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

private fun formatPlays(plays: Int): String = when {
    plays >= 10000 -> "${plays / 10000}.${(plays % 10000) / 1000} 万"
    else -> "$plays"
}
