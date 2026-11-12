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
    /**
     * 曲绘 assets 路径。**已由预处理脚本按优先级选好**：
     * B 站官方 MV 封面（官方曲绘） > 网易云单曲专辑封面（专属曲绘，通常带歌名） >
     * B 站最高播放视频封面 > 网易云合集封面（兜底）。
     * 所以运行时直接用这个字段，不再需要跑「网易云 vs B 站」的取舍逻辑。
     */
    val coverAsset: String,
    /**
     * B 站视频封面（assets 内路径）。按「官方投稿 > 最高播放 > 非官方」挑出来的那支视频，
     * 有就优先用它当曲绘，与跳转链接是同一个来源。
     */
    val biliCoverAsset: String = "",
    /** 板块：TIANYI / MIKU。 */
    val series: String,
    /** 演唱会系列名，如「无限共鸣·纯蓝幻乐」。 */
    val tour: String,
    /** 出场年份，逗号分隔。 */
    val years: String,
    /** 出场次数。 */
    val perfCount: Int,
    /** B 站视频链接（官方投稿优先，其次播放量最高的一支）。 */
    val linkUrl: String = "",
    /** 链接来源标签：B站官方 / B站最高播放 / B站非官方。 */
    val linkLabel: String = "",
    val linkAuthor: String = "",
    val linkPlay: Int = 0,
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
            val bili = o.optJSONObject("bili")
            ArchivedSong(
                id = id,
                title = title,
                artist = o.optString("artist"),
                album = o.optString("album"),
                coverAsset = o.optString("cover"),
                biliCoverAsset = bili?.optString("cover").orEmpty(),
                series = o.optString("series"),
                tour = o.optString("tour"),
                years = o.optString("years"),
                perfCount = o.optInt("perf", 0),
                linkUrl = bili?.optString("url").orEmpty(),
                linkLabel = bili?.optString("label").orEmpty(),
                linkAuthor = bili?.optString("author").orEmpty(),
                linkPlay = bili?.optInt("play", 0) ?: 0,
            )
        }
    }.getOrElse { emptyList() }
}
