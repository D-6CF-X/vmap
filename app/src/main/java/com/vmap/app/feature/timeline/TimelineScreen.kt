package com.vmap.app.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            items = chronological.reversed(),
            key = { _, spot -> spot.id },
        ) { index, spot ->
            if (index > 0) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 6.dp).width(14.dp).height(14.dp),
                )
            }
            TimelineCard(
                spot = spot,
                accent = accent,
                order = chronological.size - index,
            )
        }
    }
}

@Composable
private fun TimelineCard(
    spot: Spot,
    accent: androidx.compose.ui.graphics.Color,
    order: Int,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent, shape = CircleShape) {
                Box(modifier = Modifier.width(26.dp).height(26.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = order.toString(),
                        color = androidx.compose.ui.graphics.Color(0xFF06151B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spot.cityName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = spot.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(spot.performedAt)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}
