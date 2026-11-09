package com.vmap.app.data.city

/**
 * 城市搜索的统一入口。两个实现：
 * - [CitySearcher]（高德，走 SDK，Android 15 及以下）
 * - [BuiltinCitySearcher]（内置列表，Android 16 及以上，避免加载高德 native 库）
 *
 * 两者的坐标统一是 WGS84，入库前再转 GCJ02。
 */
interface CitySearchProvider {
    fun search(keyword: String, onResult: (List<CityHit>) -> Unit)
}

/**
 * 内置主要城市列表。覆盖直辖市、省会、副省级城市和常见演出城市，
 * 演唱会基本不会去这些之外的地方。坐标是 WGS84。
 */
data class BuiltinCity(
    val name: String,
    val province: String,
    val adcode: String,
    val lat: Double,
    val lng: Double,
)

object BuiltinCities {

    val all: List<BuiltinCity> = listOf(
        // 直辖市
        BuiltinCity("北京", "北京市", "110000", 39.9042, 116.4074),
        BuiltinCity("上海", "上海市", "310000", 31.2304, 121.4737),
        BuiltinCity("天津", "天津市", "120000", 39.0851, 117.1999),
        BuiltinCity("重庆", "重庆市", "500000", 29.5630, 106.5516),
        // 省会 / 副省级
        BuiltinCity("广州", "广东省", "440100", 23.1291, 113.2644),
        BuiltinCity("深圳", "广东省", "440300", 22.5431, 114.0579),
        BuiltinCity("成都", "四川省", "510100", 30.5728, 104.0665),
        BuiltinCity("杭州", "浙江省", "330100", 30.2741, 120.1551),
        BuiltinCity("武汉", "湖北省", "420100", 30.5931, 114.3054),
        BuiltinCity("南京", "江苏省", "320100", 32.0603, 118.7969),
        BuiltinCity("西安", "陕西省", "610100", 34.3416, 108.9398),
        BuiltinCity("长沙", "湖南省", "430100", 28.2282, 112.9388),
        BuiltinCity("郑州", "河南省", "410100", 34.7466, 113.6254),
        BuiltinCity("沈阳", "辽宁省", "210100", 41.8057, 123.4315),
        BuiltinCity("大连", "辽宁省", "210200", 38.9140, 121.6147),
        BuiltinCity("济南", "山东省", "370100", 36.6512, 117.1205),
        BuiltinCity("青岛", "山东省", "370200", 36.0671, 120.3826),
        BuiltinCity("哈尔滨", "黑龙江省", "230100", 45.8038, 126.5349),
        BuiltinCity("长春", "吉林省", "220100", 43.8171, 125.3235),
        BuiltinCity("苏州", "江苏省", "320500", 31.2989, 120.5853),
        BuiltinCity("无锡", "江苏省", "320200", 31.4912, 120.3119),
        BuiltinCity("宁波", "浙江省", "330200", 29.8746, 121.5497),
        BuiltinCity("厦门", "福建省", "350200", 24.4798, 118.0894),
        BuiltinCity("福州", "福建省", "350100", 26.0745, 119.2965),
        BuiltinCity("合肥", "安徽省", "340100", 31.8206, 117.2272),
        BuiltinCity("南昌", "江西省", "360100", 28.6829, 115.8582),
        BuiltinCity("昆明", "云南省", "530100", 24.8801, 102.8329),
        BuiltinCity("贵阳", "贵州省", "520100", 26.6470, 106.6302),
        BuiltinCity("南宁", "广西壮族自治区", "450100", 22.8170, 108.3661),
        BuiltinCity("太原", "山西省", "140100", 37.8706, 112.5489),
        BuiltinCity("石家庄", "河北省", "130100", 38.0428, 114.5149),
        BuiltinCity("兰州", "甘肃省", "620100", 36.0611, 103.8343),
        BuiltinCity("西宁", "青海省", "630100", 36.6171, 101.7782),
        BuiltinCity("银川", "宁夏回族自治区", "640100", 38.4872, 106.2309),
        BuiltinCity("乌鲁木齐", "新疆维吾尔自治区", "650100", 43.8256, 87.6168),
        BuiltinCity("呼和浩特", "内蒙古自治区", "150100", 40.8426, 111.7492),
        BuiltinCity("拉萨", "西藏自治区", "540100", 29.6456, 91.1409),
        BuiltinCity("海口", "海南省", "460100", 20.0440, 110.1983),
        BuiltinCity("三亚", "海南省", "460200", 18.2528, 109.5119),
        // 其他常见演出城市
        BuiltinCity("东莞", "广东省", "441900", 23.0207, 113.7518),
        BuiltinCity("佛山", "广东省", "440600", 23.0215, 113.1214),
        BuiltinCity("珠海", "广东省", "440400", 22.2707, 113.5767),
        BuiltinCity("惠州", "广东省", "441300", 23.1115, 114.4162),
        BuiltinCity("汕头", "广东省", "440500", 23.3535, 116.6820),
        BuiltinCity("中山", "广东省", "442000", 22.5176, 113.3928),
        BuiltinCity("温州", "浙江省", "330300", 28.0006, 120.6994),
        BuiltinCity("嘉兴", "浙江省", "330400", 30.7460, 120.7555),
        BuiltinCity("绍兴", "浙江省", "330600", 30.0303, 120.5821),
        BuiltinCity("台州", "浙江省", "331000", 28.6564, 121.4206),
        BuiltinCity("金华", "浙江省", "330700", 29.0787, 119.6475),
        BuiltinCity("常州", "江苏省", "320400", 31.8107, 119.9741),
        BuiltinCity("南通", "江苏省", "320600", 31.9802, 120.8943),
        BuiltinCity("徐州", "江苏省", "320300", 34.2058, 117.2841),
        BuiltinCity("扬州", "江苏省", "321000", 32.3936, 119.4129),
        BuiltinCity("镇江", "江苏省", "321100", 32.1878, 119.4250),
        BuiltinCity("盐城", "江苏省", "320900", 33.3474, 120.1636),
        BuiltinCity("泉州", "福建省", "350500", 24.8741, 118.6757),
        BuiltinCity("洛阳", "河南省", "410300", 34.6197, 112.4540),
        BuiltinCity("烟台", "山东省", "370600", 37.4638, 121.4479),
        BuiltinCity("潍坊", "山东省", "370700", 36.7069, 119.1618),
        BuiltinCity("唐山", "河北省", "130200", 39.6305, 118.1802),
        BuiltinCity("保定", "河北省", "130600", 38.8739, 115.4646),
        BuiltinCity("桂林", "广西壮族自治区", "450300", 25.2736, 110.2900),
        BuiltinCity("大理", "云南省", "532900", 25.6065, 100.2676),
        BuiltinCity("丽江", "云南省", "530700", 26.8550, 100.2278),
        // 港澳台
        BuiltinCity("香港", "中国香港", "810000", 22.3193, 114.1694),
        BuiltinCity("澳门", "中国澳门", "820000", 22.1987, 113.5439),
        BuiltinCity("台北", "中国台湾", "710000", 25.0330, 121.5654),
    )
}

/** 内置城市搜索：按名称或省份做包含匹配，离线可用。 */
class BuiltinCitySearcher : CitySearchProvider {

    override fun search(keyword: String, onResult: (List<CityHit>) -> Unit) {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            onResult(emptyList())
            return
        }
        val hits = BuiltinCities.all
            .filter { it.name.contains(kw) || it.province.contains(kw) }
            .map {
                CityHit(
                    adcode = it.adcode,
                    name = it.name,
                    parent = it.province,
                    lat = it.lat,
                    lng = it.lng,
                )
            }
        onResult(hits)
    }
}
