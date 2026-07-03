package com.example.ui

import com.example.data.MediaItem

sealed class ScreenState {
    object Splash : ScreenState()
    object Home : ScreenState()
    object Search : ScreenState()
    data class PhotoViewer(val mediaItemId: Int) : ScreenState()
    data class VideoPlayer(val mediaItemId: Int) : ScreenState()
    data class PhotoEditor(val mediaItemId: Int) : ScreenState()
    object Albums : ScreenState()
    data class AlbumDetail(val albumName: String) : ScreenState()
    object Favorites : ScreenState()
    object HiddenLocked : ScreenState()
    object HiddenMedia : ScreenState()
    object Trash : ScreenState()
    object Settings : ScreenState()
}

data class GalleryUiState(
    val currentScreen: ScreenState = ScreenState.Splash,
    val backStack: List<ScreenState> = emptyList(),
    val gridColumns: Int = 3,
    val searchQuery: String = "",
    val selectedMediaIds: Set<Int> = emptySet(),
    val isMultiSelectActive: Boolean = false,
    val isDarkMode: Boolean? = null, // null means follow system
    val isAmoledMode: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val keepScreenOn: Boolean = false,
    val pinCode: String = "1111", // Default simple PIN
    val isPinSet: Boolean = true,
    val selectedTab: Int = 0, // 0 = Photos, 1 = Videos, 2 = Albums, 3 = Favorites, 4 = Settings
    val categoryFilter: String = "Photos", // Photos, Videos, Albums, Favorites, Hidden
    val sortBy: String = "Newest", // Newest, Oldest, Name A-Z, Name Z-A, Largest, Smallest
    val geminiAnalysis: String? = null,
    val isAnalyzingImage: Boolean = false,
    val analysisError: String? = null,
    val autoDeleteTrashDays: Int = 30,
    val hasStoragePermission: Boolean = false,
    val isPermissionPermanentlyDenied: Boolean = false,
    val isScanningMedia: Boolean = false
)
