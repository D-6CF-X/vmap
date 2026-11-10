package com.vmap.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 一个标签，两个板块共用（如「内场」「首场」「应援」「合影」）。
 * [color] 是 ARGB 整数。
 */
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Long = 0xFF66CCFF,
)
