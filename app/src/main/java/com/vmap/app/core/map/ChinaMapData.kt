package com.vmap.app.core.map

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import org.json.JSONObject

/**
 * 一个省级行政区的形状。坐标已换算成虚拟坐标系：
 * x = (lng - 73) / 62 * 1000，y = (54 - lat) / 36 * 580。
 * 渲染时再按画布尺寸整体缩放，这样 Path 只需要构建一次。
 */
class ProvinceShape(
    val adcode: String,
    val name: String,
    val paths: List<Path>,
)

/**
 * 中国省级边界，来自阿里 DataV 的行政区划数据（简化到 3 位小数，约 400KB）。
 * 加载一次缓存复用；纯 Canvas 绘制，不依赖任何地图 SDK，也就没有
 * 瓦片加载、坐标系漂移和 16KB 页兼容这些问题。
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
                val polygonsRaw = when (val type = f.getString("type")) {
                    "Polygon" -> listOf(f.getJSONArray("coords"))
                    "MultiPolygon" -> {
                        val arr = f.getJSONArray("coords")
                        (0 until arr.length()).map { arr.getJSONArray(it) }
                    }
                    else -> {
                        // 未知几何类型，宁可跳过也不能崩
                        continue
                    }
                }
                val paths = polygonsRaw.mapNotNull { poly ->
                    try {
                        val path = Path()
                        val rings = if (poly is org.json.JSONArray) poly else return@mapNotNull null
                        for (r in 0 until rings.length()) {
                            val ring = rings.getJSONArray(r)
                            for (j in 0 until ring.length()) {
                                val pt = ring.getJSONArray(j)
                                val x = lngToX(pt.getDouble(0))
                                val y = latToY(pt.getDouble(1))
                                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            path.close()
                        }
                        path
                    } catch (e: Exception) {
                        null
                    }
                }
                if (paths.isNotEmpty()) {
                    result.add(ProvinceShape(f.getString("adcode"), f.getString("name"), paths))
                }
            }
            cached = result
            return result
        }
    }
}
