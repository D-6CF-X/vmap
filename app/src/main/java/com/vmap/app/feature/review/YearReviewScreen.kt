package com.vmap.app.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.accent
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.core.ui.gradientTextStyle
import com.vmap.app.domain.model.Spot
import com.vmap.app.feature.map.MapViewModel
import java.util.Calendar
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun YearReviewScreen(viewModel: MapViewModel, isDark: Boolean = true) {
    val allSpots by viewModel.allSpots.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val accent = section.accent

    val year = Calendar.getInstance().get(Calendar.YEAR)
    val thisYear = allSpots.filter { yearOf(it.performedAt) == year }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 96.dp, bottom = 28.dp),
    ) {
        GradientTitle(text = "$year 年度回顾", accent = accent, fontSize = 24.sp)
        Text(
            text = "这一年，你为歌声走过的路",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(18.dp))

        if (thisYear.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(RoundedCornerShape(20.dp), isDark, accent, elevation = 10.dp)
                    .padding(20.dp),
            ) {
                Text("今年还没有演出记录", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "点亮第一座城市，年末再来看看",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }

        // 线上活动没有坐标，城市数/里程都不算它
        val sorted = thisYear.filter { it.adcode.isNotBlank() }.sortedBy { it.performedAt }
        val cityCount = sorted.map { it.adcode }.distinct().size
        val provinceCount = thisYear.map { it.provinceName }.distinct().size
        val distanceKm = sorted.zipWithNext().sumOf { (a, b) -> distanceKm(a, b) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("场演出", thisYear.size.toString(), accent, isDark, Modifier.weight(1f))
            StatCard("座城市", cityCount.toString(), accent, isDark, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("个省份", provinceCount.toString(), accent, isDark, Modifier.weight(1f))
            StatCard("公里", distanceKm.toInt().toString(), accent, isDark, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        MonthRhythm(thisYear, accent, isDark)

        // 两条洞察：最长一程、最常去
        val leg = sorted.zipWithNext().maxByOrNull { (a, b) -> distanceKm(a, b) }
        val mostVisited = thisYear.groupBy { it.cityName }.maxByOrNull { it.value.size }

        Spacer(Modifier.height(10.dp))
        if (leg != null) {
            InsightRow(
                icon = "✈️",
                title = "最长一程",
                detail = "${leg.first.cityName} → ${leg.second.cityName}",
                value = "${distanceKm(leg.first, leg.second).toInt()} km",
                accent = accent,
                isDark = isDark,
            )
            Spacer(Modifier.height(10.dp))
        }
        mostVisited?.let { (city, list) ->
            InsightRow(
                icon = "📍",
                title = "去得最多",
                detail = city,
                value = "${list.size} 次",
                accent = accent,
                isDark = isDark,
            )
        }

        Spacer(Modifier.height(14.dp))

        // 走过的城市列表
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(RoundedCornerShape(20.dp), isDark, accent, elevation = 10.dp)
                .padding(16.dp),
        ) {
            Text(
                text = "走过的城市",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(10.dp))
            sorted.forEach { spot ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = spot.cityName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = spot.title.ifBlank { "演出" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 12 个月节奏条：有演出的月份亮起，柱高表示场次。 */
@Composable
private fun MonthRhythm(spots: List<Spot>, accent: Color, isDark: Boolean) {
    val perMonth = IntArray(12)
    spots.forEach { perMonth[monthOf(it.performedAt)] += 1 }
    val peak = perMonth.max().coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(20.dp), isDark, accent, elevation = 10.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "月度节奏",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "${perMonth.count { it > 0 }} 个月有演出",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            perMonth.forEachIndexed { index, count ->
                val h = if (count == 0) 4.dp else (8 + 34f * count / peak).dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(h)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (count == 0) {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isDark) 0.06f else 0.5f),
                                        Color.White.copy(alpha = if (isDark) 0.03f else 0.35f),
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(lerp(accent, Color.White, 0.45f), accent),
                                )
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(12) { index ->
                Text(
                    text = "${index + 1}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 8.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** 一条洞察：图标 + 标题 + 具体 + 数值。 */
@Composable
private fun InsightRow(
    icon: String,
    title: String,
    detail: String,
    value: String,
    accent: Color,
    isDark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(RoundedCornerShape(16.dp), isDark, accent, elevation = 8.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Text(
            text = value,
            color = if (isDark) lerp(accent, Color.White, 0.35f) else accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatCard(
    unit: String,
    value: String,
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .glassPanel(RoundedCornerShape(20.dp), isDark, accent, elevation = 10.dp)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = gradientTextStyle(accent, 24.sp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = unit,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

private fun yearOf(timestamp: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

private fun monthOf(timestamp: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.MONTH)

/** 两点间的粗略球面距离（公里），用于统计跨越的总里程。 */
private fun distanceKm(a: Spot, b: Spot): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val s = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) *
        sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * asin(sqrt(s))
}
