package com.vmap.app.feature.map

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.model.Tag
import com.vmap.app.domain.repository.SectionStats
import com.vmap.app.feature.spot.SpotEditSheet

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onLightCity: () -> Unit,
    dimmed: Boolean = false,
) {
    val section by viewModel.section.collectAsStateWithLifecycle()
    val spots by viewModel.spots.collectAsStateWithLifecycle()
    val route by viewModel.chronological.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val showRoute by viewModel.showRoute.collectAsStateWithLifecycle()
    val isDark by viewModel.isDark.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val filterTag by viewModel.filterTag.collectAsStateWithLifecycle()

    var detailSpot by remember { mutableStateOf<Spot?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        ChinaMapCanvas(
            modifier = Modifier.fillMaxSize(),
            spots = spots,
            route = route,
            section = section,
            showRoute = showRoute,
            onSpotClick = { detailSpot = it },
            // 有弹窗盖着时冻结动画，避免后台地图每帧重绘拖慢对话框
            animated = detailSpot == null && !dimmed,
            isDark = isDark,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionSwitchBar(
                accent = section.accent,
                stats = stats,
                onToggleRoute = viewModel::toggleRoute,
                onToggleTheme = viewModel::toggleTheme,
                showRoute = showRoute,
                isDark = isDark,
            )

            if (tags.isNotEmpty()) {
                TagFilterBar(
                    tags = tags,
                    filterTag = filterTag,
                    onSelect = viewModel::setFilterTag,
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onLightCity,
            containerColor = section.accent,
            contentColor = Color(0xFF06151B),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 108.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("点亮城市", fontWeight = FontWeight.Medium) },
        )

        detailSpot?.let { spot ->
            SpotEditSheet(
                spot = spot,
                onDismiss = { detailSpot = null },
                onDeleted = { detailSpot = null },
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun SectionSwitchBar(
    accent: Color,
    stats: SectionStats,
    onToggleRoute: () -> Unit,
    onToggleTheme: () -> Unit,
    showRoute: Boolean,
    isDark: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已点亮 ${stats.cityCount} 座城市 · ${stats.spotCount} 场演出",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onToggleRoute,
                    shape = CircleShape,
                    color = if (showRoute) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = if (showRoute) "路线开" else "路线关",
                        color = if (showRoute) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Surface(
                    onClick = onToggleTheme,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (isDark) "切到白天" else "切到夜间",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

/** 标签筛选栏：横向滚动的 chips，「全部」+ 每个标签。 */
@Composable
private fun TagFilterBar(
    tags: List<Tag>,
    filterTag: String?,
    onSelect: (String?) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.large,
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    text = "全部",
                    selected = filterTag == null,
                    accent = MaterialTheme.colorScheme.onSurface,
                    onClick = { onSelect(null) },
                )
            }
            items(tags, key = { it.id }) { tag ->
                FilterChip(
                    text = tag.name,
                    selected = filterTag == tag.id,
                    accent = Color(tag.color),
                    onClick = { onSelect(tag.id) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
