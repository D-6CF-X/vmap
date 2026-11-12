package com.vmap.app.feature.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.geo.GeoPoint
import com.vmap.app.core.geo.Srid
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.accent
import com.vmap.app.data.archive.ArchiveEvent
import com.vmap.app.data.archive.ConcertArchive
import com.vmap.app.data.city.BuiltinCitySearcher
import com.vmap.app.data.city.CityHit
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.feature.map.MapViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 我的行程：把「官方开过哪些场」和「我实际去过哪」分开记。
 *
 * 上半部分是当年官方确认的演出，点一下勾「去过」，就记进我的路线；
 * 下半部分是我额外去的地方（旅行、见面会、别的演出），可以自己加、自己删。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTripSheet(
    viewModel: MapViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val section by viewModel.section.collectAsStateWithLifecycle()
    val allSpots by viewModel.allSpots.collectAsStateWithLifecycle()
    val accent = section.accent

    val events = remember { ConcertArchive.load(context) }
    val sectionEvents = remember(events, section) { events.filter { it.section == section } }
    val mineSpots = remember(allSpots, section) {
        allSpots.filter { it.section == section && it.mine && !it.deleted }
    }

    fun yearOf(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.YEAR)

    val years = remember(sectionEvents, mineSpots) {
        (sectionEvents.map { it.year } + mineSpots.map { yearOf(it.performedAt) })
            .distinct()
            .sortedDescending()
    }
    var year by remember(years) { mutableStateOf(years.firstOrNull() ?: yearOf(System.currentTimeMillis())) }

    val yearEvents = remember(sectionEvents, year) {
        sectionEvents.filter { it.year == year }.sortedBy { it.performedAt }
    }
    val yearExtras = remember(mineSpots, year) {
        mineSpots.filter { !it.id.startsWith("arc-") && yearOf(it.performedAt) == year }
            .sortedBy { it.performedAt }
    }
    val visitedCount = yearEvents.count { ev -> mineSpots.any { it.id == "arc-${ev.id}" || matches(it, ev) } }

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Spot?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    GradientTitle(text = "我的行程", accent = accent, fontSize = 21.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$year · 去过 $visitedCount/${yearEvents.size} 场官方演出 · 额外 ${yearExtras.size} 处",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 快速勾选：一年的场次太多时，一键全部记为去过 / 全部取消
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.10f))
                            .border(1.dp, accent.copy(alpha = 0.32f), CircleShape)
                            .clickable {
                                yearEvents.forEach { ev ->
                                    val visited = mineSpots.any { it.id == "arc-${ev.id}" || matches(it, ev) }
                                    if (!visited) viewModel.markArchive(ev, true)
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "全去过",
                            color = if (isDark) lerp(accent, Color.White, 0.3f) else lerp(accent, Color(0xFF08161E), 0.55f),
                            fontSize = 10.sp,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.45f), CircleShape)
                            .clickable { showAdd = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "＋ 额外",
                            color = if (isDark) lerp(accent, Color.White, 0.3f) else lerp(accent, Color(0xFF08161E), 0.55f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(years) { y ->
                    val selected = y == year
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (selected) accent.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = if (isDark) 0.05f else 0.45f),
                            )
                            .border(
                                1.dp,
                                if (selected) accent.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = if (isDark) 0.10f else 0.55f),
                                CircleShape,
                            )
                            .clickable { year = y }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "$y",
                            color = if (selected) {
                                if (isDark) lerp(accent, Color.White, 0.3f) else lerp(accent, Color(0xFF08161E), 0.55f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(modifier = Modifier.heightIn(max = 430.dp)) {
                if (yearEvents.isEmpty()) {
                    item {
                        Text(
                            text = "这一年档案里还没有官方场次",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )
                    }
                }
                items(yearEvents, key = { it.id }) { ev ->
                    val visited = mineSpots.any { it.id == "arc-${ev.id}" || matches(it, ev) }
                    EventRow(
                        event = ev,
                        visited = visited,
                        accent = accent,
                        isDark = isDark,
                        onToggle = { viewModel.markArchive(ev, !visited) },
                    )
                }

                if (yearExtras.isNotEmpty()) {
                    item {
                        Text(
                            text = "额外去的",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                        )
                    }
                    items(yearExtras, key = { it.id }) { spot ->
                        ExtraRow(
                            spot = spot,
                            accent = accent,
                            onDelete = { pendingDelete = spot },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddExtraDialog(
            section = section,
            accent = accent,
            defaultYear = year,
            onDismiss = { showAdd = false },
            onSave = { spot ->
                scope.launch {
                    viewModel.save(spot)
                    year = yearOf(spot.performedAt)
                }
                showAdd = false
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条行程？") },
            text = { Text("${target.cityName} · ${target.title}") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { viewModel.delete(target.id) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 官方档案条目与已打卡记录是否同一场（城市 + 同一天）。 */
private fun matches(spot: Spot, event: ArchiveEvent): Boolean {
    if (spot.adcode != event.adcode) return false
    val day = SimpleDateFormat("yyyyMMdd", Locale.US)
    return day.format(Date(spot.performedAt)) == day.format(Date(event.performedAt))
}

@Composable
private fun EventRow(
    event: ArchiveEvent,
    visited: Boolean,
    accent: Color,
    isDark: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (visited) accent.copy(alpha = if (isDark) 0.13f else 0.10f)
                else Color.White.copy(alpha = if (isDark) 0.04f else 0.35f),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = if (visited) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            Text(
                text = "${event.dateLabel} · ${event.city}",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (visited) accent else Color.Transparent)
                .border(
                    1.dp,
                    if (visited) accent else Color.White.copy(alpha = if (isDark) 0.22f else 0.6f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (visited) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "去过",
                    tint = Color(0xFF06151B),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun ExtraRow(spot: Spot, accent: Color, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spot.title.ifBlank { "个人行程" },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp,
            )
            Text(
                text = "${SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date(spot.performedAt))} · ${spot.cityName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 10.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 额外去过的地方：搜城市 + 选日期 + 一句话备注，存成 mine=true 的打卡。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExtraDialog(
    section: Section,
    accent: Color,
    defaultYear: Int,
    onDismiss: () -> Unit,
    onSave: (Spot) -> Unit,
) {
    val searcher = remember { BuiltinCitySearcher() }
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityHit>>(emptyList()) }
    var selected by remember { mutableStateOf<CityHit?>(null) }
    var note by remember { mutableStateOf("") }
    var performedAt by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(defaultYear, 6, 1, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis,
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(keyword) {
        if (selected != null) return@LaunchedEffect
        if (keyword.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(420)
        searcher.search(keyword) { results = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("额外去过的地方", fontSize = 16.sp) },
        text = {
            Column {
                if (selected == null) {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("搜索城市") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 210.dp)) {
                        items(results, key = { it.adcode }) { hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = hit; keyword = hit.name }
                                    .padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(hit.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(hit.parent, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selected!!.displayName,
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        TextButton(onClick = { selected = null; results = emptyList() }) {
                            Text("重选", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showDatePicker = true }
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "日期 · " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(performedAt)),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("去做什么（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = {
                    val city = selected ?: return@TextButton
                    val gcj = GeoPoint(city.lat, city.lng, Srid.WGS84).asGcj02()
                    onSave(
                        Spot(
                            section = section,
                            adcode = city.adcode,
                            cityName = city.name,
                            provinceName = city.parent,
                            lat = gcj.lat,
                            lng = gcj.lng,
                            title = note.trim().ifBlank { "个人行程" },
                            performedAt = performedAt,
                            mine = true,
                        ),
                    )
                },
            ) { Text("加入我的路线") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

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
        ) { DatePicker(state = dateState) }
    }
}
