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
)
