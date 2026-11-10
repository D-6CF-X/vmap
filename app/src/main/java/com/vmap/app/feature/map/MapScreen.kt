package com.vmap.app.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vmap.app.core.geo.distanceKm
import com.vmap.app.core.ui.accent
import com.vmap.app.core.ui.glassPanel
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
                spots = spots,
                route = route,
                onToggleRoute = viewModel::toggleRoute,
                onToggleTheme = viewModel::toggleTheme,
                showRoute = showRoute,
                isDark = isDark,
            )

            if (tags.isNotEmpty()) {
                TagFilterBar(
                    tags = tags,
                    filterTag = filterTag,
                    accent = section.accent,
                    isDark = isDark,
                    onSelect = viewModel::setFilterTag,
                )
            }
        }

        LightCityButton(
            accent = section.accent,
            onClick = onLightCity,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 108.dp),
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

/** 顶部信息条：玻璃卡片 + 应援色立体数字 + 路线/主题开关。点信息条可在三种统计间切换。 */
@Composable
private fun SectionSwitchBar(
    accent: Color,
    stats: SectionStats,
    spots: List<Spot>,
    route: List<Spot>,
    onToggleRoute: () -> Unit,
    onToggleTheme: () -> Unit,
    showRoute: Boolean,
    isDark: Boolean,
) {
    val shape = RoundedCornerShape(22.dp)

    // 三种视角：城市/场次 → 累计里程 → 最常去
    var statPage by remember { mutableIntStateOf(0) }
    val totalKm = remember(route) {
        route.zipWithNext().sumOf { (a, b) -> distanceKm(a.lat, a.lng, b.lat, b.lng) }
    }
    val topCity = remember(spots) { spots.groupBy { it.cityName }.maxByOrNull { it.value.size } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(shape = shape, isDark = isDark, accent = accent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val numberColor = if (isDark) lerp(accent, Color.White, 0.15f) else accent
        Text(
            text = buildAnnotatedString {
                when (statPage) {
                    1 -> {
                        append("累计里程 ")
                        withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                            append("${totalKm.toInt()}")
                        }
                        append(" 公里")
                    }

                    2 -> {
                        if (topCity == null) {
                            append("还没有演出记录")
                        } else {
                            append("最常去 ")
                            withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                                append(topCity.key)
                            }
                            append(" · ${topCity.value.size} 次")
                        }
                    }

                    else -> {
                        append("已点亮 ")
                        withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                            append("${stats.cityCount}")
                        }
                        append(" 座城市 · ")
                        withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                            append("${stats.spotCount}")
                        }
                        append(" 场演出")
                    }
                }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.clickable { statPage = (statPage + 1) % 3 },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 路线开关：开启时玻璃胶囊里透出应援色
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (showRoute) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = if (isDark) 0.05f else 0.35f))
                    .border(
                        width = 1.dp,
                        color = if (showRoute) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = if (isDark) 0.12f else 0.55f),
                        shape = CircleShape,
                    )
                    .clickable(onClick = onToggleRoute)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (showRoute) "路线开" else "路线关",
                    color = if (showRoute) (if (isDark) lerp(accent, Color.White, 0.3f) else accent) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (showRoute) FontWeight.Medium else FontWeight.Normal,
                )
            }
            // 主题切换：小圆玻璃按钮
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isDark) 0.05f else 0.35f))
                    .border(1.dp, Color.White.copy(alpha = if (isDark) 0.12f else 0.55f), CircleShape)
                    .clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = if (isDark) "切到白天" else "切到夜间",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

/** 标签筛选栏：玻璃容器里横向滚动的 chips，「全部」+ 每个标签。 */
@Composable
private fun TagFilterBar(
    tags: List<Tag>,
    filterTag: String?,
    accent: Color,
    isDark: Boolean,
    onSelect: (String?) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(shape = RoundedCornerShape(20.dp), isDark = isDark, accent = accent, elevation = 12.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    text = "全部",
                    selected = filterTag == null,
                    accent = accent,
                    isDark = isDark,
                    onClick = { onSelect(null) },
                )
            }
            items(tags, key = { it.id }) { tag ->
                FilterChip(
                    text = tag.name,
                    selected = filterTag == tag.id,
                    accent = Color(tag.color),
                    isDark = isDark,
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
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) accent.copy(alpha = 0.20f)
                else Color.White.copy(alpha = if (isDark) 0.05f else 0.40f),
            )
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = if (isDark) 0.10f else 0.55f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) (if (isDark) lerp(accent, Color.White, 0.25f) else accent) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 主行动按钮：应援色渐变 + 外发光，比扁平 FAB 更有厚度。 */
@Composable
private fun LightCityButton(
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                spotColor = accent.copy(alpha = 0.55f),
                ambientColor = Color.Transparent,
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(accent, Color.White, 0.38f),
                        accent,
                        lerp(accent, Color.Black, 0.16f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.10f)),
                ),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = Color(0xFF05222E),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "点亮城市",
            color = Color(0xFF05222E),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
