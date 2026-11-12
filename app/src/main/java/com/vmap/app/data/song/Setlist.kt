package com.vmap.app.data.song

import android.content.Context
import org.json.JSONObject

/**
 * 一场演出的实际歌单（节目单）。
 *
 * 曲目数据来自公开整理（QQ 音乐巡演歌单 / VCPedia 曲目表），
 * 和「系列歌单」的区别：系列是这一轮唱过的所有歌，这里是**这一站当晚**的曲目和顺序。
 */
data class Setlist(
    val id: String,
    val tour: String,
    val city: String,
    val year: Int,
    val date: String,
    val source: String,
    val tracks: List<String>,
)

object SetlistArchive {

    @Volatile
    private var cached: List<Setlist>? = null

    fun load(context: Context): List<Setlist> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val list = runCatching {
                val json = context.assets.open("setlists.json")
                    .bufferedReader().use { it.readText() }
                val arr = JSONObject(json).getJSONArray("setlists")
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val tracks = o.optJSONArray("tracks") ?: return@mapNotNull null
                    Setlist(
                        id = o.optString("id"),
                        tour = o.optString("tour"),
                        city = o.optString("city"),
                        year = o.optInt("year"),
                        date = o.optString("date"),
                        source = o.optString("source"),
                        tracks = (0 until tracks.length()).map { tracks.optString(it) },
                    )
                }
            }.getOrDefault(emptyList())
            cached = list
            return list
        }
    }

    /** 按「系列 + 城市 + 年份」找这一场的歌单，找不到返回 null（回退到系列级）。 */
    fun find(context: Context, tour: String, city: String, year: Int): Setlist? =
        load(context).firstOrNull { it.tour == tour && it.city == city && it.year == year }
}
