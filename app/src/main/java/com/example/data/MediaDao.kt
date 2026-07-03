package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // Media Items Queries
    @Query("SELECT * FROM media_items WHERE isHidden = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isVideo = 0 AND isHidden = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getAllPhotos(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isVideo = 1 AND isHidden = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getAllVideos(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 AND isHidden = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isHidden = 1 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getHiddenMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isTrashed = 1 ORDER BY trashedDate DESC")
    fun getTrashedMedia(): Flow<List<MediaItem>>

    @Query("SELECT DISTINCT albumName FROM media_items WHERE isHidden = 0 AND isTrashed = 0")
    fun getAlbums(): Flow<List<String>>

    @Query("SELECT * FROM media_items WHERE albumName = :albumName AND isHidden = 0 AND isTrashed = 0 ORDER BY dateAdded DESC")
    fun getMediaByAlbum(albumName: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaDirect(): List<MediaItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(mediaItem: MediaItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMedia(mediaItems: List<MediaItem>)

    @Update
    suspend fun updateMedia(mediaItem: MediaItem)

    @Delete
    suspend fun deleteMedia(mediaItem: MediaItem)

    @Query("DELETE FROM media_items WHERE isTrashed = 1")
    suspend fun clearTrash()

    @Query("UPDATE media_items SET isTrashed = 0, trashedDate = 0 WHERE isTrashed = 1")
    suspend fun restoreAllTrash()

    // Recent Searches Queries
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<RecentSearch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(recentSearch: RecentSearch)

    @Query("DELETE FROM recent_searches WHERE `query` = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()
}
