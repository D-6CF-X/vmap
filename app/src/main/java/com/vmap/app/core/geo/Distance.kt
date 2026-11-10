package com.vmap.app.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 地球平均半径（公里）。 */
private const val EARTH_RADIUS_KM = 6371.0

/** 两点间的球面距离（公里），地图连线里程、年度回顾共用。 */
fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val s = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(s))
}
