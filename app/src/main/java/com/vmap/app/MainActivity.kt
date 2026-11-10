package com.vmap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vmap.app.core.ui.VMapTheme
import com.vmap.app.core.ui.accent
import com.vmap.app.feature.map.MapScreen
import com.vmap.app.feature.map.MapViewModel
import com.vmap.app.feature.spot.LightCitySheet
import com.vmap.app.feature.timeline.TimelineScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VMapRoot()
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    MAP("地图", Icons.Filled.Map),
    TIMELINE("时间线", Icons.Filled.Timeline),
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

    var tab by remember { mutableIntStateOf(0) }
    var showLightSheet by remember { mutableStateOf(false) }
    val section by viewModel.section.collectAsStateWithLifecycle()
    val isDark by viewModel.isDark.collectAsStateWithLifecycle()

    VMapTheme(darkTheme = isDark) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (Tab.entries[tab]) {
                Tab.MAP -> MapScreen(
                    viewModel = viewModel,
                    onLightCity = { showLightSheet = true },
                    dimmed = showLightSheet,
                )

                Tab.TIMELINE -> TimelineScreen(
                    viewModel = viewModel,
                    modifier = Modifier.padding(bottom = 84.dp),
                )
            }

            NavigationBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ) {
                Tab.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = section.accent,
                            selectedTextColor = section.accent,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
