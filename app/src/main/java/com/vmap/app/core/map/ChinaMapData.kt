package com.vmap.app.core.map

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import org.json.JSONObject

/**
 * 一个城市的形状。坐标已换算成虚拟坐标系：
 * x = (lng - 73) / 62 * 1000，y = (54 - lat) / 36 * 580。
 * [rings] 是原始坐标点（用于点选判定），[path] 用于渲染，[center] 是外接框中心（用于放城市名）。
 */
class ProvinceShape(
    val adcode: String,
    val name: String,
    val rings: List<List<Offset>>,
    val path: Path,
    val center: Offset,
) {
    fun contains(x: Float, y: Float): Boolean =
        rings.any { ring -> pointInPolygon(ring, x, y) }
}

/**
 * 中国地级市边界，来自阿里 DataV（简化到 3 位小数，370 城约 2.4MB）。
 * 纯 Canvas 绘制，无地图 SDK 依赖。加载一次缓存复用。
 */
object ChinaMapData {

    private const val LNG_MIN = 73.0
    private const val LNG_MAX = 136.0
    private const val LAT_MAX = 54.0
    private const val LAT_MIN = 18.0
    const val VW = 1000f
    const val VH = 580f

    fun lngToX(lng: Double): Float =
        ((lng - LNG_MIN) / (LNG_MAX - LNG_MIN) * VW).toFloat()

    fun latToY(lat: Double): Float =
        ((LAT_MAX - lat) / (LAT_MAX - LAT_MIN) * VH).toFloat()

    @Volatile
    private var cached: List<ProvinceShape>? = null

    fun load(context: Context): List<ProvinceShape> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val json = context.assets.open("china_provinces.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val features = root.getJSONArray("features")
            val result = ArrayList<ProvinceShape>(features.length())
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val type = f.getString("type")

                // 收集所有环（外环 + 内环），Path 用全部环（even-odd 自动处理洞）
                val rings = ArrayList<List<Offset>>()
                when (type) {
                    "Polygon" -> collectRings(f.getJSONArray("coords"), rings)
                    "MultiPolygon" -> {
                        val arr = f.getJSONArray("coords")
                        for (p in 0 until arr.length()) {
                            collectRings(arr.getJSONArray(p), rings)
                        }
                    }
                    else -> continue
                }

                if (rings.isEmpty()) continue

                val path = Path()
                rings.forEach { ring ->
                    ring.forEachIndexed { j, pt ->
                        if (j == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    path.close()
                }

                // 外接框中心（城市名位置）
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                rings.forEach { ring ->
                    ring.forEach { pt ->
                        if (pt.x < minX) minX = pt.x
                        if (pt.y < minY) minY = pt.y
                        if (pt.x > maxX) maxX = pt.x
                        if (pt.y > maxY) maxY = pt.y
                    }
                }

                result.add(
                    ProvinceShape(
                        adcode = f.getString("adcode"),
                        name = f.getString("name"),
                        rings = rings,
                        path = path,
                        center = Offset((minX + maxX) / 2, (minY + maxY) / 2),
                    )
                )
            }
            cached = result
            return result
        }
    }

    private fun collectRings(coords: org.json.JSONArray, out: ArrayList<List<Offset>>) {
        for (r in 0 until coords.length()) {
            val ring = coords.getJSONArray(r)
            val points = ArrayList<Offset>(ring.length())
            for (j in 0 until ring.length()) {
                val pt = ring.getJSONArray(j)
                points.add(Offset(lngToX(pt.getDouble(0)), latToY(pt.getDouble(1))))
            }
            if (points.isNotEmpty()) out.add(points)
        }
    }

    /** 省级边界，仅用于描边。 */
    data class ProvinceBound(val path: Path)

    @Volatile
    private var cachedBounds: List<ProvinceBound>? = null

    /** 省级边界（34 个），画在地级市之上，勾勒清晰的省界。 */
    fun loadProvinceBounds(context: Context): List<ProvinceBound> {
        cachedBounds?.let { return it }
        synchronized(this) {
            cachedBounds?.let { return it }
            val json = context.assets.open("province_bounds.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val features = root.getJSONArray("features")
            val result = ArrayList<ProvinceBound>(features.length())
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val type = f.getString("type")
                val rings = ArrayList<List<Offset>>()
                when (type) {
                    "Polygon" -> collectRings(f.getJSONArray("coords"), rings)
                    "MultiPolygon" -> {
                        val arr = f.getJSONArray("coords")
                        for (p in 0 until arr.length()) {
                            collectRings(arr.getJSONArray(p), rings)
                        }
                    }
                    else -> continue
                }
                if (rings.isEmpty()) continue
                val path = Path()
                rings.forEach { ring ->
                    ring.forEachIndexed { j, pt ->
                        if (j == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                    }
                    path.close()
                }
                result.add(ProvinceBound(path))
            }
            cachedBounds = result
            return result
        }
    }

    /** 省级名字与中心（虚拟坐标）。全国视图只显示省名用。 */
    data class ProvinceLabel(val name: String, val x: Float, val y: Float)

    @Volatile
    private var cachedProvinceLabels: List<ProvinceLabel>? = null

    fun loadProvinceLabels(context: Context): List<ProvinceLabel> {
        cachedProvinceLabels?.let { return it }
        synchronized(this) {
            cachedProvinceLabels?.let { return it }
            val json = context.assets.open("provinces.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr = root.getJSONArray("provinces")
            val list = ArrayList<ProvinceLabel>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ProvinceLabel(
                        name = o.getString("name"),
                        x = lngToX(o.getDouble("lng")),
                        y = latToY(o.getDouble("lat")),
                    )
                )
            }
            cachedProvinceLabels = list
            return list
        }
    }
}

/** 射线法判定点是否在多边形内。 */
private fun pointInPolygon(poly: List<Offset>, x: Float, y: Float): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val xi = poly[i].x
        val yi = poly[i].y
        val xj = poly[j].x
        val yj = poly[j].y
        if ((yi > y) != (yj > y) &&
            x < (xj - xi) * (y - yi) / (yj - yi) + xi
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}
