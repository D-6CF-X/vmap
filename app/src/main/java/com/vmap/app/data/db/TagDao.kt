package com.vmap.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vmap.app.domain.model.SpotTag
import com.vmap.app.domain.model.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<Tag>>

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN spot_tags ON tags.id = spot_tags.tagId
        WHERE spot_tags.spotId = :spotId
        ORDER BY tags.name
        """
    )
    fun observeBySpot(spotId: String): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelation(spotTag: SpotTag)

    @Query("DELETE FROM spot_tags WHERE spotId = :spotId AND tagId = :tagId")
    suspend fun deleteRelation(spotId: String, tagId: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: String)

    @Query("DELETE FROM spot_tags WHERE tagId = :tagId")
    suspend fun deleteRelationsOfTag(tagId: String)
}
