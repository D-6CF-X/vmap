package com.vmap.app.domain.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 一张照片。[path] 是相对 `filesDir` 的路径，绝对路径换机必失效。
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = Spot::class,
            parentColumns = ["id"],
            childColumns = ["spotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("spotId")],
)
data class Photo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val spotId: String,
    val path: String,
    val width: Int = 0,
    val height: Int = 0,
    val takenAt: Long = System.currentTimeMillis(),
)
