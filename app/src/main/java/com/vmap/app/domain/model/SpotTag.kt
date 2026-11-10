package com.vmap.app.domain.model

import androidx.room.Entity

/** 打卡与标签的多对多关联。 */
@Entity(tableName = "spot_tags", primaryKeys = ["spotId", "tagId"])
data class SpotTag(
    val spotId: String,
    val tagId: String,
)
