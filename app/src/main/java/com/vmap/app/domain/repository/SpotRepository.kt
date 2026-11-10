package com.vmap.app.domain.repository

import com.vmap.app.domain.model.Photo
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.model.Tag
import kotlinx.coroutines.flow.Flow

data class SectionStats(
    val cityCount: Int = 0,
    val spotCount: Int = 0,
)

interface SpotRepository {

    /** 按演出时间倒序，用于地图与列表。 */
    fun observeSpots(section: Section): Flow<List<Spot>>

    /** 按演出时间正序，用于时间线连线与箭头方向。 */
    fun observeChronological(section: Section): Flow<List<Spot>>

    /** 已点亮的行政区编码去重集合。 */
    fun observeLitAdcodes(section: Section): Flow<List<String>>

    fun observeStats(section: Section): Flow<SectionStats>

    fun observePhotos(spotId: String): Flow<List<Photo>>

    fun observeTags(): Flow<List<Tag>>

    fun observeTagsOfSpot(spotId: String): Flow<List<Tag>>

    fun observeSpotsByTag(section: Section, tagId: String): Flow<List<Spot>>

    fun observeChronologicalByTag(section: Section, tagId: String): Flow<List<Spot>>

    fun observeAllSpots(): Flow<List<Spot>>

    suspend fun save(spot: Spot)

    suspend fun softDelete(id: String)

    suspend fun addPhoto(photo: Photo)

    suspend fun deletePhoto(id: String)

    suspend fun addTag(name: String, color: Long): Tag

    suspend fun assignTag(spotId: String, tagId: String)

    suspend fun removeTag(spotId: String, tagId: String)

    suspend fun deleteTag(tagId: String)
}
