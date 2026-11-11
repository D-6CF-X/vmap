package com.vmap.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import com.vmap.app.core.ui.THEME_TRANSITION_MS
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.draw.blur
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
import com.vmap.app.core.ui.gradientColors
import com.vmap.app.domain.model.Board
import com.vmap.app.core.ui.glassPanel
import com.vmap.app.core.ui.gradientTextStyle
import com.vmap.app.data.share.ShareCardGenerator
import com.vmap.app.data.share.ShareHelper
import com.vmap.app.domain.model.Section
import com.vmap.app.feature.achievement.AchievementScreen
import com.vmap.app.feature.archive.ArchiveScreen
import com.vmap.app.feature.map.MapScreen
import com.vmap.app.feature.map.MapViewModel
import com.vmap.app.feature.review.YearReviewScreen
import com.vmap.app.feature.songs.SongBoardScreen
import com.vmap.app.feature.songs.SongBoardViewModel
import com.vmap.app.feature.spot.LightCitySheet
import com.vmap.app.feature.timeline.TimelineScreen
import kotlinx.coroutines.launch
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vmap.app.core.ui.DrawerWallpaper
import com.vmap.app.feature.wallpaper.WallpaperCropDialog
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.shadow
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset

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
    ARCHIVE("往音簿", Icons.Filled.LibraryMusic),
    ACHIEVEMENT("成就", Icons.Filled.EmojiEvents),
    REVIEW("年度回顾", Icons.Filled.CalendarMonth),
    SONGS("歌单排行", Icons.Filled.QueueMusic),
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

    val songViewModel: SongBoardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SongBoardViewModel(app.container.songDao, app.container.coverStore) }
        },
    )

    val isDark by viewModel.isDark.collectAsStateWithLifecycle()

    VMapTheme(darkTheme = isDark) {
        var page by remember { mutableStateOf(Page.MAP) }
        var showLightSheet by remember { mutableStateOf(false) }
        val section by viewModel.section.collectAsStateWithLifecycle()
        val board by viewModel.board.collectAsStateWithLifecycle()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // 主色跟着板块过渡，切到综合就是蓝绿中间色
        val accent = animateColorAsState(
            targetValue = board.accent,
            animationSpec = tween(THEME_TRANSITION_MS),
            label = "boardAccent",
        ).value

        // 侧栏壁纸：每个板块各存一张
        val boardKey = board.name.lowercase()
        var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
        var cropSource by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(boardKey) { wallpaper = DrawerWallpaper.load(context, boardKey) }
        val pickWallpaper = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                cropSource = runCatching {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val longEdge = maxOf(info.size.width, info.size.height)
                        if (longEdge > 2400) {
                            val r = 2400f / longEdge
                            decoder.setTargetSize(
                                (info.size.width * r).toInt().coerceAtLeast(1),
                                (info.size.height * r).toInt().coerceAtLeast(1),
                            )
                        }
                    }.asImageBitmap()
                }.getOrNull()
            }
        }

        // 抽屉打开时把底图糊掉，玻璃面板叠上去就是真磨砂
        val drawerProgress by animateFloatAsState(
            targetValue = if (drawerState.isOpen) 1f else 0f,
            animationSpec = tween(220),
            label = "drawerFrost",
        )

        // 非地图页的底色与光晕：日间冷雾蓝、夜间深蓝黑，跟随主题过渡
        val pageBgTop = animateColorAsState(
            targetValue = if (isDark) Color(0xFF0B0E13) else Color(0xFFF4F8FD),
            animationSpec = tween(THEME_TRANSITION_MS),
            label = "pageBgTop",
        ).value
        val pageBgBottom = animateColorAsState(
            targetValue = if (isDark) Color(0xFF101520) else Color(0xFFE6EDF8),
            animationSpec = tween(THEME_TRANSITION_MS),
            label = "pageBgBottom",
        ).value
        val glowStrength by animateFloatAsState(
            targetValue = if (isDark) 0.18f else 0.30f,
            animationSpec = tween(THEME_TRANSITION_MS),
            label = "pageGlow",
        )

        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = if (isDark) 0.30f else 0.20f),
            drawerContent = {
                VMapDrawer(
                    board = board,
                    page = page,
                    isDark = isDark,
                    accent = accent,
                    wallpaper = wallpaper,
                    onSelectBoard = viewModel::selectBoard,
                    onSelectPage = { item ->
                        page = item
                        scope.launch { drawerState.close() }
                    },
                    onPickWallpaper = {
                        pickWallpaper.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onClearWallpaper = {
                        DrawerWallpaper.clear(context, boardKey)
                        wallpaper = null
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
                                title = "V-Map-${board.name}",
                            )
                        }
                    },
                    onClose = { scope.launch { drawerState.close() } },
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (drawerProgress > 0.01f) {
                            Modifier.blur((14f * drawerProgress).dp)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                // 非地图页铺一层带应援色光晕的底，玻璃卡片才有东西可透，不然贴在纯色上像灰块
                if (page != Page.MAP) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRect(
                                    Brush.verticalGradient(listOf(pageBgTop, pageBgBottom)),
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(accent.copy(alpha = glowStrength), Color.Transparent),
                                    ),
                                    radius = size.width * 0.9f,
                                    center = Offset(size.width * 0.92f, size.height * 0.04f),
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(
                                            accent.copy(alpha = glowStrength * 0.55f),
                                            Color.Transparent,
                                        ),
                                    ),
                                    radius = size.width * 0.85f,
                                    center = Offset(size.width * 0.04f, size.height * 0.72f),
                                )
                            },
                    )
                }

                // 页面切换做淡入 + 轻微横向位移，不是硬切
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInHorizontally { it / 14 }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally { -it / 14 })
                    },
                    label = "pageSwitch",
                ) { current ->
                    when (current) {
                        Page.MAP -> MapScreen(
                            viewModel = viewModel,
                            onLightCity = { showLightSheet = true },
                            dimmed = showLightSheet || drawerState.isOpen,
                        )

                        Page.TIMELINE -> TimelineScreen(
                            viewModel = viewModel,
                            isDark = isDark,
                        )

                        Page.ARCHIVE -> ArchiveScreen(viewModel = viewModel, isDark = isDark)

                        Page.ACHIEVEMENT -> AchievementScreen(viewModel = viewModel, isDark = isDark)
                        Page.REVIEW -> YearReviewScreen(viewModel = viewModel, isDark = isDark)
                        Page.SONGS -> SongBoardScreen(
                            viewModel = songViewModel,
                            isDark = isDark,
                            accent = accent,
                        )
                    }
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
                                accent = accent,
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

        cropSource?.let { source ->
            WallpaperCropDialog(
                source = source,
                accent = accent,
                onDismiss = { cropSource = null },
                onConfirm = { bitmap ->
                    DrawerWallpaper.save(context, boardKey, bitmap)
                    wallpaper = bitmap.asImageBitmap()
                    cropSource = null
                },
            )
        }
    }
}

/** 玻璃抽屉：半透明雾面 + 应援色冷光 + 高光边，内容用统一的玻璃语言。 */
@Composable
private fun VMapDrawer(
    board: Board,
    page: Page,
    isDark: Boolean,
    accent: Color,
    wallpaper: ImageBitmap?,
    onSelectBoard: (Board) -> Unit,
    onSelectPage: (Page) -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp)

    // 壁纸用 BitmapShader 铺（cover），交给 background 绘制，尺寸由修饰符边界保证准确
    val wallpaperBrush = remember(wallpaper) {
        wallpaper?.let { img ->
            val bmp = img.asAndroidBitmap()
            object : ShaderBrush() {
                override fun createShader(size: androidx.compose.ui.geometry.Size): Shader {
                    val k = maxOf(size.width / bmp.width, size.height / bmp.height)
                    val matrix = Matrix().apply {
                        setScale(k, k)
                        postTranslate(
                            (size.width - bmp.width * k) / 2f,
                            (size.height - bmp.height * k) / 2f,
                        )
                    }
                    return BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                        setLocalMatrix(matrix)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .width(262.dp)
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 12.dp)
            .shadow(elevation = 30.dp, shape = shape, clip = false)
            .clip(shape)
            .then(if (wallpaperBrush != null) Modifier.background(wallpaperBrush) else Modifier)
            .then(
                if (wallpaperBrush != null) {
                    // 压暗壁纸：夜间压得重（要白字可读），日间轻一点，不然整块发灰
                    val dark = (1f - MaterialTheme.colorScheme.surface.luminance()).coerceIn(0f, 1f)
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.30f + 0.26f * dark),
                                Color.Black.copy(alpha = 0.68f + 0.18f * dark),
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(
                    shape = shape,
                    isDark = isDark,
                    accent = accent,
                    elevation = 0.dp,
                    // 有壁纸时玻璃让位给图；能实时模糊时也可以更透，磨砂靠底图模糊来撑
                    base = when {
                        wallpaper != null -> 0.24f
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> 0.38f
                        else -> 0.74f
                    },
                )
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                GradientTitle(text = "V-Map", accent = accent, fontSize = 23.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "为歌声走过的城市",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    letterSpacing = 1.1.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
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
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Board.entries.forEach { item ->
                DrawerBoardChip(
                    label = item.label,
                    selected = board == item,
                    accent = item.accent,
                    gradient = item.gradientColors,
                    isDark = isDark,
                    onClick = { onSelectBoard(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
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

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Color.White.copy(alpha = if (isDark) 0.10f else 0.55f))
        Spacer(Modifier.height(10.dp))

        DrawerNavItem(
            icon = Icons.Filled.Wallpaper,
            label = "侧栏壁纸",
            selected = false,
            accent = accent,
            isDark = isDark,
            onClick = onPickWallpaper,
            trailing = if (wallpaper != null) {
                { RemoveWallpaperChip(onClick = onClearWallpaper) }
            } else {
                null
            },
        )

        DrawerNavItem(
            icon = Icons.Filled.Share,
            label = "分享点亮卡片",
            selected = false,
            accent = accent,
            isDark = isDark,
            onClick = onShare,
        )

        Spacer(Modifier.height(18.dp))
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
}

@Composable
private fun DrawerCaption(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
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
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    // 玻璃很透，字压一层淡淡的阴影，落在壁纸/亮背景上也认得出
    val labelShadow = Shadow(Color.Black.copy(alpha = if (isDark) 0.45f else 0.20f), Offset(0f, 1f), 5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
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
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(15.dp)
                .clip(CircleShape)
                .background(if (selected) accent else Color.Transparent),
        )
        Spacer(Modifier.width(9.dp))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) lerp(accent, Color.White, 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = if (selected) {
                TextStyle(
                    color = if (isDark) {
                        lerp(accent, Color.White, 0.45f)
                    } else {
                        lerp(accent, Color(0xFF08161E), 0.62f)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = labelShadow,
                )
            } else {
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    shadow = labelShadow,
                )
            },
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** 有壁纸时导航项右侧的小"移除"胶囊。 */
@Composable
private fun RemoveWallpaperChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = "移除",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

/** 板块切换胶囊：选中时是应援色玻璃。综合模式用天依蓝→初音绿的双色渐变。 */
@Composable
private fun DrawerBoardChip(
    label: String,
    selected: Boolean,
    accent: Color,
    gradient: List<Color>,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    Brush.verticalGradient(
                        listOf(
                            gradient.first().copy(alpha = 0.42f),
                            gradient.last().copy(alpha = 0.16f),
                        ),
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
                brush = Brush.horizontalGradient(
                    if (selected) {
                        listOf(gradient.first().copy(alpha = 0.70f), gradient.last().copy(alpha = 0.70f))
                    } else {
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.12f else 0.6f),
                            Color.White.copy(alpha = if (isDark) 0.12f else 0.6f),
                        )
                    },
                ),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) {
                if (isDark) lerp(accent, Color.White, 0.42f) else lerp(accent, Color(0xFF08161E), 0.62f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
