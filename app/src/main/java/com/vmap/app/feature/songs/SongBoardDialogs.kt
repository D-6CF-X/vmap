package com.vmap.app.feature.songs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vmap.app.domain.model.Song
import com.vmap.app.domain.model.SongRating
import com.vmap.app.feature.web.WebViewerDialog
import java.io.File

/** 评级可选的颜色，够用就好，不做完整取色器。 */
private val RATING_COLORS = listOf(
    0xFFFF6B8B, 0xFFFFB454, 0xFF66CCFF, 0xFF39C5BB,
    0xFFB48BFF, 0xFF8E9BAA, 0xFF7ED957,
)

/** 新增 / 编辑一首歌。曲绘可以从相册选，也可以随时移除。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongEditDialog(
    song: Song?,
    ratings: List<SongRating>,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        plays: Int,
        ratingId: String,
        coverUri: Uri?,
        removeCover: Boolean,
    ) -> Unit,
    onDelete: (() -> Unit)?,
    onPickAltCover: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var title by remember(song) { mutableStateOf(song?.title.orEmpty()) }
    var artist by remember(song) { mutableStateOf(song?.artist.orEmpty()) }
    var plays by remember(song) {
        mutableStateOf(if ((song?.plays ?: 0) > 0) song!!.plays.toString() else "")
    }
    var ratingId by remember(song, ratings) {
        mutableStateOf(song?.ratingId ?: ratings.firstOrNull()?.id.orEmpty())
    }
    var pickedCover by remember(song) { mutableStateOf<Uri?>(null) }
    var removeCover by remember(song) { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pickedCover = uri
            removeCover = false
        }
    }

    val existingPath = song?.coverPath.orEmpty()
    val coverModel: Any? = when {
        pickedCover != null -> pickedCover
        removeCover -> null
        existingPath.isNotBlank() -> File(context.filesDir, existingPath)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (song == null) "添加歌曲" else "编辑歌曲", fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 曲绘：点击直接换图
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .clickable {
                                coverPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (coverModel != null) {
                            AsyncImage(
                                model = coverModel,
                                contentDescription = "曲绘",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("＋", fontSize = 20.sp, color = accent)
                                Text("曲绘", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (coverModel == null) "选一张曲绘 / 封面" else "点图可更换",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "方形或竖向图效果最好",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (coverModel != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "移除曲绘",
                                fontSize = 11.sp,
                                color = Color(0xFFE05561),
                                modifier = Modifier.clickable {
                                    pickedCover = null
                                    removeCover = true
                                },
                            )
                        }
                    }
                }
                song?.takeIf { it.linkUrl.isNotBlank() }?.let { s ->
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.10f))
                            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                            .clickable { showLink = true }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Column {
                            Text(
                                text = "▶ 在 B 站看这支 · ${s.linkLabel.ifBlank { "B站" }}",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = listOf(
                                    s.linkAuthor.ifBlank { "未知 UP" },
                                    if (s.linkPlay > 0) "播放 ${formatLinkPlays(s.linkPlay)}" else "",
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                // 候选曲绘：这首歌的其他版本封面，点一下直接换
                val alts = song?.altCoverAssets?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() && it != song.coverPath }
                    .orEmpty()
                if (alts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("候选曲绘 · 点一下快速换", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        alts.forEach { alt ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.10f))
                                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .clickable { onPickAltCover(alt) },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = File(context.filesDir, alt),
                                    contentDescription = "候选曲绘",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("歌名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("歌手") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = plays,
                    onValueChange = { plays = it.filter { c -> c.isDigit() } },
                    label = { Text("播放次数（可留空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("评级", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ratings.forEach { rating ->
                        RatingPickChip(
                            rating = rating,
                            selected = ratingId == rating.id,
                            onClick = { ratingId = rating.id },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title.trim(),
                        artist.trim(),
                        plays.toIntOrNull() ?: 0,
                        ratingId,
                        pickedCover,
                        removeCover,
                    )
                },
                enabled = title.isNotBlank() && ratingId.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = Color(0xFFE05561))
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )

    if (showLink) {
        val link = song?.linkUrl.orEmpty()
        if (link.isNotBlank()) {
            WebViewerDialog(
                url = link,
                title = song?.title.orEmpty(),
                isDark = true,
                onDismiss = { showLink = false },
            )
        }
    }
}

private fun formatLinkPlays(plays: Int): String = when {
    plays >= 10000 -> "${plays / 10000}.${(plays % 10000) / 1000} 万"
    else -> "$plays"
}

@Composable
private fun RatingPickChip(rating: SongRating, selected: Boolean, onClick: () -> Unit) {
    val color = Color(rating.color)
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.28f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (selected) 0.8f else 0.3f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = rating.name,
            color = if (selected) lerp(color, Color.White, 0.25f) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 批量导入：一行一首。网易云 / QQ 音乐的听歌排行可以用「分享 → 复制」或网页版复制，
 * 粘进来按行解析，重复的（同名同歌手）自动跳过。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportDialog(
    ratings: List<SongRating>,
    accent: Color,
    onDismiss: () -> Unit,
    onImport: (text: String, ratingId: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var ratingId by remember(ratings) { mutableStateOf(ratings.lastOrNull()?.id.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入听歌数据", fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    text = "一行一首，支持「歌名」「歌名 - 歌手」「歌名 - 歌手 - 次数」，" +
                        "Tab、逗号分隔也认。可以从网易云/QQ 音乐的听歌排行里复制后粘贴进来。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text("晴天 - 周杰伦 - 128\n夜曲 - 周杰伦 - 96\n千本樱 - 初音ミク", fontSize = 12.sp)
                    },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("先放进哪个评级", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ratings.forEach { rating ->
                        RatingPickChip(
                            rating = rating,
                            selected = ratingId == rating.id,
                            onClick = { ratingId = rating.id },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(text, ratingId) },
                enabled = text.isNotBlank() && ratingId.isNotBlank(),
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 评级管理：新增、改色、删除。 */
@Composable
fun RatingDialog(
    ratings: List<SongRating>,
    accent: Color,
    isDark: Boolean,
    onAdd: (name: String, color: Long) -> Unit,
    onDelete: (SongRating) -> Unit,
    onRecolor: (SongRating, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableLongStateOf(RATING_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评级", fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ratings.forEach { rating ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(rating.color).copy(alpha = 0.26f))
                                .border(1.dp, Color(rating.color).copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = rating.name,
                                fontSize = if (rating.name.length > 2) 8.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = lerp(Color(rating.color), Color.White, 0.3f),
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            RATING_COLORS.take(5).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .clickable { onRecolor(rating, c) },
                                )
                            }
                        }
                        TextButton(onClick = { onDelete(rating) }) {
                            Text("删除", fontSize = 12.sp, color = Color(0xFFE05561))
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(3) },
                        label = { Text("新评级") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RATING_COLORS.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(if (c == color) 18.dp else 14.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(
                                        width = if (c == color) 2.dp else 0.dp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        shape = CircleShape,
                                    )
                                    .clickable { color = c },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        onAdd(name, color)
                        name = ""
                    },
                    enabled = name.isNotBlank(),
                ) { Text("＋ 添加评级") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}
