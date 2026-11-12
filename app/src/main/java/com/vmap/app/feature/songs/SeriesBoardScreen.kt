package com.vmap.app.feature.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.data.song.SongArchive
import com.vmap.app.feature.web.WebViewerDialog
import java.io.File

/** 系列里的一首歌：只保留这张页面要用的字段，数据库和内置档案都能转成它。 */
private data class SeriesItem(
    val id: String,
    val title: String,
    /** 封面模型：File（库里）或 file:///android_asset/…（档案里）。 */
    val coverModel: Any?,
    val tour: String,
    val years: String,
    val series: String,
    val linkUrl: String,
    val linkLabel: String,
)

/** 一个演唱会系列（同名巡演的多场合并）。 */
private data class SeriesGroup(
    val name: String,
    val songs: List<SeriesItem>,
    val years: List<Int>,
)

/**
 * 系列歌单：按演唱会系列把曲目重新收一遍。
 *
 * 和「歌曲鉴赏」的分工：鉴赏页是**我的**评级墙（神/夯/顶/赞/稳，能拖能改）；
 * 这里放的是**官方系列**的曲目——同一首歌唱过几轮就出现在几个系列里，
 * 不参与评级，也不能被拖动，纯粹用来「按演出把歌收好」。
 *
 * 数据优先用数据库里的歌（带用户自己的封面与信息）；一旦库里没有系列字段
 * （老版本导入时 `tour` 是空的），直接退回读内置档案，保证页面不会一首都没有。
 */
@Composable
fun SeriesBoardScreen(
    viewModel: SongBoardViewModel,
    isDark: Boolean,
    accent: Color,
) {
    val context = LocalContext.current
    val allSongs by viewModel.songs.collectAsStateWithLifecycle()
    var seriesFilter by remember { mutableStateOf("") }
    var playingUrl by remember { mutableStateOf("") }

    // 库里那套（用户的封面、手动改过的字段都在）
    val dbItems = remember(allSongs, context) {
        allSongs.map { s ->
            SeriesItem(
                id = s.id,
                title = s.title,
                coverModel = if (s.coverPath.isNotBlank()) File(context.filesDir, s.coverPath) else null,
                tour = s.tour,
                years = s.years,
                series = s.series,
                linkUrl = s.linkUrl,
                linkLabel = s.linkLabel,
            )
        }
    }
    // 内置档案那套：库里没系列信息时的兜底，封面直接读 assets
    val archive = remember(context) { SongArchive.load(context) }
    val archiveItems = remember(archive) {
        archive.map { a ->
            val cover = a.biliCoverAsset.ifBlank { a.coverAsset }
            SeriesItem(
                id = "arc-${a.id}",
                title = a.title,
                coverModel = if (cover.isNotBlank()) "file:///android_asset/$cover" else null,
                tour = a.tour,
                years = a.years,
                series = a.series,
                linkUrl = a.linkUrl,
                linkLabel = a.linkLabel,
            )
        }
    }

    // 库里有系列信息就用库，没有就用档案（老数据 tour 为空的情况）
    val baseItems = if (dbItems.any { it.tour.isNotBlank() }) dbItems else archiveItems

    val groups = remember(baseItems, seriesFilter) {
        baseItems
            .filter { s -> seriesFilter.isBlank() || s.series == seriesFilter }
            .flatMap { s ->
                s.tour.split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map { it to s }
            }
            .groupBy({ it.first }, { it.second })
            .map { (name, list) ->
                SeriesGroup(
                    name = name,
                    songs = list.distinctBy { it.id },
                    years = list.flatMap { it.years.split(',') }
                        .mapNotNull { it.trim().toIntOrNull() }
                        .distinct()
                        .sorted(),
                )
            }
            // 最近还在演的排前面，同年按曲目多的排
            .sortedWith(
                compareByDescending<SeriesGroup> { it.years.maxOrNull() ?: 0 }
                    .thenByDescending { it.songs.size },
            )
    }

    val totalSongs = remember(groups) { groups.sumOf { it.songs.size } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 92.dp, bottom = 112.dp),
    ) {
        item(key = "header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                GradientTitle(text = "系列歌单", accent = accent, fontSize = 24.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${groups.size} 个系列 · ${totalSongs} 首 · 同一首歌唱过几轮，就进几个系列",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(9.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item(key = "all") {
                        SeriesChip("全部板块", seriesFilter.isBlank(), accent, isDark) { seriesFilter = "" }
                    }
                    item(key = "t") {
                        SeriesChip("依你随行", seriesFilter == "TIANYI", accent, isDark) { seriesFilter = "TIANYI" }
                    }
                    item(key = "m") {
                        SeriesChip("因音而遇", seriesFilter == "MIKU", accent, isDark) { seriesFilter = "MIKU" }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        items(groups, key = { it.name }) { group ->
            SeriesSection(
                group = group,
                isDark = isDark,
                accent = accent,
                onPlay = { url -> if (url.isNotBlank()) playingUrl = url },
            )
        }

        if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "还没有系列曲目——内置曲库导入完成后会自动出现",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp),
                )
            }
        }
    }

    if (playingUrl.isNotBlank()) {
        WebViewerDialog(url = playingUrl, isDark = isDark, onDismiss = { playingUrl = "" })
    }
}

/** 一个系列：标题条（系列名 + 年份 + 首数）+ 一排横滑的曲目。 */
@Composable
private fun SeriesSection(
    group: SeriesGroup,
    isDark: Boolean,
    accent: Color,
    onPlay: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.85f)),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = group.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = buildString {
                    if (group.years.isNotEmpty()) {
                        append(group.years.first())
                        if (group.years.size > 1) append("–").append(group.years.last())
                        append(" · ")
                    }
                    append(group.songs.size)
                    append(" 首")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(7.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(group.songs, key = { it.id }) { song ->
                SeriesSongCard(
                    song = song,
                    isDark = isDark,
                    accent = accent,
                    onClick = { onPlay(song.linkUrl) },
                )
            }
        }
    }
}

/** 系列里的一首歌：方形封面 + 歌名，有 B 站来源的右上角一个小角标。 */
@Composable
private fun SeriesSongCard(
    song: SeriesItem,
    isDark: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(11.dp))
                .glassPanel(
                    shape = RoundedCornerShape(11.dp),
                    isDark = isDark,
                    accent = accent,
                    elevation = 3.dp,
                    base = 0.05f,
                ),
        ) {
            val model = song.coverModel
            if (model != null) {
                AsyncImage(
                    model = model,
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
                                    accent.copy(alpha = 0.5f),
                                    lerp(accent, Color.Black, 0.4f).copy(alpha = 0.75f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = song.title.take(1).ifBlank { "曲" },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (song.linkUrl.isNotBlank()) {
                Text(
                    text = if (song.linkLabel.contains("官方")) "官" else "B",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 7.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 2.5.dp, vertical = 0.5.dp),
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = song.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 9.5.sp,
            maxLines = 2,
            lineHeight = 11.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 1.dp),
        )
    }
}

@Composable
private fun SeriesChip(
    label: String,
    selected: Boolean,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = if (isDark) 0.28f else 0.20f) else Color.Transparent)
            .border(1.dp, accent.copy(alpha = if (selected) 0.72f else 0.22f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
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
