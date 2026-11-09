package com.vmap.app.data.city

import android.content.Context
import com.amap.api.services.district.DistrictItem
import com.amap.api.services.district.DistrictResult
import com.amap.api.services.district.DistrictSearch
import com.amap.api.services.district.DistrictSearchQuery
import com.vmap.app.core.geo.GeoPoint
import com.vmap.app.core.geo.Srid

/**
 * 一次搜索命中的行政区。经纬度统一为 WGS84（两个搜索源都转成这个），
 * 入库前由调用方再转 GCJ02。
 */
data class CityHit(
    val adcode: String,
    val name: String,
    val parent: String,
    val lat: Double,
    val lng: Double,
) {
    val displayName: String
        get() = if (parent.isBlank() || parent == name) name else "$parent · $name"
}

/**
 * 用高德行政区查询找城市。每次搜索新建一个 DistrictSearch 实例，
 * 它不能复用，重复 search 会静默不回调。
 */
class CitySearcher(private val context: Context) : CitySearchProvider {

    override fun search(keyword: String, onResult: (List<CityHit>) -> Unit) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            onResult(emptyList())
            return
        }

        val query = DistrictSearchQuery().apply {
            keywords = trimmed
            isShowBoundary = false
            pageSize = 20
            pageNum = 0
        }

        runCatching {
            DistrictSearch(context).apply {
                this.query = query
                setOnDistrictSearchListener { result: DistrictResult? ->
                    val items = result?.district
                    val hits = ArrayList<CityHit>(items?.size ?: 0)
                    if (items != null) {
                        for (item in items) {
                            item.toHit()?.let(hits::add)
                        }
                    }
                    onResult(hits)
                }
                searchDistrictAsyn()
            }
        }.onFailure { onResult(emptyList()) }
    }

    /**
     * 注意 API 命名：高德 9.7.1 的 DistrictItem 用的是 getName() / getCenter()，
     * 不是常见的 getDistrictName() / getDistrictCenter()。改版本前先 javap 看一眼。
     *
     * center 是高德的 GCJ02，这里统一转成 WGS84 再交给上层。
     */
    private fun DistrictItem.toHit(): CityHit? {
        val c = center ?: return null
        val code = adcode
        if (code.isNullOrBlank()) return null
        val wgs = GeoPoint(c.latitude, c.longitude, Srid.GCJ02).asWgs84()
        return CityHit(
            adcode = code,
            name = name.orEmpty(),
            parent = "",
            lat = wgs.lat,
            lng = wgs.lng,
        )
    }
}
