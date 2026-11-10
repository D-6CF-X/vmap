package com.vmap.app.data.repository

import com.vmap.app.data.db.PhotoDao
import com.vmap.app.data.db.SpotDao
import com.vmap.app.data.db.TagDao
import com.vmap.app.domain.model.Photo
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.model.SpotTag
import com.vmap.app.domain.model.Tag
import com.vmap.app.domain.repository.SectionStats
import com.vmap.app.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SpotRepositoryImpl(
    private val spotDao: SpotDao,
    private val photoDao: PhotoDao,
    private val tagDao: TagDao,
) : SpotRepository {

    override fun observeSpots(section: Section): Flow<List<Spot>> =
        spotDao.observeBySection(section)

    override fun observeChronological(section: Section): Flow<List<Spot>> =
        spotDao.observeChronological(section)

    override fun observeLitAdcodes(section: Section): Flow<List<String>> =
        spotDao.observeLitAdcodes(section)

    override fun observeStats(section: Section): Flow<SectionStats> =
        combine(
            spotDao.observeCityCount(section),
            spotDao.observeSpotCount(section),
        ) { cities, spots -> SectionStats(cityCount = cities, spotCount = spots) }

    override fun observePhotos(spotId: String): Flow<List<Photo>> =
        photoDao.observeBySpot(spotId)

    override suspend fun save(spot: Spot) {
        spotDao.upsert(spot.copy(updatedAt = System.currentTimeMillis(), synced = false))
    }

    override suspend fun softDelete(id: String) {
        spotDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun addPhoto(photo: Photo) {
        photoDao.upsert(photo)
    }

    override suspend fun deletePhoto(id: String) {
        photoDao.delete(id)
    }

    override fun observeTags(): Flow<List<Tag>> = tagDao.observeAll()

    override fun observeTagsOfSpot(spotId: String): Flow<List<Tag>> = tagDao.observeBySpot(spotId)

    override fun observeSpotsByTag(section: Section, tagId: String): Flow<List<Spot>> =
        spotDao.observeByTag(section, tagId)

    override fun observeChronologicalByTag(section: Section, tagId: String): Flow<List<Spot>> =
        spotDao.observeByTagChronological(section, tagId)

    override fun observeAllSpots(): Flow<List<Spot>> = spotDao.observeAll()

    override suspend fun addTag(name: String, color: Long): Tag {
        val tag = Tag(name = name.trim(), color = color)
        tagDao.upsert(tag)
        return tag
    }

    override suspend fun assignTag(spotId: String, tagId: String) {
        tagDao.insertRelation(SpotTag(spotId = spotId, tagId = tagId))
    }

    override suspend fun removeTag(spotId: String, tagId: String) {
        tagDao.deleteRelation(spotId, tagId)
    }

    override suspend fun deleteTag(tagId: String) {
        tagDao.deleteRelationsOfTag(tagId)
        tagDao.deleteTag(tagId)
    }
}
