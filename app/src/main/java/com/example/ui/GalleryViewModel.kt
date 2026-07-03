package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaItem
import com.example.data.MediaRepository
import com.example.util.GeminiClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream

class GalleryViewModel(private val repository: MediaRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    // Expose flows from repository
    val allMedia: StateFlow<List<MediaItem>> = repository.allMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPhotos: StateFlow<List<MediaItem>> = repository.allPhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVideos: StateFlow<List<MediaItem>> = repository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<MediaItem>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenMedia: StateFlow<List<MediaItem>> = repository.hiddenMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedMedia: StateFlow<List<MediaItem>> = repository.trashedMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<String>> = repository.albums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSearches = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getMediaByAlbum(albumName: String): Flow<List<MediaItem>> {
        return repository.getMediaByAlbum(albumName)
    }

    // Basic Photo Editor bitmap state
    private val _editorBitmap = MutableStateFlow<Bitmap?>(null)
    val editorBitmap: StateFlow<Bitmap?> = _editorBitmap.asStateFlow()

    private var editorHistory = mutableListOf<Bitmap>()
    private var historyIndex = -1

    private var isObserverRegistered = false
    private var contentObserver: android.database.ContentObserver? = null
    private var contentResolver: android.content.ContentResolver? = null

    fun setStoragePermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasStoragePermission = granted) }
    }

    fun setPermissionPermanentlyDenied(denied: Boolean) {
        _uiState.update { it.copy(isPermissionPermanentlyDenied = denied) }
    }

    fun startMediaScan(context: Context) {
        val appCtx = context.applicationContext
        contentResolver = appCtx.contentResolver
        
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningMedia = true) }
            try {
                repository.syncWithMediaStore(appCtx)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isScanningMedia = false) }
            }
        }

        if (!isObserverRegistered) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            contentObserver = object : android.database.ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    viewModelScope.launch {
                        try {
                            repository.syncWithMediaStore(appCtx)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            try {
                contentResolver?.registerContentObserver(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    contentObserver!!
                )
                contentResolver?.registerContentObserver(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    contentObserver!!
                )
                isObserverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        // Automatically transit from Splash after 1.5 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _uiState.update { it.copy(currentScreen = ScreenState.Home) }
        }
    }

    // --- Navigation Actions ---
    fun navigateTo(screen: ScreenState) {
        _uiState.update { state ->
            val newBackStack = state.backStack + state.currentScreen
            state.copy(currentScreen = screen, backStack = newBackStack)
        }
        // Reset selections when navigating
        clearSelection()
        // Reset Gemini analysis on navigation
        _uiState.update { it.copy(geminiAnalysis = null, analysisError = null, isAnalyzingImage = false) }
    }

    fun navigateBack(): Boolean {
        var handled = false
        _uiState.update { state ->
            if (state.backStack.isNotEmpty()) {
                val previousScreen = state.backStack.last()
                val newBackStack = state.backStack.dropLast(1)
                handled = true
                state.copy(currentScreen = previousScreen, backStack = newBackStack)
            } else {
                state
            }
        }
        clearSelection()
        return handled
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
        when (index) {
            0 -> _uiState.update { it.copy(categoryFilter = "Photos") }
            1 -> _uiState.update { it.copy(categoryFilter = "Videos") }
            2 -> _uiState.update { it.copy(categoryFilter = "Albums") }
            3 -> _uiState.update { it.copy(categoryFilter = "Favorites") }
            4 -> _uiState.update { it.copy(categoryFilter = "Settings") }
        }
    }

    fun setCategoryFilter(filter: String) {
        _uiState.update { it.copy(categoryFilter = filter) }
        when (filter) {
            "Photos" -> _uiState.update { it.copy(selectedTab = 0) }
            "Videos" -> _uiState.update { it.copy(selectedTab = 1) }
            "Albums" -> _uiState.update { it.copy(selectedTab = 2) }
            "Favorites" -> _uiState.update { it.copy(selectedTab = 3) }
        }
    }

    // --- Sorting & Grid settings ---
    fun setSortBy(sortBy: String) {
        _uiState.update { it.copy(sortBy = sortBy) }
    }

    fun setGridColumns(columns: Int) {
        _uiState.update { it.copy(gridColumns = columns) }
    }

    fun setShowHiddenFiles(show: Boolean) {
        _uiState.update { it.copy(showHiddenFiles = show) }
    }

    fun setKeepScreenOn(keep: Boolean) {
        _uiState.update { it.copy(keepScreenOn = keep) }
    }

    fun setThemeMode(isDark: Boolean?) {
        _uiState.update { it.copy(isDarkMode = isDark) }
    }

    fun setAmoledMode(enabled: Boolean) {
        _uiState.update { it.copy(isAmoledMode = enabled) }
    }

    fun setAutoDeleteDays(days: Int) {
        _uiState.update { it.copy(autoDeleteTrashDays = days) }
    }

    // --- Search Operations ---
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isNotBlank()) {
            viewModelScope.launch {
                repository.addRecentSearch(query)
            }
        }
    }

    fun addRecentSearch(query: String) {
        viewModelScope.launch {
            repository.addRecentSearch(query)
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            repository.deleteRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    // --- Multi-Select & Actions ---
    fun toggleMediaSelection(id: Int) {
        _uiState.update { state ->
            val selected = state.selectedMediaIds.toMutableSet()
            if (selected.contains(id)) {
                selected.remove(id)
            } else {
                selected.add(id)
            }
            state.copy(
                selectedMediaIds = selected,
                isMultiSelectActive = selected.isNotEmpty()
            )
        }
    }

    fun selectAllMedia(items: List<MediaItem>) {
        _uiState.update { state ->
            state.copy(
                selectedMediaIds = items.map { it.id }.toSet(),
                isMultiSelectActive = items.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(
                selectedMediaIds = emptySet(),
                isMultiSelectActive = false
            )
        }
    }

    // --- Media Action Operations ---
    fun toggleFavorite(mediaItem: MediaItem) {
        viewModelScope.launch {
            repository.toggleFavorite(mediaItem)
        }
    }

    fun moveToTrash(mediaItem: MediaItem) {
        viewModelScope.launch {
            repository.moveToTrash(mediaItem)
        }
    }

    fun restoreFromTrash(mediaItem: MediaItem) {
        viewModelScope.launch {
            repository.restoreFromTrash(mediaItem)
        }
    }

    fun deleteMediaPermanently(mediaItem: MediaItem) {
        viewModelScope.launch {
            repository.deleteMedia(mediaItem)
        }
    }

    fun toggleHidden(mediaItem: MediaItem) {
        viewModelScope.launch {
            repository.toggleHidden(mediaItem)
        }
    }

    fun renameMedia(mediaItem: MediaItem, newName: String) {
        viewModelScope.launch {
            repository.renameMedia(mediaItem, newName)
        }
    }

    fun createAlbum(context: Context, albumName: String) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedMediaIds
            if (selectedIds.isNotEmpty()) {
                val currentMedia = allMedia.value
                currentMedia.filter { selectedIds.contains(it.id) }.forEach { item ->
                    repository.moveMediaToAlbum(item, albumName)
                }
                clearSelection()
                android.widget.Toast.makeText(context, "Successfully moved selected items to '$albumName'", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Please select photos or videos first, then create an album to move them!", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun moveSelectedToAlbum(albumName: String, items: List<MediaItem>) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedMediaIds
            items.filter { selectedIds.contains(it.id) }.forEach { item ->
                repository.moveMediaToAlbum(item, albumName)
            }
            clearSelection()
        }
    }

    fun trashSelected(items: List<MediaItem>) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedMediaIds
            items.filter { selectedIds.contains(it.id) }.forEach { item ->
                repository.moveToTrash(item)
            }
            clearSelection()
        }
    }

    fun hideSelected(items: List<MediaItem>) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedMediaIds
            items.filter { selectedIds.contains(it.id) }.forEach { item ->
                repository.toggleHidden(item)
            }
            clearSelection()
        }
    }

    fun restoreSelected(items: List<MediaItem>) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedMediaIds
            items.filter { selectedIds.contains(it.id) }.forEach { item ->
                repository.restoreFromTrash(item)
            }
            clearSelection()
        }
    }

    fun deleteSelectedPermanently(items: List<MediaItem>) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedMediaIds
            items.filter { selectedIds.contains(it.id) }.forEach { item ->
                repository.deleteMedia(item)
            }
            clearSelection()
        }
    }

    fun restoreAllTrash() {
        viewModelScope.launch {
            repository.restoreAllTrash()
        }
    }

    fun clearTrash() {
        viewModelScope.launch {
            repository.clearTrash()
        }
    }

    // --- Sort Media Helper ---
    fun sortMedia(items: List<MediaItem>): List<MediaItem> {
        val query = _uiState.value.searchQuery.lowercase()
        val filtered = if (query.isBlank()) {
            items
        } else {
            items.filter {
                it.name.lowercase().contains(query) ||
                it.albumName.lowercase().contains(query) ||
                it.format.lowercase().contains(query)
            }
        }

        return when (_uiState.value.sortBy) {
            "Newest" -> filtered.sortedByDescending { it.dateAdded }
            "Oldest" -> filtered.sortedBy { it.dateAdded }
            "Name A-Z" -> filtered.sortedBy { it.name }
            "Name Z-A" -> filtered.sortedByDescending { it.name }
            "Largest" -> filtered.sortedByDescending { it.size }
            "Smallest" -> filtered.sortedBy { it.size }
            else -> filtered
        }
    }

    // --- Basic Photo Editor Operations ---
    fun initEditor(context: Context, mediaItem: MediaItem) {
        viewModelScope.launch {
            try {
                val uri = Uri.parse(mediaItem.uri)
                val inputStream: InputStream? = if (uri.scheme == "android.resource") {
                    context.contentResolver.openInputStream(uri)
                } else {
                    null // Fallback to asset/resource load
                }

                val bitmap = if (inputStream != null) {
                    BitmapFactory.decodeStream(inputStream)
                } else {
                    // Try drawable fallback by parsing resource ID
                    val resName = mediaItem.uri.substringAfterLast("/")
                    val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                    if (resId != 0) {
                        BitmapFactory.decodeResource(context.resources, resId)
                    } else {
                        null
                    }
                }

                if (bitmap != null) {
                    // Configured mutable bitmap
                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    _editorBitmap.value = mutableBitmap
                    editorHistory.clear()
                    editorHistory.add(mutableBitmap)
                    historyIndex = 0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun pushHistory(bitmap: Bitmap) {
        // Trim history if we are in the middle of undo stack
        if (historyIndex < editorHistory.size - 1) {
            editorHistory = editorHistory.subList(0, historyIndex + 1).toMutableList()
        }
        editorHistory.add(bitmap)
        historyIndex = editorHistory.size - 1
        _editorBitmap.value = bitmap
    }

    fun undoEditor() {
        if (historyIndex > 0) {
            historyIndex--
            _editorBitmap.value = editorHistory[historyIndex]
        }
    }

    fun redoEditor() {
        if (historyIndex < editorHistory.size - 1) {
            historyIndex++
            _editorBitmap.value = editorHistory[historyIndex]
        }
    }

    fun rotateEditor() {
        val current = _editorBitmap.value ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        pushHistory(rotated)
    }

    fun flipHorizontalEditor() {
        val current = _editorBitmap.value ?: return
        val matrix = Matrix().apply { postScale(-1f, 1f) }
        val flipped = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        pushHistory(flipped)
    }

    fun flipVerticalEditor() {
        val current = _editorBitmap.value ?: return
        val matrix = Matrix().apply { postScale(1f, -1f) }
        val flipped = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        pushHistory(flipped)
    }

    fun cropEditor(ratio: String) {
        val current = _editorBitmap.value ?: return
        val w = current.width
        val h = current.height
        val cropped = when (ratio) {
            "1:1" -> {
                val size = minOf(w, h)
                Bitmap.createBitmap(current, (w - size) / 2, (h - size) / 2, size, size)
            }
            "4:3" -> {
                val targetH = (w * 3) / 4
                if (targetH <= h) {
                    Bitmap.createBitmap(current, 0, (h - targetH) / 2, w, targetH)
                } else {
                    val targetW = (h * 4) / 3
                    Bitmap.createBitmap(current, (w - targetW) / 2, 0, targetW, h)
                }
            }
            "16:9" -> {
                val targetH = (w * 9) / 16
                if (targetH <= h) {
                    Bitmap.createBitmap(current, 0, (h - targetH) / 2, w, targetH)
                } else {
                    val targetW = (h * 16) / 9
                    Bitmap.createBitmap(current, (w - targetW) / 2, 0, targetW, h)
                }
            }
            else -> current
        }
        pushHistory(cropped)
    }

    fun applyColorAdjustment(brightness: Float, contrast: Float, saturation: Float, warmth: Float) {
        val base = editorHistory.getOrNull(0) ?: return
        val adjusted = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(adjusted)
        val paint = Paint()

        // Combine adjustment color matrices
        val cm = ColorMatrix()

        // Saturated Matrix
        val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
        cm.postConcat(satMatrix)

        // Brightness Matrix (scale color values)
        val briMatrix = ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(briMatrix)

        // Contrast Matrix
        val scale = contrast
        val translate = (-.5f * scale + .5f) * 255f
        val conMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(conMatrix)

        // Warmth Matrix (increase red & yellow, decrease blue)
        val warmthMatrix = ColorMatrix(floatArrayOf(
            1f + (warmth * 0.1f), 0f, 0f, 0f, 0f,
            0f, 1f + (warmth * 0.05f), 0f, 0f, 0f,
            0f, 0f, 1f - (warmth * 0.1f), 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(warmthMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(base, 0f, 0f, paint)

        // Overwrite the current active screen but do not stack a million adjustment histories
        if (historyIndex > 0 && historyIndex == editorHistory.size - 1) {
            editorHistory[historyIndex] = adjusted
            _editorBitmap.value = adjusted
        } else {
            pushHistory(adjusted)
        }
    }

    fun applyFilter(filterName: String) {
        val current = _editorBitmap.value ?: return
        val filtered = current.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(filtered)
        val paint = Paint()
        val cm = ColorMatrix()

        when (filterName) {
            "Sepia" -> {
                cm.set(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "B&W" -> {
                cm.setSaturation(0f)
            }
            "Vintage" -> {
                cm.set(floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 0.8f, 0f, 0f, 0f,
                    0f, 0f, 0.5f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "Invert" -> {
                cm.set(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(current, 0f, 0f, paint)
        pushHistory(filtered)
    }

    fun saveEditorChanges(context: Context, originalItem: MediaItem) {
        val finalBitmap = _editorBitmap.value ?: return
        viewModelScope.launch {
            try {
                // Save the edited bitmap to our virtual database
                val cacheFile = java.io.File(context.cacheDir, "edited_${System.currentTimeMillis()}.jpg")
                cacheFile.outputStream().use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val packageName = context.packageName
                val newItem = MediaItem(
                    uri = Uri.fromFile(cacheFile).toString(),
                    name = "Edited_${originalItem.name}",
                    size = cacheFile.length(),
                    resolution = "${finalBitmap.width}x${finalBitmap.height}",
                    format = "JPEG",
                    path = cacheFile.absolutePath,
                    albumName = "Edited",
                    isVideo = false
                )
                repository.insertMedia(newItem)

                // Return to home/viewer
                _uiState.update { it.copy(currentScreen = ScreenState.Home) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Gemini Analysis Operations ---
    fun analyzePhotoWithGemini(context: Context, mediaItem: MediaItem) {
        _uiState.update { it.copy(isAnalyzingImage = true, geminiAnalysis = null, analysisError = null) }
        viewModelScope.launch {
            try {
                val uri = Uri.parse(mediaItem.uri)
                val inputStream: InputStream? = if (uri.scheme == "android.resource") {
                    context.contentResolver.openInputStream(uri)
                } else {
                    null
                }

                val bitmap = if (inputStream != null) {
                    BitmapFactory.decodeStream(inputStream)
                } else {
                    val resName = mediaItem.uri.substringAfterLast("/")
                    val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                    if (resId != 0) {
                        BitmapFactory.decodeResource(context.resources, resId)
                    } else {
                        null
                    }
                }

                if (bitmap != null) {
                    val prompt = "Analyze this image and describe what it contains. What are the key subjects, colors, aesthetics, lighting, composition, and mood? Provide a highly polished art critique of this picture. Output bullet points for: Subject, Aesthetic Style, Core Colors, Emotional Mood, and a short 2-sentence summary review."
                    val result = GeminiClient.analyzeImage(bitmap, prompt)
                    result.onSuccess { text ->
                        _uiState.update { it.copy(geminiAnalysis = text, isAnalyzingImage = false) }
                    }.onFailure { error ->
                        _uiState.update { it.copy(analysisError = error.message ?: "An error occurred", isAnalyzingImage = false) }
                    }
                } else {
                    _uiState.update { it.copy(analysisError = "Failed to load image bitmap for analysis.", isAnalyzingImage = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(analysisError = "Error loading image: ${e.message}", isAnalyzingImage = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        contentObserver?.let { observer ->
            contentResolver?.unregisterContentObserver(observer)
        }
        _editorBitmap.value = null
        editorHistory.forEach { 
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        editorHistory.clear()
    }
}
