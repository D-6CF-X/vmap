package com.vmap.app.feature.map

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.vmap.app.BuildConfig
import com.vmap.app.core.ui.MapIcons
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.repository.SectionStats
import com.vmap.app.feature.spot.SpotEditSheet

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onLightCity: () -> Unit,
) {
    val section by viewModel.section.collectAsStateWithLifecycle()
    val spots by viewModel.spots.collectAsStateWithLifecycle()
    val route by viewModel.chronological.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val showRoute by viewModel.showRoute.collectAsStateWithLifecycle()

    var detailSpot by remember { mutableStateOf<Spot?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        MapCanvas(
            modifier = Modifier.fillMaxSize(),
            spots = spots,
            route = route,
            section = section,
            showRoute = showRoute,
            onSpotClick = { detailSpot = it },
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
                .padding(20.dp),
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * 地图入口。有高德 Key 用高德，没有就降级到 osmdroid。
 *
 * 这么做的原因很实际：项目开源后，别人 clone 下来不会为了看一眼地图去申请 Key。
 * Key 为空时自动切到 osmdroid，地图照样能显示，只是数据源从高德换成 OSM。
 */
@Composable
private fun MapCanvas(
    modifier: Modifier,
    spots: List<Spot>,
    route: List<Spot>,
    section: Section,
    showRoute: Boolean,
    onSpotClick: (Spot) -> Unit,
) {
    if (BuildConfig.HAS_AMAP_KEY) {
        AmapCanvas(
            modifier = modifier,
            spots = spots,
            route = route,
            section = section,
            showRoute = showRoute,
            onSpotClick = onSpotClick,
        )
    } else {
        OsmCanvas(
            modifier = modifier,
            spots = spots,
            route = route,
            section = section,
            showRoute = showRoute,
        )
    }
}

@Composable
private fun AmapCanvas(
    modifier: Modifier,
    spots: List<Spot>,
    route: List<Spot>,
    section: Section,
    showRoute: Boolean,
    onSpotClick: (Spot) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    var aMap by remember { mutableStateOf<AMap?>(null) }
    var loaded by remember { mutableStateOf(false) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                map?.apply {
                    mapType = AMap.MAP_TYPE_NIGHT
                    uiSettings.isZoomControlsEnabled = false
                    uiSettings.isRotateGesturesEnabled = false
                    uiSettings.isTiltGesturesEnabled = false
                    uiSettings.isMyLocationButtonEnabled = false
                    setOnMapLoadedListener {
                        aMap = this
                        loaded = true
                    }
                    setOnMarkerClickListener { marker ->
                        val found = spots.firstOrNull { it.title == marker.snippet && it.cityName == marker.title }
                        if (found != null) {
                            onSpotClick(found)
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        },
    )

    LaunchedEffect(loaded, spots, route, section, showRoute) {
        val map = aMap ?: return@LaunchedEffect
        redraw(map, section, spots, route, showRoute)
    }

    LaunchedEffect(loaded, section) {
        val map = aMap ?: return@LaunchedEffect
        val targets = spots.ifEmpty { route }
        if (targets.isEmpty()) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(34.2, 108.9), 3.6f))
            return@LaunchedEffect
        }
        if (targets.size == 1) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(targets.first().lat, targets.first().lng),
                    9f,
                )
            )
        } else {
            val bounds = LatLngBounds.Builder().apply {
                targets.forEach { include(LatLng(it.lat, it.lng)) }
            }.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 160))
        }
    }
}

private fun redraw(
    map: AMap,
    section: Section,
    spots: List<Spot>,
    route: List<Spot>,
    showRoute: Boolean,
) {
    map.clear()
    val accentArgb = section.accent.toArgb()
    val dot = MapIcons.cityDot(accentArgb)

    if (showRoute && route.size >= 2) {
        val points = route.map { LatLng(it.lat, it.lng) }
        map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .width(7f)
                .color(accentArgb and 0x66FFFFFF),
        )
        route.zipWithNext { from, to ->
            val a = LatLng(from.lat, from.lng)
            val b = LatLng(to.lat, to.lng)
            val angle = MapIcons.bearing(a.latitude, a.longitude, b.latitude, b.longitude)
            map.addMarker(
                MarkerOptions()
                    .position(LatLng((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2))
                    .icon(MapIcons.arrow(accentArgb, angle))
                    .anchor(0.5f, 0.5f),
            )
        }
    }

    spots.forEach { spot ->
        map.addMarker(
            MarkerOptions()
                .position(LatLng(spot.lat, spot.lng))
                .icon(dot)
                .anchor(0.5f, 0.5f)
                .title(spot.cityName)
                .snippet(spot.title),
        )
    }
}
