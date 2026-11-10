package com.vmap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vmap.app.core.ui.GradientTitle
import com.vmap.app.core.ui.VMapTheme
import com.vmap.app.core.ui.accent
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.core.ui.gradientTextStyle
import com.vmap.app.data.share.ShareCardGenerator
import com.vmap.app.data.share.ShareHelper
import com.vmap.app.domain.model.Section
import com.vmap.app.feature.achievement.AchievementScreen
import com.vmap.app.feature.map.MapScreen
import com.vmap.app.feature.map.MapViewModel
import com.vmap.app.feature.review.YearReviewScreen
import com.vmap.app.feature.spot.LightCitySheet
import com.vmap.app.feature.timeline.TimelineScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VMapRoot()
        }
    }
}

private enum class Page(val label: String, val icon: ImageVector) {
    MAP("地图", Icons.Filled.Map),
    TIMELINE("时间线", Icons.Filled.Timeline),
    ACHIEVEMENT("成就", Icons.Filled.EmojiEvents),
    REVIEW("年度回顾", Icons.Filled.CalendarMonth),
}

@Composable
private fun VMapRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as VMapApp

    val viewModel: MapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MapViewModel(app.container.spotRepository) }
        },
    )

    val isDark by viewModel.isDark.collectAsStateWithLifecycle()

    VMapTheme(darkTheme = isDark) {
        var page by remember { mutableStateOf(Page.MAP) }
        var showLightSheet by remember { mutableStateOf(false) }
        val section by viewModel.section.collectAsStateWithLifecycle()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.26f),
            drawerContent = {
                VMapDrawer(
                    section = section,
                    page = page,
                    isDark = isDark,
                    onSelectSection = viewModel::selectSection,
                    onSelectPage = { item ->
                        page = item
                        scope.launch { drawerState.close() }
                    },
                    onShare = {
                        scope.launch {
                            drawerState.close()
                            val bitmap = ShareCardGenerator.generate(
                                section = section,
                                spots = viewModel.spots.value,
                                dark = isDark,
                            )
                            ShareHelper.saveAndShare(
                                context = context,
                                bitmap = bitmap,
                                title = "V-Map-${section.name}",
                            )
                        }
                    },
                    onClose = { scope.launch { drawerState.close() } },
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    Page.MAP -> MapScreen(
                        viewModel = viewModel,
                        onLightCity = { showLightSheet = true },
                        dimmed = showLightSheet,
                    )

                    Page.TIMELINE -> TimelineScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(top = 84.dp),
                    )

                    Page.ACHIEVEMENT -> AchievementScreen(viewModel = viewModel)
                    Page.REVIEW -> YearReviewScreen(viewModel = viewModel)
                }

                // 左上角汉堡按钮，打开抽屉（覆盖层，所有页面共用）；抽屉打开时淡出，避免露出半截
                AnimatedVisibility(
                    visible = drawerState.isClosed,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120)),
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(8.dp)
                            .size(40.dp)
                            .glassPanel(
                                shape = RoundedCornerShape(14.dp),
                                isDark = isDark,
                                accent = section.accent,
                                elevation = 10.dp,
                                base = 0.55f,
                            )
                            .clickable { scope.launch { drawerState.open() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        if (showLightSheet) {
            LightCitySheet(
                section = section,
                onDismiss = { showLightSheet = false },
            )
        }
    }
}

/** 玻璃抽屉：半透明雾面 + 应援色冷光 + 高光边，内容用统一的玻璃语言。 */
@Composable
private fun VMapDrawer(
    section: Section,
    page: Page,
    isDark: Boolean,
    onSelectSection: (Section) -> Unit,
    onSelectPage: (Page) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val accent = section.accent
    val shape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
    Column(
        modifier = Modifier
            .width(306.dp)
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 12.dp)
            .glassPanel(
                shape = shape,
                isDark = isDark,
                accent = accent,
                elevation = 30.dp,
                base = 0.80f,
            )
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                GradientTitle(text = "V-Map", accent = accent, fontSize = 27.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "为歌声走过的城市",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isDark) 0.07f else 0.45f))
                    .border(1.dp, Color.White.copy(alpha = if (isDark) 0.14f else 0.6f), CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DrawerSectionChip(
                label = "依你随行",
                selected = section == Section.TIANYI,
                accent = Section.TIANYI.accent,
                isDark = isDark,
                onClick = { onSelectSection(Section.TIANYI) },
                modifier = Modifier.weight(1f),
            )
            DrawerSectionChip(
                label = "因音而遇",
                selected = section == Section.MIKU,
                accent = Section.MIKU.accent,
                isDark = isDark,
                onClick = { onSelectSection(Section.MIKU) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(22.dp))
        DrawerCaption("浏览")
        Page.entries.forEach { item ->
            DrawerNavItem(
                icon = item.icon,
                label = item.label,
                selected = page == item,
                accent = accent,
                isDark = isDark,
                onClick = { onSelectPage(item) },
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color.White.copy(alpha = if (isDark) 0.10f else 0.55f))
        Spacer(Modifier.height(12.dp))

        DrawerNavItem(
            icon = Icons.Filled.Share,
            label = "分享点亮卡片",
            selected = false,
            accent = accent,
            isDark = isDark,
            onClick = onShare,
        )

        Spacer(Modifier.height(22.dp))

        // 底部提示卡：把抽屉下半部分的留白收住，同时告诉新用户怎么用
        val tipShape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tipShape)
                .background(Color.White.copy(alpha = if (isDark) 0.05f else 0.45f))
                .border(1.dp, Color.White.copy(alpha = if (isDark) 0.10f else 0.6f), tipShape)
                .padding(14.dp),
        ) {
            Text(
                text = "小提示",
                color = if (isDark) lerp(accent, Color.White, 0.3f) else accent,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "双指缩放地图 · 点击城市看演出详情 · 左上角随时切换板块",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "V-Map v0.1.0",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DrawerCaption(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 14.dp, bottom = 8.dp),
    )
}

/** 抽屉里的导航项：选中时玻璃片里透出应援色，左侧一条发光竖条。 */
@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.05f)),
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, accent.copy(alpha = 0.45f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(CircleShape)
                .background(if (selected) accent else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) lerp(accent, Color.White, 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            style = if (selected) {
                TextStyle(
                    color = if (isDark) lerp(accent, Color.White, 0.45f) else accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
            },
        )
    }
}

/** 板块切换胶囊：选中时是应援色玻璃 + 立体渐变字。 */
@Composable
private fun DrawerSectionChip(
    label: String,
    selected: Boolean,
    accent: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.40f), accent.copy(alpha = 0.12f)),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.07f else 0.55f),
                            Color.White.copy(alpha = if (isDark) 0.02f else 0.35f),
                        ),
                    )
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.60f) else Color.White.copy(alpha = if (isDark) 0.12f else 0.6f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (selected) {
                gradientTextStyle(accent, 14.sp)
            } else {
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            },
            textAlign = TextAlign.Center,
        )
    }
}
