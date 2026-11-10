package com.vmap.app.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Spot
import com.vmap.app.feature.map.MapViewModel
import java.util.Calendar
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun YearReviewScreen(viewModel: MapViewModel) {
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
            .padding(top = 84.dp, bottom = 24.dp),
    ) {
        Text(
            text = "$year 年度回顾",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "这一年，你为歌声走过的路",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        if (thisYear.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("今年还没有演出记录", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点亮第一座城市，年末再来看看",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
            return@Column
        }

        val sorted = thisYear.sortedBy { it.performedAt }
        val cityCount = thisYear.map { it.adcode }.distinct().size
        val provinceCount = thisYear.map { it.provinceName }.distinct().size
        val distanceKm = sorted.zipWithNext().sumOf { (a, b) -> distanceKm(a, b) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("场演出", thisYear.size.toString(), accent, Modifier.weight(1f))
            StatCard("座城市", cityCount.toString(), accent, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("个省份", provinceCount.toString(), accent, Modifier.weight(1f))
            StatCard("公里", distanceKm.toInt().toString(), accent, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // 走过的城市列表
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "走过的城市",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                sorted.forEach { spot ->
                    Text(
                        text = "${spot.cityName} · ${spot.title.ifBlank { "演出" }}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    unit: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = unit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

private fun yearOf(timestamp: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

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
