package com.vmap.app.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.accent
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.domain.model.Spot
import com.vmap.app.feature.map.MapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** 一个月里的一组演出，时间线按月份分段。 */
private data class MonthGroup(
    val key: String,
    val label: String,
    val spots: List<Spot>,
)

@Composable
fun TimelineScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
) {
    val chronological by viewModel.chronological.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val accent = section.accent

    // 去重：同一板块、同一座城、同一天就算同一场（档案换过 id / 手点过一次会留两条）。
    // 保留信息最全的那条：我去过的 > 档案自动导入的 > 标题更完整的；只要有一边标了「去过」就合并成我的。
    val deduped = remember(chronological) {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US)
        chronological
            .groupBy {
                "${it.section}|${it.adcode.ifBlank { it.cityName }}|${day.format(Date(it.performedAt))}"
            }
            .map { (_, list) ->
                val keeper = list.maxWithOrNull(
                    compareBy<Spot> { if (it.mine) 1 else 0 }
                        .thenBy { if (it.id.startsWith("arc-")) 1 else 0 }
                        .thenBy { it.title.length }
                        .thenBy { it.updatedAt },
                ) ?: list.first()
                keeper.copy(
                    mine = list.any { it.mine },
                    detail = keeper.detail.ifBlank { list.firstOrNull { it.detail.isNotBlank() }?.detail.orEmpty() },
                    venue = keeper.venue.ifBlank { list.firstOrNull { it.venue.isNotBlank() }?.venue.orEmpty() },
                )
            }
            .sortedByDescending { it.performedAt }
    }

    val groups = remember(deduped) {
        deduped
            .groupBy { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.performedAt)) }
            .map { (key, spots) ->
                MonthGroup(
                    key = key,
                    label = key.replace("-", "."),
                    spots = spots.sortedByDescending { it.performedAt },
                )
            }
            .sortedByDescending { it.key }
    }

    val cityCount = deduped.map { it.adcode }.distinct().size

    // 删除要二次确认：点卡片上的垃圾桶先把这场存进待删，确认后才真的 softDelete
    var pendingDelete by remember { mutableStateOf<Spot?>(null) }
    val scope = rememberCoroutineScope()

    if (deduped.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有点亮的城市", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "回到地图，点右下角「点亮城市」开始记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 28.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 96.dp, bottom = 6.dp)) {
                GradientTitle(text = "时间线", accent = accent, fontSize = 24.sp)
                Text(
                    text = "${deduped.size} 场演出 · 走过 $cityCount 座城市",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        groups.forEach { group ->
            item(key = "head-${group.key}") {
                MonthHeader(label = group.label, count = group.spots.size, accent = accent)
            }
            items(group.spots, key = { it.id }) { spot ->
                TimelineCard(
                    spot = spot,
                    accent = accent,
                    isDark = isDark,
                    isLast = spot.id == group.spots.last().id,
                    onDelete = { pendingDelete = spot },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这场演出？") },
            text = {
                Text(
                    buildString {
                        append(target.cityName)
                        append(" · ")
                        append(target.title.ifBlank { "演出" })
                        append('\n')
                        append("删除后地图上的这条足迹和对应的一次点亮也会一起消失。")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val day = SimpleDateFormat("yyyyMMdd", Locale.US)
                    val key = "${target.section}|${target.adcode.ifBlank { target.cityName }}|${day.format(Date(target.performedAt))}"
                    // 连同这一场的重复条目一起删，避免删完还留一条一模一样的
                    val sameGroup = chronological.filter {
                        "${it.section}|${it.adcode.ifBlank { it.cityName }}|${day.format(Date(it.performedAt))}" == key
                    }
                    scope.launch {
                        sameGroup.forEach { viewModel.delete(it.id) }
                        if (sameGroup.isEmpty()) viewModel.delete(target.id)
                    }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 月份分段头：一条应援色短线 + 年月 + 场次。 */
@Composable
private fun MonthHeader(label: String, count: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count 场",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 11.sp,
        )
    }
}

/**
 * 一张演出卡：左侧时间轴（圆点 + 竖线）串起走过的路，右侧玻璃卡片。
 */
@Composable
private fun TimelineCard(
    spot: Spot,
    accent: Color,
    isDark: Boolean,
    isLast: Boolean,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // 时间轴
        Column(
            modifier = Modifier
                .width(26.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(accent.copy(alpha = 0.20f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(accent, CircleShape),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .weight(1f)
                        .background(accent.copy(alpha = 0.18f)),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.width(10.dp))

        // 玻璃卡片
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
                .glassPanel(
                    shape = shape,
                    isDark = isDark,
                    accent = accent,
                    elevation = 8.dp,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = SimpleDateFormat("MM.dd", Locale.getDefault()).format(Date(spot.performedAt)),
                    color = if (isDark) lerp(accent, Color.White, 0.35f) else accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 「我的」= 我真的去了；「官方」= 只是档案里开过，用来区分两条路线
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (spot.mine) accent.copy(alpha = 0.20f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = if (spot.mine) "我的" else "官方",
                            color = if (spot.mine) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = spot.cityName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "删除这场演出",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = spot.title.ifBlank { "演出" },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            if (spot.venue.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = spot.venue,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
