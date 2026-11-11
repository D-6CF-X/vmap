package com.vmap.app

import android.app.Application
import android.content.Context
import com.vmap.app.core.db.VMapDatabase
import com.vmap.app.data.photo.PhotoStore
import com.vmap.app.data.repository.SpotRepositoryImpl
import com.vmap.app.data.song.CoverStore
import com.vmap.app.domain.repository.SpotRepository

class VMapApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * 手写依赖容器。依赖关系就这么几条，引 Hilt 是净负担。
 */
class AppContainer(context: Context) {

    /** 交给需要读 assets / SharedPreferences 的地方（自动导入用）。 */
    val appContext: Context = context

    private val database: VMapDatabase = VMapDatabase.build(context)

    val spotRepository: SpotRepository =
        SpotRepositoryImpl(database.spotDao(), database.photoDao(), database.tagDao())

    val songDao = database.songDao()

    val coverStore: CoverStore = CoverStore(context)

    val photoStore: PhotoStore = PhotoStore(context)
}
