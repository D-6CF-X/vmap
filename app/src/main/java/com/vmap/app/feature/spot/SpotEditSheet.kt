package com.vmap.app.feature.spot

import android.widget.Toast
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vmap.app.VMapApp
import com.vmap.app.core.ui.AnimatedGif
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.accent
import com.vmap.app.data.sticker.Sticker
import com.vmap.app.data.sticker.StickerPack
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.model.Tag
import com.vmap.app.feature.map.MapViewModel
import com.vmap.app.feature.web.WebViewerDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpotEditSheet(
    spot: Spot,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: MapViewModel,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VMapApp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = spot.section.accent

    var editing by remember { mutableStateOf(false) }
    var title by remember(spot) { mutableStateOf(spot.title) }
    var venue by remember(spot) { mutableStateOf(spot.venue) }
    var note by remember(spot) { mutableStateOf(spot.note) }
    var performedAt by remember(spot) { mutableStateOf(spot.performedAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var showNewTag by remember { mutableStateOf(false) }
    var showVideo by remember { mutableStateOf(false) }
    var detail by remember(spot) { mutableStateOf(spot.detail) }
    var videoUrl by remember(spot) { mutableStateOf(spot.videoUrl) }
    // 2026「纯蓝幻乐」场次带官方表情包，点开看动图
    val stickers = remember(spot.title) { StickerPack.forSpot(spot.title) }
    var previewSticker by remember { mutableStateOf<Sticker?>(null) }

    val photos by app.container.spotRepository
        .observePhotos(spot.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val allTags by app.container.spotRepository
        .observeTags()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val spotTags by app.container.spotRepository
        .observeTagsOfSpot(spot.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(9),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            var ok = 0
            var fail = 0
            for (uri in uris) {
                val photo = app.container.photoStore.import(uri, spot.id)
                if (photo != null) {
                    app.container.spotRepository.addPhoto(photo)
                    ok++
                } else {
                    fail++
                }
            }
            importing = false
            val msg = when {
                ok > 0 && fail > 0 -> "已添加 $ok 张照片，$fail 张失败"
                ok > 0 -> "已添加 $ok 张照片"
                else -> "照片添加失败"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GradientTitle(
                    text = spot.cityName,
                    accent = accent,
                    fontSize = 22.sp,
                )
                Text(
                    text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                        .format(Date(performedAt)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            if (editing) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("演出名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("场馆") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { showDatePicker = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "日期 · " +
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(performedAt)),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (venue.isNotBlank()) {
                    Text(
                        text = venue,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                if (note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                // 演唱会详情（往期档案带来的长介绍）
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                // 视频入口：有 B 站链接时显示，点开走内置浏览器
                if (videoUrl.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.10f)),
                                ),
                            )
                            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .clickable { showVideo = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "▶",
                                color = accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "观看演出视频",
                                    color = if (spot.section == com.vmap.app.domain.model.Section.TIANYI) {
                                        Color(0xFF0B1F2B)
                                    } else accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "哔哩哔哩 · 应用内打开",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
                // 官方表情包：只有 2026「无限共鸣·纯蓝幻乐」这一套
                if (stickers.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "纯蓝幻乐表情包 · ${stickers.size} 张",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stickers.forEach { st ->
                            item(key = st.asset) {
                                Column(
                                modifier = Modifier
                                    .width(64.dp)
                                    .clickable { previewSticker = st },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(accent.copy(alpha = 0.10f))
                                        .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp)),
                                ) {
                                    // 缩略图用 Coil 取首帧（不引 coil-gif），点开才是动图
                                    AsyncImage(
                                        model = "file:///android_asset/${st.asset}",
                                        contentDescription = st.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp),
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = st.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 标签：点选打上/取消，末尾是新建入口
            Text(
                text = "标签",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allTags.forEach { tag ->
                    val selected = spotTags.any { it.id == tag.id }
                    TagChip(tag = tag, selected = selected, onClick = {
                        scope.launch {
                            if (selected) {
                                app.container.spotRepository.removeTag(spot.id, tag.id)
                            } else {
                                app.container.spotRepository.assignTag(spot.id, tag.id)
                            }
                        }
                    })
                }
                Surface(
                    onClick = { showNewTag = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "+ 新建",
                        color = accent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (photos.isEmpty() && !importing) {
                Text(
                    text = "还没有照片",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                ) {
                    items(photos, key = { it.id }) { photo ->
                        AsyncImage(
                            model = File(app.filesDir, photo.path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    if (importing) {
                        item {
                            Box(modifier = Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accent, modifier = Modifier.height(28.dp).width(28.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !importing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent.copy(alpha = 0.18f),
                    contentColor = accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加照片")
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        if (editing) {
                            scope.launch {
                                app.container.spotRepository.save(
                                    spot.copy(
                                        title = title.trim(),
                                        venue = venue.trim(),
                                        note = note.trim(),
                                        performedAt = performedAt,
                                    )
                                )
                                editing = false
                            }
                        } else {
                            editing = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (editing) "保存" else "编辑", color = accent)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.delete(spot.id)
                            onDeleted()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("删除", color = Color(0xFFE24B4A))
                }
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = performedAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { performedAt = it }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showNewTag) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTag = false },
            title = { Text("新建标签") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("标签名") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        scope.launch {
                            val color = TAG_COLORS[allTags.size % TAG_COLORS.size]
                            val tag = app.container.spotRepository.addTag(newName, color)
                            app.container.spotRepository.assignTag(spot.id, tag.id)
                            showNewTag = false
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showNewTag = false }) { Text("取消") }
            },
        )
    }

    previewSticker?.let { st ->
        Dialog(onDismissRequest = { previewSticker = null }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0B1B26).copy(alpha = 0.94f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedGif(st.asset, Modifier.size(240.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = st.label,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (showVideo && videoUrl.isNotBlank()) {
        WebViewerDialog(
            url = videoUrl,
            title = spot.title,
            isDark = true,
            onDismiss = { showVideo = false },
        )
    }
}

@Composable
private fun TagChip(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Color(tag.color).copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = tag.name,
            color = if (selected) Color(tag.color) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 新建标签时循环分配的预设色。 */
private val TAG_COLORS = listOf(
    0xFF66CCFF, 0xFF39C5BB, 0xFFFFB74D, 0xFFE57373, 0xFFBA68C8, 0xFF81C784,
)
