package com.vmap.app.data.repository

import com.vmap.app.data.db.PhotoDao
import com.vmap.app.data.db.SpotDao
import com.vmap.app.domain.model.Photo
import com.vmap.app.domain.model.Section
import com.vmap.app.domain.model.Spot
import com.vmap.app.domain.repository.SectionStats
import com.vmap.app.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SpotRepositoryImpl(
    private val spotDao: SpotDao,
    private val photoDao: PhotoDao,
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
}
