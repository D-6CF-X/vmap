package com.vmap.app

import android.app.Application
import android.content.Context
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.vmap.app.core.db.VMapDatabase
import com.vmap.app.data.photo.PhotoStore
import com.vmap.app.data.repository.SpotRepositoryImpl
import com.vmap.app.domain.repository.SpotRepository

class VMapApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // 高德 SDK 隐私合规。这两个接口不调，地图只会显示灰底，看不出是没配 key 还是没声明。
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)

        container = AppContainer(this)
    }
}

/**
 * 手写依赖容器。依赖关系就这么几条，引 Hilt 是净负担。
 */
class AppContainer(context: Context) {

    private val database: VMapDatabase = VMapDatabase.build(context)

    val spotRepository: SpotRepository =
        SpotRepositoryImpl(database.spotDao(), database.photoDao())

    val photoStore: PhotoStore = PhotoStore(context)
}
