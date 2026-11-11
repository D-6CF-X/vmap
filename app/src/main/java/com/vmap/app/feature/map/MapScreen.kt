package com.vmap.app.feature.map

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Layers
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
import com.vmap.app.core.ui.THEME_TRANSITION_MS
import com.vmap.app.core.ui.accent
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.domain.model.Board
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
    val board by viewModel.board.collectAsStateWithLifecycle()
    val spots by viewModel.spots.collectAsStateWithLifecycle()
    val route by viewModel.chronological.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val showRoute by viewModel.showRoute.collectAsStateWithLifecycle()
    val isDark by viewModel.isDark.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val filterTag by viewModel.filterTag.collectAsStateWithLifecycle()
    val yearLayers by viewModel.yearLayers.collectAsStateWithLifecycle()
    val focusYear by viewModel.focusYear.collectAsStateWithLifecycle()

    var detailSpot by remember { mutableStateOf<Spot?>(null) }

    // 板块切换时主色和"综合"混合进度都做过渡，地图不会瞬间变色
    val accent = animateColorAsState(
        targetValue = board.accent,
        animationSpec = tween(THEME_TRANSITION_MS),
        label = "boardAccent",
    ).value
    val mixProgress by animateFloatAsState(
        targetValue = if (board == Board.ALL) 1f else 0f,
        animationSpec = tween(THEME_TRANSITION_MS),
        label = "boardMix",
    )

    // 地图配色跟着主题一起过渡，不要瞬间变白/变黑
    val darkProgress by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        animationSpec = tween(THEME_TRANSITION_MS),
        label = "mapDarkness",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ChinaMapCanvas(
            modifier = Modifier.fillMaxSize(),
            spots = spots,
            route = route,
            accent = accent,
            mixed = board == Board.ALL,
            mixProgress = mixProgress,
            showRoute = showRoute,
            onSpotClick = { detailSpot = it },
            // 有弹窗盖着时冻结动画，避免后台地图每帧重绘拖慢对话框
            animated = detailSpot == null && !dimmed,
            isDark = isDark,
            darkProgress = darkProgress,
            yearLayers = yearLayers,
            focusYear = focusYear,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionSwitchBar(
                accent = accent,
                stats = stats,
                spots = spots,
                route = route,
                onToggleRoute = viewModel::toggleRoute,
                onToggleTheme = viewModel::toggleTheme,
                onToggleYearLayers = viewModel::toggleYearLayers,
                showRoute = showRoute,
                isDark = isDark,
                yearLayers = yearLayers,
            )

            // 年份图例：每个去过的年份一个色点，越早越淡；点一下只看那一年
            if (yearLayers && spots.isNotEmpty()) {
                YearLegendBar(
                    spots = spots,
                    accent = accent,
                    isDark = isDark,
                    focusYear = focusYear,
                    onSelect = viewModel::setFocusYear,
                )
            }

            // 综合模式下跨板块筛标签没有明确语义，先不显示
            if (tags.isNotEmpty() && board != Board.ALL) {
                TagFilterBar(
                    tags = tags,
                    filterTag = filterTag,
                    accent = accent,
                    isDark = isDark,
                    onSelect = viewModel::setFilterTag,
                )
            }
        }

        LightCityButton(
            accent = accent,
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
    onToggleYearLayers: () -> Unit,
    showRoute: Boolean,
    isDark: Boolean,
    yearLayers: Boolean,
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
        val numberColor = if (isDark) {
            lerp(accent, Color.White, 0.15f)
        } else {
            // 日间的应援色在白底上太浅，压深一档才看得清
            lerp(accent, Color(0xFF08161E), 0.55f)
        }
        Crossfade(
            targetState = statPage,
            animationSpec = tween(200),
            label = "statView",
            modifier = Modifier.clickable { statPage = (statPage + 1) % 3 },
        ) { current ->
            Text(
                text = buildAnnotatedString {
                    when (current) {
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
            )
        }
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
                    color = if (showRoute) {
                        if (isDark) lerp(accent, Color.White, 0.3f) else lerp(accent, Color(0xFF08161E), 0.58f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                    fontWeight = if (showRoute) FontWeight.Medium else FontWeight.Normal,
                )
            }
            // 年份分层开关：开启后地块按演出年份着色，越早越淡
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (yearLayers) accent.copy(alpha = 0.20f)
                        else Color.White.copy(alpha = if (isDark) 0.05f else 0.35f),
                    )
                    .border(
                        width = 1.dp,
                        color = if (yearLayers) accent.copy(alpha = 0.55f)
                        else Color.White.copy(alpha = if (isDark) 0.12f else 0.55f),
                        shape = CircleShape,
                    )
                    .clickable(onClick = onToggleYearLayers),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = if (yearLayers) "关闭年份分层" else "开启年份分层",
                    tint = if (yearLayers) {
                        if (isDark) lerp(accent, Color.White, 0.3f) else lerp(accent, Color(0xFF08161E), 0.58f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(17.dp),
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

/** 时间戳转年份。 */
private fun yearOf(timestamp: Long): Int {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = timestamp
    return cal.get(java.util.Calendar.YEAR)
}

/**
 * 年份图例：每个去过的年份一个色点，颜色深浅就是该年在年份范围里的位置（越早越淡）。
 * 点一下只看那一年，再点一下回到全部。
 */
@Composable
private fun YearLegendBar(
    spots: List<Spot>,
    accent: Color,
    isDark: Boolean,
    focusYear: Int?,
    onSelect: (Int?) -> Unit,
) {
    val years = remember(spots) { spots.map { yearOf(it.performedAt) }.distinct().sorted() }
    if (years.isEmpty()) return
    val minYear = years.first()
    val maxYear = years.last()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(shape = RoundedCornerShape(20.dp), isDark = isDark, accent = accent, elevation = 12.dp),
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                Text(
                    text = "年份",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                )
            }
            items(years) { year ->
                val strength = if (maxYear <= minYear) 1f
                else (year - minYear).toFloat() / (maxYear - minYear)
                val selected = focusYear == year
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) accent.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = if (isDark) 0.05f else 0.40f),
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) accent.copy(alpha = 0.60f)
                            else Color.White.copy(alpha = if (isDark) 0.10f else 0.55f),
                            shape = CircleShape,
                        )
                        .clickable { onSelect(year) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.26f + 0.74f * strength)),
                    )
                    Text(
                        text = year.toString(),
                        color = if (selected) {
                            if (isDark) lerp(accent, Color.White, 0.25f)
                            else lerp(accent, Color(0xFF08161E), 0.55f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
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
            color = if (selected) {
                if (isDark) lerp(accent, Color.White, 0.25f) else lerp(accent, Color(0xFF08161E), 0.55f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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
