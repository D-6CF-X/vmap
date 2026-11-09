package com.vmap.app.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS84 与 GCJ02 之间的转换。整个项目只有这一处允许做坐标转换。
 *
 * 算法是公开的经典实现：对 WGS84 做一次非线性偏移得到 GCJ02；反过来没有解析解，
 * 用迭代逼近，三次足够把误差压到厘米级。
 */
object GeoConvert {

    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    fun wgs84ToGcj02(p: GeoPoint): GeoPoint {
        require(p.srid == Srid.WGS84) { "expected WGS84, got ${p.srid}" }
        if (outOfChina(p.lat, p.lng)) return p.copy(srid = Srid.GCJ02)

        var dLat = transformLat(p.lng - 105.0, p.lat - 35.0)
        var dLng = transformLng(p.lng - 105.0, p.lat - 35.0)

        val radLat = p.lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)

        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)

        return GeoPoint(p.lat + dLat, p.lng + dLng, Srid.GCJ02)
    }

    fun gcj02ToWgs84(p: GeoPoint): GeoPoint {
        require(p.srid == Srid.GCJ02) { "expected GCJ02, got ${p.srid}" }
        if (outOfChina(p.lat, p.lng)) return p.copy(srid = Srid.WGS84)

        var lat = p.lat
        var lng = p.lng
        repeat(3) {
            val gcj = wgs84ToGcj02(GeoPoint(lat, lng, Srid.WGS84))
            lat += p.lat - gcj.lat
            lng += p.lng - gcj.lng
        }
        return GeoPoint(lat, lng, Srid.WGS84)
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
