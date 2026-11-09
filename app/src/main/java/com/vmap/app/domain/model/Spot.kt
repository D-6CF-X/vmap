package com.vmap.app.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vmap.app.core.geo.GeoPoint
import com.vmap.app.core.geo.Srid
import java.util.UUID

/**
 * 一次演出打卡。城市、坐标、演出信息、照片都挂在它下面。
 *
 * 坐标固定存 GCJ02，与高德渲染一致。手动点亮时城市中心来自高德行政区查询，
 * 本身就是 GCJ02，不需要转换。
 */
@Entity(tableName = "spots")
data class Spot(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val section: Section,
    val adcode: String,
    val cityName: String,
    val provinceName: String = "",
    val lat: Double,
    val lng: Double,
    val title: String,
    val venue: String = "",
    val performedAt: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    val synced: Boolean = false,
) {
    val point: GeoPoint
        get() = GeoPoint(lat, lng, Srid.GCJ02)
}
