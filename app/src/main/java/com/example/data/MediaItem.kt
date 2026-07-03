package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uri: String,
    val name: String,
    val size: Long,
    val resolution: String,
    val format: String,
    val path: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val duration: Long = 0L, // 0 for photos, non-zero for videos in milliseconds
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedDate: Long = 0L,
    val albumName: String = "All",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val cameraModel: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val isVideo: Boolean = false
)
