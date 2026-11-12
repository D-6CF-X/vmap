package com.vmap.app.feature.archive

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.accent
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.data.archive.ArchiveEvent
import com.vmap.app.data.archive.ConcertArchive
import com.vmap.app.domain.model.Section
import com.vmap.app.feature.map.MapViewModel
import kotlinx.coroutines.launch

/**
 * 往音簿：历年演出档案。
 *
 * 按年份倒序展示洛天依与初音未来在中国（含港澳）的主要演出，
 * 任意一条可以一键「快速添加」——转成自己的打卡记录，地图上就会按年份点亮。
 */
@Composable
fun ArchiveScreen(
    viewModel: MapViewModel,
    isDark: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val events = remember { ConcertArchive.load(context) }
    val existing by viewModel.allSpots.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(Section.TIANYI) }
    val accentNow = section.accent

    val filtered = remember(events, section) { events.filter { it.section == section } }
    val byYear = remember(filtered) {
        filtered.groupBy { it.year }.entries.sortedByDescending { it.key }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 96.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                GradientTitle(text = "往音簿", accent = accentNow, fontSize = 27.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "历年散场的夜晚，都在这里",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArchiveSectionChip(
                    label = "依你随行",
                    selected = section == Section.TIANYI,
                    accent = Section.TIANYI.accent,
                    isDark = isDark,
                    onClick = { section = Section.TIANYI },
                    modifier = Modifier.weight(1f),
                )
                ArchiveSectionChip(
                    label = "因音而遇",
                    selected = section == Section.MIKU,
                    accent = Section.MIKU.accent,
                    isDark = isDark,
                    onClick = { section = Section.MIKU },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        byYear.forEach { (year, list) ->
            item(key = "y$year") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$year",
                        color = accentNow,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "年 · ${list.size} 场",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accentNow.copy(alpha = 0.35f), Color.Transparent),
                                ),
                            ),
                    )
                }
            }
            items(list, key = { it.id }) { event ->
                val recorded = existing.any {
                    it.id == "arc-${event.id}" || (it.adcode == event.adcode && it.title == event.name)
                }
                ArchiveRow(
                    event = event,
                    accent = accentNow,
                    isDark = isDark,
                    recorded = recorded,
                    onRecord = {
                        if (!recorded) {
                            viewModel.markArchive(event, true)
                            Toast.makeText(context, "已记入我的行程 · ${event.city}", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

        item {
            Text(
                text = "档案来自 MIKU EXPO 官方巡演表、萌番百科「未来有你」与 Vsinger 公开资料，收录主要场次。",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

@Composable
private fun ArchiveRow(
    event: ArchiveEvent,
    accent: Color,
    isDark: Boolean,
    recorded: Boolean,
    onRecord: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(
                shape = RoundedCornerShape(18.dp),
                isDark = isDark,
                accent = accent,
                elevation = 8.dp,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.dateLabel,
                color = accent.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = event.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
            )
            val place = listOf(event.city, event.venue)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (place.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = place,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            if (event.note.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = event.note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        RecordChip(
            accent = accent,
            isDark = isDark,
            recorded = recorded,
            onClick = onRecord,
        )
    }
}

/** 右侧小胶囊：快速添加 / 已添加。 */
@Composable
private fun RecordChip(
    accent: Color,
    isDark: Boolean,
    recorded: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (recorded) Color.White.copy(alpha = if (isDark) 0.05f else 0.40f)
                else accent.copy(alpha = 0.18f),
            )
            .border(
                width = 1.dp,
                color = if (recorded) Color.White.copy(alpha = if (isDark) 0.10f else 0.5f)
                else accent.copy(alpha = 0.50f),
                shape = CircleShape,
            )
            .then(if (recorded) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = if (recorded) "已去过" else "去过",
            color = if (recorded) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                if (isDark) lerp(accent, Color.White, 0.28f) else lerp(accent, Color(0xFF08161E), 0.55f)
            },
            fontSize = 11.sp,
            fontWeight = if (recorded) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

@Composable
private fun ArchiveSectionChip(
    label: String,
    selected: Boolean,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.20f)
                else Color.White.copy(alpha = if (isDark) 0.05f else 0.45f),
            )
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.55f)
                else Color.White.copy(alpha = if (isDark) 0.12f else 0.55f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) {
                if (isDark) lerp(accent, Color.White, 0.30f) else lerp(accent, Color(0xFF08161E), 0.58f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
