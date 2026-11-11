package com.vmap.app.data.archive

import android.content.Context
import com.vmap.app.core.geo.GeoPoint
import com.vmap.app.core.geo.Srid
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import org.json.JSONObject
import java.util.Calendar

/**
 * 一场往期演出（档案条目）。坐标是城市中心的 WGS84，只用于地图点亮与连线，
 * 记入足迹时统一转成 GCJ02 入库。
 */
data class ArchiveEvent(
    val id: String,
    val section: Section,
    val year: Int,
    val dateLabel: String,
    val name: String,
    val city: String,
    val adcode: String,
    val lat: Double,
    val lng: Double,
    val venue: String,
    val note: String,
    val detail: String = "",
    val videoUrl: String = "",
    /** 纯线上演出：没有实体城市，地图上不点亮地块，巡演时只飘一条活动名。 */
    val online: Boolean = false,
) {
    /** 把 "2017.06.17" / "2019.11" 这类标签转成时间戳，缺日按 1 号、缺月按 1 月。 */
    val performedAt: Long
        get() {
            val parts = dateLabel.split(".")
            val y = parts.getOrNull(0)?.toIntOrNull() ?: year
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
            return Calendar.getInstance().apply {
                set(y, m - 1, d, 19, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

    /** 转成一条打卡记录（坐标 WGS84 → GCJ02）。id 固定为 arc-前缀，重复导入不会产生重复足迹。 */
    fun toSpot(): Spot {
        val gcj = if (online || adcode.isBlank()) {
            GeoPoint(0.0, 0.0, Srid.WGS84)
        } else {
            GeoPoint(lat, lng, Srid.WGS84).asGcj02()
        }
        return Spot(
            id = "arc-$id",
            section = section,
            // 线上活动没有城市：adcode 置空，地图不会点亮任何地块
            adcode = if (online) "" else adcode,
            cityName = city,
            provinceName = "",
            lat = gcj.lat,
            lng = gcj.lng,
            title = name,
            venue = venue,
            performedAt = performedAt,
            note = note,
            detail = detail,
            videoUrl = videoUrl,
        )
    }
}

/**
 * 历年演出档案。数据打包在 assets/concert_archive.json，纯离线，
 * 来源为 MIKU EXPO 官方 HISTORY、萌娘百科与 Vsinger 官方资料。
 */
object ConcertArchive {

    @Volatile
    private var cached: List<ArchiveEvent>? = null

    fun load(context: Context): List<ArchiveEvent> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val list = runCatching {
                val json = context.assets.open("concert_archive.json")
                    .bufferedReader().use { it.readText() }
                val arr = JSONObject(json).getJSONArray("events")
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val section = when (o.optString("section")) {
                        "MIKU" -> Section.MIKU
                        "TIANYI" -> Section.TIANYI
                        else -> return@mapNotNull null
                    }
                    ArchiveEvent(
                        id = o.getString("id"),
                        section = section,
                        year = o.getInt("year"),
                        dateLabel = o.optString("dateLabel"),
                        name = o.optString("name"),
                        city = o.optString("city"),
                        adcode = o.optString("adcode"),
                        lat = o.optDouble("lat", 0.0),
                        lng = o.optDouble("lng", 0.0),
                        venue = o.optString("venue"),
                        note = o.optString("note"),
                        detail = o.optString("detail"),
                        videoUrl = o.optString("videoUrl"),
                        online = o.optBoolean("online", false) || o.optString("city") == "线上",
                    )
                }.sortedByDescending { it.performedAt }
            }.getOrDefault(emptyList())
            cached = list
            return list
        }
    }
}
