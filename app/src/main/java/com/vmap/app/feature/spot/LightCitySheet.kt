package com.vmap.app.feature.spot

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vmap.app.VMapApp
import com.vmap.app.core.geo.GeoPoint
import com.vmap.app.core.geo.Srid
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.accent
import com.vmap.app.data.city.BuiltinCitySearcher
import com.vmap.app.data.city.CityHit
import com.vmap.app.data.city.CitySearchProvider
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightCitySheet(
    section: Section,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VMapApp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searcher: CitySearchProvider = remember { BuiltinCitySearcher() }
    val accent = section.accent

    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityHit>>(emptyList()) }
    var selected by remember { mutableStateOf<CityHit?>(null) }
    var title by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var performedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(keyword) {
        if (selected != null) return@LaunchedEffect
        if (keyword.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(450)
        searcher.search(keyword) { results = it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            GradientTitle(
                text = if (selected == null) "选择要点亮的城市" else "这场演出",
                accent = accent,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(14.dp))

            if (selected == null) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("搜索城市，如「上海」") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(results, key = { it.adcode }) { hit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = hit }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(hit.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                            Text(
                                hit.parent,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            } else {
                val city = selected!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = city.name,
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(0.dp))
                    TextButton(onClick = { selected = null; results = emptyList() }) {
                        Text("重选", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("演出名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("场馆") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Surface(
                    onClick = { showDatePicker = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "演出日期 · " +
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(performedAt)),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                val canSubmit = title.isNotBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (canSubmit) {
                                Brush.linearGradient(
                                    listOf(
                                        lerp(accent, Color.White, 0.35f),
                                        accent,
                                        lerp(accent, Color.Black, 0.15f),
                                    ),
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        Color.Gray.copy(alpha = 0.35f),
                                        Color.Gray.copy(alpha = 0.22f),
                                    ),
                                )
                            },
                        )
                        .clickable(enabled = canSubmit) {
                            scope.launch {
                                // CityHit 是 WGS84，入库统一 GCJ02
                                val gcj = GeoPoint(city.lat, city.lng, Srid.WGS84).asGcj02()
                                app.container.spotRepository.save(
                                    Spot(
                                        section = section,
                                        adcode = city.adcode,
                                        cityName = city.name,
                                        provinceName = city.parent,
                                        lat = gcj.lat,
                                        lng = gcj.lng,
                                        title = title.trim(),
                                        venue = venue.trim(),
                                        performedAt = performedAt,
                                        note = note.trim(),
                                        // 自己点亮的城市，天然属于「我的路线」
                                        mine = true,
                                    )
                                )
                                onDismiss()
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "点亮这座城",
                        color = if (canSubmit) Color(0xFF06151B) else Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
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
}
