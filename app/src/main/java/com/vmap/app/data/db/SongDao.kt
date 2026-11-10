package com.vmap.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vmap.app.domain.model.Song
import com.vmap.app.domain.model.SongRating
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM song_ratings ORDER BY orderIndex ASC, name ASC")
    fun observeRatings(): Flow<List<SongRating>>

    @Query("SELECT * FROM songs ORDER BY orderIndex ASC")
    fun observeSongs(): Flow<List<Song>>

    @Query("SELECT COUNT(*) FROM song_ratings")
    suspend fun ratingCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRating(rating: SongRating)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRatings(ratings: List<SongRating>)

    @Query("DELETE FROM song_ratings WHERE id = :id")
    suspend fun deleteRating(id: String)

    @Query("DELETE FROM songs WHERE ratingId = :ratingId")
    suspend fun deleteSongsOfRating(ratingId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<Song>)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSong(id: String)

    @Query("SELECT MAX(orderIndex) FROM songs WHERE ratingId = :ratingId")
    suspend fun maxOrderOf(ratingId: String): Int?

    @Query("SELECT * FROM songs WHERE ratingId = :ratingId ORDER BY orderIndex ASC")
    suspend fun songsOf(ratingId: String): List<Song>
}
