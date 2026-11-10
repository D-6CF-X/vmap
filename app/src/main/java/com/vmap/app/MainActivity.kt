package com.vmap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vmap.app.core.ui.VMapTheme
import com.vmap.app.core.ui.accent
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
            scrimColor = Color.Black.copy(alpha = 0.15f),
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "V-Map",
                            color = section.accent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "为歌声走过的城市",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DrawerSectionChip(
                                label = "依你随行",
                                selected = section == Section.TIANYI,
                                accent = Section.TIANYI.accent,
                                onClick = { viewModel.selectSection(Section.TIANYI) },
                                modifier = Modifier.weight(1f),
                            )
                            DrawerSectionChip(
                                label = "因音而遇",
                                selected = section == Section.MIKU,
                                accent = Section.MIKU.accent,
                                onClick = { viewModel.selectSection(Section.MIKU) },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.dp))

                        Page.entries.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                selected = page == item,
                                onClick = {
                                    page = item
                                    scope.launch { drawerState.close() }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = section.accent.copy(alpha = 0.16f),
                                    selectedIconColor = section.accent,
                                    selectedTextColor = section.accent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.dp))

                        NavigationDrawerItem(
                            label = { Text("分享点亮卡片") },
                            icon = { Icon(Icons.Filled.Share, contentDescription = "分享") },
                            selected = false,
                            onClick = {
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
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
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

                // 左上角汉堡按钮，打开抽屉（覆盖层，所有页面共用）
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "菜单",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
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

@Composable
private fun DrawerSectionChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}
