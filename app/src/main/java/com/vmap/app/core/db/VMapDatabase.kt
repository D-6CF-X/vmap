package com.vmap.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vmap.app.data.db.Converters
import com.vmap.app.data.db.PhotoDao
import com.vmap.app.data.db.SongDao
import com.vmap.app.data.db.SpotDao
import com.vmap.app.data.db.TagDao
import com.vmap.app.domain.model.Photo
import com.vmap.app.domain.model.Song
import com.vmap.app.domain.model.SongRating
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.model.SpotTag
import com.vmap.app.domain.model.Tag

@Database(
    entities = [Spot::class, Photo::class, Tag::class, SpotTag::class, SongRating::class, Song::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VMapDatabase : RoomDatabase() {

    abstract fun spotDao(): SpotDao

    abstract fun photoDao(): PhotoDao

    abstract fun tagDao(): TagDao

    abstract fun songDao(): SongDao

    companion object {
        private const val NAME = "vmap.db"

        /** v2 → v3：只新增歌曲排行两张表，不动已有的打卡数据。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `song_ratings` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `songs` (" +
                        "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, " +
                        "`plays` INTEGER NOT NULL, `ratingId` TEXT NOT NULL, " +
                        "`orderIndex` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                        "`externalId` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /** v3 → v4：歌曲加曲绘路径，只加列，不动旧数据。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `coverPath` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): VMapDatabase =
            Room.databaseBuilder(context, VMapDatabase::class.java, NAME)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                // 兜底：将来再加表时如果忘了写 Migration，宁可清库也别崩
                .fallbackToDestructiveMigration()
                .build()
    }
}
