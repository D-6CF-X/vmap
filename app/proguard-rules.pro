# 高德地图 SDK
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**

# 高德 SDK 内部依赖的反射入口
-keep class com.amap.api.maps.** { *; }
-keep class com.amap.api.services.** { *; }
-keep class com.amap.api.col.** { *; }
