package com.example.data

import android.content.Context
import android.provider.MediaStore
import android.content.ContentUris
import android.media.ExifInterface
import java.io.File
import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {
    val allMedia: Flow<List<MediaItem>> = mediaDao.getAllMedia()
    val allPhotos: Flow<List<MediaItem>> = mediaDao.getAllPhotos()
    val allVideos: Flow<List<MediaItem>> = mediaDao.getAllVideos()
    val favorites: Flow<List<MediaItem>> = mediaDao.getFavorites()
    val hiddenMedia: Flow<List<MediaItem>> = mediaDao.getHiddenMedia()
    val trashedMedia: Flow<List<MediaItem>> = mediaDao.getTrashedMedia()
    val albums: Flow<List<String>> = mediaDao.getAlbums()
    val recentSearches: Flow<List<RecentSearch>> = mediaDao.getRecentSearches()

    suspend fun syncWithMediaStore(context: Context) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val scannedItems = mutableListOf<MediaItem>()
        val contentResolver = context.contentResolver

        // Scan images
        try {
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    val name = cursor.getString(nameCol) ?: "Image_$id"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                    val albumName = bucket ?: run {
                        val file = File(path)
                        file.parentFile?.name ?: "Camera"
                    }

                    val format = mime.substringAfter('/', "JPEG").uppercase()

                    var item = MediaItem(
                        id = 0,
                        uri = uri,
                        name = name,
                        size = size,
                        resolution = "${width}x${height}",
                        format = format,
                        path = path,
                        dateAdded = dateAdded,
                        duration = 0L,
                        isFavorite = false,
                        isHidden = false,
                        isTrashed = false,
                        albumName = albumName,
                        isVideo = false
                    )

                    if (path.isNotEmpty()) {
                        try {
                            val file = File(path)
                            if (file.exists()) {
                                val exif = ExifInterface(path)
                                val cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL) ?: exif.getAttribute(ExifInterface.TAG_MAKE)
                                val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" }
                                val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                                val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                                
                                val latLong = FloatArray(2)
                                val hasGps = exif.getLatLong(latLong)
                                val latitude = if (hasGps) latLong[0].toDouble() else null
                                val longitude = if (hasGps) latLong[1].toDouble() else null

                                item = item.copy(
                                    cameraModel = cameraModel,
                                    aperture = aperture,
                                    exposureTime = exposureTime?.let { if (it.contains("/")) it else "${it}s" },
                                    iso = iso,
                                    latitude = latitude,
                                    longitude = longitude
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    scannedItems.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Scan videos
        try {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DURATION
            )
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                    val albumName = bucket ?: run {
                        val file = File(path)
                        file.parentFile?.name ?: "Camera"
                    }
                    val duration = cursor.getLong(durationCol)

                    val format = mime.substringAfter('/', "MP4").uppercase()

                    scannedItems.add(
                        MediaItem(
                            id = 0,
                            uri = uri,
                            name = name,
                            size = size,
                            resolution = "${width}x${height}",
                            format = format,
                            path = path,
                            dateAdded = dateAdded,
                            duration = duration,
                            isFavorite = false,
                            isHidden = false,
                            isTrashed = false,
                            albumName = albumName,
                            isVideo = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Synchronize with database
        val existingItems = mediaDao.getAllMediaDirect()
        val existingByUriOrPath = existingItems.associateBy { it.path.ifEmpty { it.uri } }

        val itemsToInsertOrUpdate = mutableListOf<MediaItem>()
        val scannedUrisOrPaths = scannedItems.map { it.path.ifEmpty { it.uri } }.toSet()

        for (scanned in scannedItems) {
            val key = scanned.path.ifEmpty { scanned.uri }
            val existing = existingByUriOrPath[key]
            if (existing != null) {
                itemsToInsertOrUpdate.add(
                    scanned.copy(
                        id = existing.id,
                        isFavorite = existing.isFavorite,
                        isHidden = existing.isHidden,
                        isTrashed = existing.isTrashed,
                        trashedDate = existing.trashedDate,
                        albumName = existing.albumName
                    )
                )
            } else {
                itemsToInsertOrUpdate.add(scanned)
            }
        }

        val itemsToDelete = mutableListOf<MediaItem>()
        for (existing in existingItems) {
            val key = existing.path.ifEmpty { existing.uri }
            if (!scannedUrisOrPaths.contains(key)) {
                if (!existing.isHidden && !existing.isTrashed) {
                    itemsToDelete.add(existing)
                } else {
                    if (existing.path.isNotEmpty()) {
                        val file = File(existing.path)
                        if (!file.exists()) {
                            itemsToDelete.add(existing)
                        }
                    }
                }
            }
        }

        if (itemsToInsertOrUpdate.isNotEmpty()) {
            mediaDao.insertAllMedia(itemsToInsertOrUpdate)
        }
        if (itemsToDelete.isNotEmpty()) {
            for (item in itemsToDelete) {
                mediaDao.deleteMedia(item)
            }
        }
    }

    fun getMediaByAlbum(albumName: String): Flow<List<MediaItem>> {
        return mediaDao.getMediaByAlbum(albumName)
    }

    suspend fun insertMedia(mediaItem: MediaItem): Long {
        return mediaDao.insertMedia(mediaItem)
    }

    suspend fun updateMedia(mediaItem: MediaItem) {
        mediaDao.updateMedia(mediaItem)
    }

    suspend fun deleteMedia(mediaItem: MediaItem) {
        mediaDao.deleteMedia(mediaItem)
    }

    suspend fun clearTrash() {
        mediaDao.clearTrash()
    }

    suspend fun restoreAllTrash() {
        mediaDao.restoreAllTrash()
    }

    suspend fun toggleFavorite(mediaItem: MediaItem) {
        mediaDao.updateMedia(mediaItem.copy(isFavorite = !mediaItem.isFavorite))
    }

    suspend fun moveToTrash(mediaItem: MediaItem) {
        mediaDao.updateMedia(
            mediaItem.copy(
                isTrashed = true,
                trashedDate = System.currentTimeMillis()
            )
        )
    }

    suspend fun restoreFromTrash(mediaItem: MediaItem) {
        mediaDao.updateMedia(
            mediaItem.copy(
                isTrashed = false,
                trashedDate = 0L
            )
        )
    }

    suspend fun toggleHidden(mediaItem: MediaItem) {
        mediaDao.updateMedia(mediaItem.copy(isHidden = !mediaItem.isHidden))
    }

    suspend fun renameMedia(mediaItem: MediaItem, newName: String) {
        // Also update path if needed
        val extension = mediaItem.name.substringAfterLast('.', "")
        val formattedName = if (extension.isNotEmpty() && !newName.endsWith(".$extension")) {
            "$newName.$extension"
        } else {
            newName
        }
        val parentPath = mediaItem.path.substringBeforeLast('/', "")
        val newPath = if (parentPath.isNotEmpty()) "$parentPath/$formattedName" else formattedName
        mediaDao.updateMedia(mediaItem.copy(name = formattedName, path = newPath))
    }

    suspend fun moveMediaToAlbum(mediaItem: MediaItem, targetAlbum: String) {
        mediaDao.updateMedia(mediaItem.copy(albumName = targetAlbum))
    }

    // Recent Searches
    suspend fun addRecentSearch(query: String) {
        if (query.isNotBlank()) {
            mediaDao.insertRecentSearch(RecentSearch(query = query.trim()))
        }
    }

    suspend fun deleteRecentSearch(query: String) {
        mediaDao.deleteRecentSearch(query)
    }

    suspend fun clearRecentSearches() {
        mediaDao.clearRecentSearches()
    }
}
