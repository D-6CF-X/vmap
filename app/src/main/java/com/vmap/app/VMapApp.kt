package com.vmap.app

import android.app.Application
import android.content.Context
import com.vmap.app.core.db.VMapDatabase
import com.vmap.app.data.photo.PhotoStore
import com.vmap.app.data.repository.SpotRepositoryImpl
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

    private val database: VMapDatabase = VMapDatabase.build(context)

    val spotRepository: SpotRepository =
        SpotRepositoryImpl(database.spotDao(), database.photoDao())

    val photoStore: PhotoStore = PhotoStore(context)
}
