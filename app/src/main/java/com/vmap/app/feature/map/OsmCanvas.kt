package com.vmap.app.feature.map

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vmap.app.core.ui.MapIcons
import com.vmap.app.core.ui.accent
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * osmdroid 版地图。没有 API Key 时的兜底，任何人 clone 下来都能看到地图。
 *
 * 关键差异：OSM 瓦片是 WGS84 坐标系，而库里存的是 GCJ02，
 * 所以每个点画上去之前必须转一次，否则整体偏移 300~500 米。
 */
@Composable
fun OsmCanvas(
    modifier: Modifier,
    spots: List<Spot>,
    route: List<Spot>,
    section: Section,
    showRoute: Boolean,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(34.5, 108.9))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view -> renderOsm(view, context, spots, route, section, showRoute) },
    )
}

private fun renderOsm(
    mapView: MapView,
    context: Context,
    spots: List<Spot>,
    route: List<Spot>,
    section: Section,
    showRoute: Boolean,
) {
    // 内容没变就不重绘，否则每次重组都会闪一下，还会把用户拖走的地图拉回来
    val signature = spots.map { it.id to it.lat to it.lng }.hashCode() * 31 +
        route.size * 7 +
        section.ordinal * 3 +
        (if (showRoute) 1 else 0)
    if (mapView.tag == signature) return
    mapView.tag = signature

    mapView.overlays.clear()
    val accentArgb = section.accent.toArgb()

    if (showRoute && route.size >= 2) {
        val line = Polyline(mapView).apply {
            setPoints(route.map { it.toOsmPoint() })
            outlinePaint.color = accentArgb and 0x66FFFFFF
            outlinePaint.strokeWidth = 6f
            outlinePaint.isAntiAlias = true
        }
        mapView.overlays.add(line)

        route.zipWithNext { from, to ->
            val a = from.toOsmPoint()
            val b = to.toOsmPoint()
            val angle = MapIcons.bearing(from.lat, from.lng, to.lat, to.lng)
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(
                        (a.latitude + b.latitude) / 2,
                        (a.longitude + b.longitude) / 2,
                    )
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = BitmapDrawable(context.resources, MapIcons.arrowBitmap(accentArgb, angle))
                    setInfoWindow(null)
                },
            )
        }
    }

    spots.forEach { spot ->
        mapView.overlays.add(
            Marker(mapView).apply {
                position = spot.toOsmPoint()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = BitmapDrawable(context.resources, MapIcons.cityDotBitmap(accentArgb))
                setInfoWindow(null)
            },
        )
    }

    mapView.invalidate()

    mapView.post {
        if (spots.isEmpty()) return@post
        if (spots.size == 1) {
            mapView.controller.setZoom(8.0)
            mapView.controller.setCenter(spots.first().toOsmPoint())
        } else {
            val box = BoundingBox.fromGeoPoints(spots.map { it.toOsmPoint() })
            mapView.zoomToBoundingBox(box, false, 140)
        }
    }
}

/** 入库存 GCJ02，OSM 要 WGS84，转换只走 GeoPoint 这一个入口。 */
private fun Spot.toOsmPoint(): GeoPoint {
    val wgs = point.asWgs84()
    return GeoPoint(wgs.lat, wgs.lng)
}
