package com.vmap.app.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionSwitchBar(
                section = section,
                stats = stats,
                onSelect = viewModel::selectSection,
                onToggleRoute = viewModel::toggleRoute,
                showRoute = showRoute,
            )
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
    section: Section,
    stats: SectionStats,
    onSelect: (Section) -> Unit,
    onToggleRoute: () -> Unit,
    showRoute: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionChip(
                    label = "依你随行",
                    selected = section == Section.TIANYI,
                    accent = Section.TIANYI.accent,
                    onClick = { onSelect(Section.TIANYI) },
                    modifier = Modifier.weight(1f),
                )
                SectionChip(
                    label = "因音而遇",
                    selected = section == Section.MIKU,
                    accent = Section.MIKU.accent,
                    onClick = { onSelect(Section.MIKU) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已点亮 ${stats.cityCount} 座城市 · ${stats.spotCount} 场演出",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Surface(
                    onClick = onToggleRoute,
                    shape = CircleShape,
                    color = if (showRoute) section.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = if (showRoute) "路线开" else "路线关",
                        color = if (showRoute) section.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
        )
    }
}
