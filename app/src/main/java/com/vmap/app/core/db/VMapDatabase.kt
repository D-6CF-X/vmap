package com.vmap.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vmap.app.data.db.Converters
import com.vmap.app.data.db.PhotoDao
import com.vmap.app.data.db.SpotDao
import com.vmap.app.domain.model.Photo
import com.vmap.app.domain.model.Spot

@Database(
    entities = [Spot::class, Photo::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VMapDatabase : RoomDatabase() {

    abstract fun spotDao(): SpotDao

    abstract fun photoDao(): PhotoDao

    companion object {
        private const val NAME = "vmap.db"

        fun build(context: Context): VMapDatabase =
            Room.databaseBuilder(context, VMapDatabase::class.java, NAME)
                // 仅用于开发期迭代。发布 1.0 前必须换成显式 Migration，
                // 否则升级会清空用户所有打卡记录。
                .fallbackToDestructiveMigration()
                .build()
    }
}
