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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Spot
import com.vmap.app.feature.map.MapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier,
) {
    val chronological by viewModel.chronological.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val accent = section.accent

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 24.dp),
    ) {
        itemsIndexed(
            items = chronological.reversed(),
            key = { _, spot -> spot.id },
        ) { index, spot ->
            TimelineNode(
                spot = spot,
                accent = accent,
                isLast = index == chronological.size - 1,
            )
        }
    }
}

/**
 * 侧栏式时间线：左侧一条应援色时间轴（圆点 + 竖线）串起走过的路，右侧是卡片。
 * 最新的在最上，一路向下到最早。
 */
@Composable
private fun TimelineNode(
    spot: Spot,
    accent: Color,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
    ) {
        // 左侧时间轴：圆点 + 竖线
        Column(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(accent.copy(alpha = 0.22f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(accent, CircleShape),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(accent.copy(alpha = 0.22f)),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // 右侧卡片
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 14.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                        .format(Date(spot.performedAt)),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = spot.cityName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (spot.title.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = spot.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                    )
                }
                if (spot.venue.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = spot.venue,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
