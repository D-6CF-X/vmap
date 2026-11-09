package com.vmap.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vmap.app.domain.model.Photo
import com.vmap.app.domain.model.Section
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE spotId = :spotId ORDER BY takenAt ASC")
    fun observeBySpot(spotId: String): Flow<List<Photo>>

    @Query(
        """
        SELECT p.* FROM photos p
        INNER JOIN spots s ON s.id = p.spotId
        WHERE s.deleted = 0 AND s.section = :section
        ORDER BY p.takenAt DESC
        """
    )
    fun observeBySection(section: Section): Flow<List<Photo>>

    @Query("SELECT COUNT(*) FROM photos")
    fun observeTotalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: Photo)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM photos WHERE spotId = :spotId")
    suspend fun findBySpot(spotId: String): List<Photo>
}
