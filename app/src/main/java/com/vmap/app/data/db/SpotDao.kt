package com.vmap.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotDao {

    /** 某个板块的全部打卡，按演出时间倒序（最新的在最上面）。 */
    @Query(
        """
        SELECT * FROM spots
        WHERE deleted = 0 AND section = :section
        ORDER BY performedAt DESC
        """
    )
    fun observeBySection(section: Section): Flow<List<Spot>>

    /** 时间线用：按时间正序，用来连接路线、判断箭头方向。 */
    @Query(
        """
        SELECT * FROM spots
        WHERE deleted = 0 AND section = :section
        ORDER BY performedAt ASC
        """
    )
    fun observeChronological(section: Section): Flow<List<Spot>>

    /** 已点亮的行政区编码去重集合。点亮进度直接从打卡数据聚合，不另设表。 */
    @Query(
        """
        SELECT DISTINCT adcode FROM spots
        WHERE deleted = 0 AND section = :section
        """
    )
    fun observeLitAdcodes(section: Section): Flow<List<String>>

    @Query(
        """
        SELECT COUNT(DISTINCT adcode) FROM spots
        WHERE deleted = 0 AND section = :section
        """
    )
    fun observeCityCount(section: Section): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM spots
        WHERE deleted = 0 AND section = :section
        """
    )
    fun observeSpotCount(section: Section): Flow<Int>

    @Query("SELECT * FROM spots WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Spot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(spot: Spot)

    @Query("UPDATE spots SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
