package com.vmap.app.core.geo

/**
 * 坐标系。国内地图 App 必须有这个概念，否则标记会偏 300~500 米。
 *
 * - [WGS84] GPS 原始坐标，照片 EXIF 里就是这个
 * - [GCJ02] 国测局坐标，高德地图的定位与渲染都用它
 */
enum class Srid { WGS84, GCJ02 }

/**
 * 带坐标系的经纬度。裸 [Double] 不允许在业务层传递坐标。
 */
data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val srid: Srid,
) {
    init {
        require(lat in -90.0..90.0) { "lat out of range: $lat" }
        require(lng in -180.0..180.0) { "lng out of range: $lng" }
    }

    fun asGcj02(): GeoPoint =
        if (srid == Srid.GCJ02) this else GeoConvert.wgs84ToGcj02(this)

    fun asWgs84(): GeoPoint =
        if (srid == Srid.WGS84) this else GeoConvert.gcj02ToWgs84(this)
}
