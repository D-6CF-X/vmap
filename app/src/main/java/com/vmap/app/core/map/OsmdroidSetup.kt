package com.vmap.app.core.map

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * osmdroid 的初始化。必须在 MapView 创建之前调用。
 *
 * Android 10 之后瓦片缓存不能再用外部存储的默认路径，要显式指到应用私有目录，
 * 否则下载完写不进去，表现为地图一直灰着。
 */
object OsmdroidSetup {

    fun init(context: Context) {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }
}
