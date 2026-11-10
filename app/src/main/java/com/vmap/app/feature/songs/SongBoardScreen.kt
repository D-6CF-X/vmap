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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.domain.model.Song
import com.vmap.app.domain.model.SongRating

/**
 * 歌单排行：左侧一列评级（自上而下 S/A/B/C…），右侧是按评级分组的歌曲卡。
 *
 * 长按歌曲卡可以拖动，跨组拖动就是改评级；松手即保存，顺序写进 orderIndex。
 */
@Composable
fun SongBoardScreen(
    viewModel: SongBoardViewModel,
    isDark: Boolean,
    accent: Color,
) {
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()

    val grouped = remember(ratings, songs) {
        ratings.map { r -> r to songs.filter { it.ratingId == r.id }.sortedBy { it.orderIndex } }
    }

    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }

    var editing by remember { mutableStateOf<Song?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showRatings by remember { mutableStateOf(false) }

    /** 手指拖到哪一项上，就把歌挪到那一项的位置（跨组等于改评级）。 */
    fun onDrag(song: Song, dy: Float) {
        dragDelta += dy
        val myKey = "s-${song.id}"
        val info = listState.layoutInfo
        val my = info.visibleItemsInfo.firstOrNull { it.key == myKey } ?: return
        val center = my.offset + my.size / 2f + dragDelta
        val over = info.visibleItemsInfo.firstOrNull { item ->
            item.key != myKey && center >= item.offset && center <= item.offset + item.size
        } ?: return
        val key = over.key as? String ?: return
        when {
            key.startsWith("s-") -> {
                val targetId = key.removePrefix("s-")
                grouped.forEach { (rating, list) ->
                    val index = list.indexOfFirst { it.id == targetId }
                    if (index >= 0 && targetId != song.id) {
                        viewModel.moveSong(song.id, rating.id, index)
                    }
                }
                dragDelta = 0f
            }

            // 拖到空档上就是换评级，落到该组第一个
            key.startsWith("empty-") -> {
                val ratingId = key.removePrefix("empty-")
                if (ratingId != song.ratingId) {
                    viewModel.moveSong(song.id, ratingId, 0)
                }
                dragDelta = 0f
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 96.dp, bottom = 120.dp),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(bottom = 14.dp)) {
                    GradientTitle(text = "歌单排行", accent = accent, fontSize = 24.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${songs.size} 首 · ${ratings.size} 个评级 · 长按拖动排序",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolChip("＋ 歌曲", accent, isDark) { showAdd = true }
                        ToolChip("导入", accent, isDark) { showImport = true }
                        ToolChip("评级", accent, isDark) { showRatings = true }
                    }
                }
            }

            grouped.forEach { (rating, list) ->
                if (list.isEmpty()) {
                    item(key = "empty-${rating.id}") {
                        SongRow(
                            song = null,
                            rating = rating,
                            isDark = isDark,
                            isDragging = false,
                            dragDelta = 0f,
                            onDragStart = {},
                            onDrag = {},
                            onDragEnd = {},
                            onClick = {},
                        )
                    }
                }
                itemsIndexed(list, key = { _, s -> "s-${s.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        rating = if (index == 0) rating else null,
                        isDark = isDark,
                        isDragging = draggingId == song.id,
                        dragDelta = if (draggingId == song.id) dragDelta else 0f,
                        onDragStart = {
                            draggingId = song.id
                            dragDelta = 0f
                        },
                        onDrag = { dy -> onDrag(song, dy) },
                        onDragEnd = {
                            draggingId = null
                            dragDelta = 0f
                        },
                        onClick = { editing = song },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (grouped.isEmpty()) {
                item(key = "no-rating") {
                    Text(
                        text = "还没有评级，点上面的「评级」加一个",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 24.dp),
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
            onSave = { title, artist, plays, ratingId ->
                viewModel.addSong(title, artist, plays, ratingId)
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
            onSave = { title, artist, plays, ratingId ->
                viewModel.updateSong(
                    song.copy(title = title, artist = artist, plays = plays, ratingId = ratingId),
                )
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

/**
 * 一行：左边评级徽章（只在组的第一行出现，后面用竖线延续），右边歌曲卡。
 * [song] 为 null 时表示该评级还空着，画一个虚线提示位。
 */
@Composable
private fun SongRow(
    song: Song?,
    rating: SongRating?,
    isDark: Boolean,
    isDragging: Boolean,
    dragDelta: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 2f else 0f),
        verticalAlignment = Alignment.Top,
    ) {
        // 左列：评级 / 延续线
        Column(
            modifier = Modifier.width(70.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (rating != null) {
                RatingBadge(rating, isDark)
            }
            if (song != null) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(48.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                        ),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        if (song == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 10.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "空档 · 拖动歌曲到这里",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
            return@Row
        }

        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
                .graphicsLayer {
                    if (isDragging) {
                        translationY = dragDelta
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                }
                .glassPanel(
                    shape = shape,
                    isDark = isDark,
                    accent = rating?.let { Color(it.color) } ?: Color(0xFF66CCFF),
                    elevation = if (isDragging) 18.dp else 6.dp,
                    base = if (isDragging) 0.62f else 0.46f,
                )
                .clickable(onClick = onClick)
                .pointerInput(song.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                    )
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (song.plays > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatPlays(song.plays),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun RatingBadge(rating: SongRating, isDark: Boolean) {
    val color = Color(rating.color)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (isDark) 0.22f else 0.18f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rating.name,
            color = if (isDark) lerp(color, Color.White, 0.35f) else lerp(color, Color(0xFF08161E), 0.35f),
            fontSize = if (rating.name.length > 2) 10.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun formatPlays(plays: Int): String = when {
    plays >= 10000 -> "${plays / 10000}.${(plays % 10000) / 1000} 万"
    else -> "$plays"
}
