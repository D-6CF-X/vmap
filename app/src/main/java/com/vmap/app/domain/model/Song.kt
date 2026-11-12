package com.vmap.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 一个评级档位（S / A / B / C …）。左侧自上而下的顺序由 [orderIndex] 决定，
 * 用户自己加、自己改色，不预设只能用这几档。
 */
@Entity(tableName = "song_ratings")
data class SongRating(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Long = 0xFF66CCFF,
    val orderIndex: Int = 0,
)

/**
 * 一首歌。[ratingId] 指向评级，[orderIndex] 是同评级内的手动排序（越小越靠上）。
 * [plays] 是听歌次数，可以手动填，也可以从平台导入时带进来。
 */
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String = "",
    val plays: Int = 0,
    val ratingId: String,
    val orderIndex: Int = 0,
    /** 来源：manual / netease / qq / kugou …，只做展示与去重提示。 */
    val source: String = "manual",
    /** 平台内的歌曲 id，导入去重用。 */
    val externalId: String = "",
    /** 曲绘 / 封面的相对路径（相对 filesDir），空表示还没上图。 */
    val coverPath: String = "",
    /** 候选曲绘的相对路径（逗号分隔），编辑弹窗里点一下就能快速换成这张。 */
    val altCoverAssets: String = "",
    /** 所属板块：TIANYI / MIKU，空表示手动添加、不属于任何演唱会。 */
    val series: String = "",
    /** 演唱会系列名，可能有好几个（同一首歌唱过好几轮），逗号分隔。手动添加的为空。 */
    val tour: String = "",
    /** 出场年份，逗号分隔（一首歌可能跨好几年都在唱），用于按年份筛选。 */
    val years: String = "",
    /** 在历次演唱会上唱过几次，作为鉴赏排行的初始依据。 */
    val perfCount: Int = 0,
    /** 专辑名（网易云曲库给的信息）。 */
    val album: String = "",
    /** B 站视频链接：官号投稿优先，没有官号就取播放量最高的一支。 */
    val linkUrl: String = "",
    /** 链接来源标签：B站官方 / B站最高播放 / B站非官方。 */
    val linkLabel: String = "",
    /** 该视频的 UP 主。 */
    val linkAuthor: String = "",
    /** 该视频的播放量。 */
    val linkPlay: Int = 0,
)
