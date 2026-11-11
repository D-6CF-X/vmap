package com.vmap.app.data.song

import android.content.Context
import org.json.JSONObject

/**
 * 内置的历年演唱会曲目档案（由网易云音乐曲库整理：曲名、歌手、专辑、曲绘都以平台信息为准）。
 *
 * 一个文件放所有场次的去重曲目，每首歌记着自己被哪几年唱过、属于哪个系列、出场几次，
 * 界面按年份 / 系列筛，就是「分年份、分系列的排行」。
 */
data class ArchivedSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    /** assets 内的曲绘路径，如 arc/123456.jpg。 */
    val coverAsset: String,
    /** 板块：TIANYI / MIKU。 */
    val series: String,
    /** 演唱会系列名，如「无限共鸣·纯蓝幻乐」。 */
    val tour: String,
    /** 出场年份，逗号分隔。 */
    val years: String,
    /** 出场次数。 */
    val perfCount: Int,
)

object SongArchive {

    private const val ASSET = "song_archive.json"

    fun load(context: Context): List<ArchivedSong> = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val arr = JSONObject(text).optJSONArray("songs") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            val title = o.optString("title")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            ArchivedSong(
                id = id,
                title = title,
                artist = o.optString("artist"),
                album = o.optString("album"),
                coverAsset = o.optString("cover"),
                series = o.optString("series"),
                tour = o.optString("tour"),
                years = o.optString("years"),
                perfCount = o.optInt("perf", 0),
            )
        }
    }.getOrElse { emptyList() }
}
