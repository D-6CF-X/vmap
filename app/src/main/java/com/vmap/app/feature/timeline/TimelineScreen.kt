package com.vmap.app.feature.timeline

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    val groups = remember(chronological) {
        chronological
            .sortedByDescending { it.performedAt }
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

    val cityCount = chronological.map { it.adcode }.distinct().size

    if (chronological.isEmpty()) {
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
                    text = "${chronological.size} 场演出 · 走过 $cityCount 座城市",
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
                )
            }
        }
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
                Text(
                    text = spot.cityName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
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
