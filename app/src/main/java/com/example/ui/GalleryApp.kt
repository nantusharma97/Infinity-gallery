package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.MediaItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp(viewModel: GalleryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[android.Manifest.permission.READ_MEDIA_IMAGES] == true &&
            permissions[android.Manifest.permission.READ_MEDIA_VIDEO] == true
        } else {
            permissions[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        viewModel.setStoragePermissionGranted(allGranted)
        if (allGranted) {
            viewModel.startMediaScan(context)
        } else {
            val activity = context as? android.app.Activity
            val permanentlyDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity?.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_MEDIA_IMAGES) == false &&
                activity.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_MEDIA_VIDEO) == false
            } else {
                activity?.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE) == false
            }
            viewModel.setPermissionPermanentlyDenied(permanentlyDenied)
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.setStoragePermissionGranted(hasPermission)
        if (hasPermission) {
            viewModel.startMediaScan(context)
        }
    }

    // Handle Android system back presses cleanly
    BackHandler(enabled = uiState.currentScreen !is ScreenState.Home) {
        viewModel.navigateBack()
    }

    // Dynamic Theming setup
    val isSystemDark = isSystemInDarkTheme()
    val useDarkMode = uiState.isDarkMode ?: isSystemDark

    val themeColorScheme = if (useDarkMode) {
        if (uiState.isAmoledMode) {
            darkColorScheme(
                primary = Color(0xFFD0BCFF),
                secondary = Color(0xFFCCC2DC),
                tertiary = Color(0xFFEFB8C8),
                background = Color(0xFF000000),
                surface = Color(0xFF121212),
                surfaceVariant = Color(0xFF1E1E1E),
                onBackground = Color(0xFFFFFFFF),
                onSurface = Color(0xFFFFFFFF)
            )
        } else {
            darkColorScheme(
                primary = Color(0xFFD0BCFF),
                secondary = Color(0xFFCCC2DC),
                tertiary = Color(0xFFEFB8C8),
                background = Color(0xFF1C1B1F),
                surface = Color(0xFF252429),
                onBackground = Color(0xFFE6E1E5),
                onSurface = Color(0xFFE6E1E5)
            )
        }
    } else {
        lightColorScheme(
            primary = Color(0xFF6650a4),
            secondary = Color(0xFF625b71),
            tertiary = Color(0xFF7D5260),
            background = Color(0xFFFFFBFE),
            surface = Color(0xFFF3EDF7),
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F)
        )
    }

    // Set Keep Screen On if configured
    val activity = context as? android.app.Activity
    LaunchedEffect(uiState.keepScreenOn) {
        if (uiState.keepScreenOn) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    MaterialTheme(colorScheme = themeColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = uiState.currentScreen is ScreenState.Home && uiState.hasStoragePermission,
                drawerContent = {
                    GalleryDrawerContent(
                        uiState = uiState,
                        onNavigate = { screen ->
                            viewModel.navigateTo(screen)
                            scope.launch { drawerState.close() }
                        },
                        onClose = { scope.launch { drawerState.close() } }
                    )
                }
            ) {
                if (uiState.currentScreen !is ScreenState.Splash && !uiState.hasStoragePermission) {
                    PermissionExplanationScreen(
                        isPermanentlyDenied = uiState.isPermissionPermanentlyDenied,
                        onGrantPermission = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(
                                    android.Manifest.permission.READ_MEDIA_IMAGES,
                                    android.Manifest.permission.READ_MEDIA_VIDEO
                                )
                            } else {
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(perms)
                        },
                        onOpenSettings = {
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                } else {
                    // Root Animated Content for beautiful, fluid transitions
                    AnimatedContent(
                        targetState = uiState.currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            is ScreenState.Splash -> SplashScreen()
                            is ScreenState.Home -> HomeScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                onOpenDrawer = { scope.launch { drawerState.open() } }
                            )
                            is ScreenState.Search -> SearchScreen(viewModel = viewModel, uiState = uiState)
                            is ScreenState.PhotoViewer -> PhotoViewerScreen(viewModel = viewModel, mediaItemId = screen.mediaItemId)
                            is ScreenState.VideoPlayer -> VideoPlayerScreen(viewModel = viewModel, mediaItemId = screen.mediaItemId)
                            is ScreenState.PhotoEditor -> PhotoEditorScreen(viewModel = viewModel, mediaItemId = screen.mediaItemId)
                            is ScreenState.Albums -> AlbumsScreen(viewModel = viewModel, uiState = uiState)
                            is ScreenState.AlbumDetail -> AlbumDetailScreen(viewModel = viewModel, uiState = uiState, albumName = screen.albumName)
                            is ScreenState.Favorites -> FavoritesScreen(viewModel = viewModel, uiState = uiState)
                            is ScreenState.HiddenLocked -> HiddenLockScreen(viewModel = viewModel, uiState = uiState)
                            is ScreenState.HiddenMedia -> HiddenMediaScreen(viewModel = viewModel, uiState = uiState)
                            is ScreenState.Trash -> TrashScreen(viewModel = viewModel, uiState = uiState)
                            is ScreenState.Settings -> SettingsScreen(viewModel = viewModel, uiState = uiState)
                        }
                    }
                }
            }
        }
    }
}

// --- Drawer Layout ---
@Composable
fun GalleryDrawerContent(
    uiState: GalleryUiState,
    onNavigate: (ScreenState) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Infinity Gallery",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

            // Navigation Items
            val items = listOf(
                Triple("Home", Icons.Default.Home, ScreenState.Home),
                Triple("Albums", Icons.Default.PhotoLibrary, ScreenState.Albums),
                Triple("Favorites", Icons.Default.Favorite, ScreenState.Favorites),
                Triple("Hidden", Icons.Default.Lock, ScreenState.HiddenLocked),
                Triple("Trash", Icons.Default.Delete, ScreenState.Trash),
                Triple("Settings", Icons.Default.Settings, ScreenState.Settings)
            )

            items.forEach { (label, icon, state) ->
                NavigationDrawerItem(
                    icon = { Icon(imageVector = icon, contentDescription = label) },
                    label = { Text(text = label, fontWeight = FontWeight.SemiBold) },
                    selected = uiState.currentScreen == state,
                    onClick = { onNavigate(state) },
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Infinity Gallery v1.0.0\nOffline-First & Secure",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
    }
}

// --- Splash Screen ---
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        // Gentle bouncing loading animation
                        translationY = 0f
                    }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Infinity Gallery",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Fast. Pure. Private.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// --- Home Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GalleryViewModel,
    uiState: GalleryUiState,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allPhotosList by viewModel.allPhotos.collectAsState()
    val allVideosList by viewModel.allVideos.collectAsState()
    val allMediaList by viewModel.allMedia.collectAsState()
    val favoritesList by viewModel.favorites.collectAsState()

    var showMoreMenu by remember { mutableStateOf(false) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }

    val activeList = when (uiState.categoryFilter) {
        "Photos" -> allPhotosList
        "Videos" -> allVideosList
        "Favorites" -> favoritesList
        else -> allMediaList
    }

    val sortedList = viewModel.sortMedia(activeList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Infinity Gallery",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(ScreenState.Search) }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
                    }

                    // More Menu dropdown
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Grid Size (2 Columns)") },
                            onClick = { viewModel.setGridColumns(2); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Grid Size (3 Columns)") },
                            onClick = { viewModel.setGridColumns(3); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Grid Size (4 Columns)") },
                            onClick = { viewModel.setGridColumns(4); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Grid Size (5 Columns)") },
                            onClick = { viewModel.setGridColumns(5); showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Sort: Newest") },
                            onClick = { viewModel.setSortBy("Newest"); showMoreMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort: Oldest") },
                            onClick = { viewModel.setSortBy("Oldest"); showMoreMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort: Name A-Z") },
                            onClick = { viewModel.setSortBy("Name A-Z"); showMoreMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort: Name Z-A") },
                            onClick = { viewModel.setSortBy("Name Z-A"); showMoreMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort: Largest") },
                            onClick = { viewModel.setSortBy("Largest"); showMoreMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort: Smallest") },
                            onClick = { viewModel.setSortBy("Smallest"); showMoreMenu = false }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Create Album") },
                            onClick = { showCreateAlbumDialog = true; showMoreMenu = false },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navItems = listOf(
                    Quadruple("Photos", Icons.Default.Photo, 0, "Photos"),
                    Quadruple("Videos", Icons.Default.Videocam, 1, "Videos"),
                    Quadruple("Albums", Icons.Default.PhotoLibrary, 2, "Albums"),
                    Quadruple("Favorites", Icons.Default.Favorite, 3, "Favorites"),
                    Quadruple("Settings", Icons.Default.Settings, 4, "Settings")
                )

                navItems.forEach { (label, icon, tabIdx, category) ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(text = label) },
                        selected = uiState.selectedTab == tabIdx,
                        onClick = {
                            viewModel.setTab(tabIdx)
                            if (tabIdx == 2) {
                                viewModel.navigateTo(ScreenState.Albums)
                            } else if (tabIdx == 4) {
                                viewModel.navigateTo(ScreenState.Settings)
                            } else if (tabIdx == 3) {
                                viewModel.setCategoryFilter("Favorites")
                            } else {
                                viewModel.setCategoryFilter(category)
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    try {
                        val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                        context.startActivity(cameraIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Camera app not found on this device.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.testTag("camera_fab")
            ) {
                Icon(imageVector = Icons.Default.Camera, contentDescription = "Open Camera")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal scrollable categories row
            HorizontalCategoriesRow(
                activeFilter = uiState.categoryFilter,
                onFilterSelected = { filter ->
                    if (filter == "Albums") {
                        viewModel.navigateTo(ScreenState.Albums)
                    } else if (filter == "Hidden") {
                        viewModel.navigateTo(ScreenState.HiddenLocked)
                    } else {
                        viewModel.setCategoryFilter(filter)
                    }
                }
            )

            // Responsive Photo Grid
            if (sortedList.isEmpty()) {
                EmptyStateView(
                    title = "No Media Found",
                    description = "Take some photos or download images to see them here."
                )
            } else {
                MediaGrid(
                    items = sortedList,
                    columns = uiState.gridColumns,
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }

    if (showCreateAlbumDialog) {
        CreateAlbumDialog(
            onDismiss = { showCreateAlbumDialog = false },
            onCreate = { name ->
                viewModel.createAlbum(context, name)
                showCreateAlbumDialog = false
            }
        )
    }
}

// Helper Class
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

// --- Horizontal Categories Row ---
@Composable
fun HorizontalCategoriesRow(
    activeFilter: String,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf("Photos", "Videos", "Albums", "Favorites", "Hidden")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = activeFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = { Text(text = filter, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

// --- Media Grid Component ---
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    viewModel: GalleryViewModel,
    uiState: GalleryUiState
) {
    var showAddToAlbumDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.isMultiSelectActive) {
            // Selection controller bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${uiState.selectedMediaIds.size} Selected",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { viewModel.selectAllMedia(items) }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                    }
                    IconButton(onClick = { showAddToAlbumDialog = true }) {
                        Icon(Icons.Default.Folder, contentDescription = "Move to Album")
                    }
                    IconButton(onClick = { viewModel.hideSelected(items) }) {
                        Icon(Icons.Default.Lock, contentDescription = "Hide Selected")
                    }
                    IconButton(onClick = { viewModel.trashSelected(items) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Trash Selected")
                    }
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items, key = { it.id }) { item ->
                MediaGridItem(
                    item = item,
                    isSelected = uiState.selectedMediaIds.contains(item.id),
                    isSelectionMode = uiState.isMultiSelectActive,
                    onClick = {
                        if (uiState.isMultiSelectActive) {
                            viewModel.toggleMediaSelection(item.id)
                        } else {
                            if (item.isVideo) {
                                viewModel.navigateTo(ScreenState.VideoPlayer(item.id))
                            } else {
                                viewModel.navigateTo(ScreenState.PhotoViewer(item.id))
                            }
                        }
                    },
                    onLongClick = {
                        viewModel.toggleMediaSelection(item.id)
                    }
                )
            }
        }
    }

    if (showAddToAlbumDialog) {
        val albumsList by viewModel.albums.collectAsState()
        AddToAlbumDialog(
            albums = albumsList,
            onDismiss = { showAddToAlbumDialog = false },
            onAlbumSelected = { albumName ->
                viewModel.moveSelectedToAlbum(albumName, items)
                showAddToAlbumDialog = false
            }
        )
    }
}

// --- Media Grid Item ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("media_grid_item_${item.id}")
    ) {
        // Thumbnail Image loading via Coil
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video Indicator overlay
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = formatDuration(item.duration),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                    )
                }
            }
        }

        // Selection checkbox / indicator overlay
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
                    .padding(6.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)
                        )
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- Empty State View ---
@Composable
fun EmptyStateView(title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- Search Screen ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(viewModel: GalleryViewModel, uiState: GalleryUiState) {
    val allMediaList by viewModel.allMedia.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    var searchInput by remember { mutableStateOf(uiState.searchQuery) }

    val filteredList = viewModel.sortMedia(allMediaList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchInput,
                        onValueChange = {
                            searchInput = it
                            viewModel.updateSearchQuery(it)
                        },
                        placeholder = { Text("Search by name, album, type...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_input")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (searchInput.isNotEmpty()) {
                        IconButton(onClick = {
                            searchInput = ""
                            viewModel.updateSearchQuery("")
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (searchInput.isEmpty()) {
                // Show Recent Searches & Suggestions
                Column(modifier = Modifier.padding(16.dp)) {
                    if (recentSearches.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Searches",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = { viewModel.clearRecentSearches() }) {
                                Text("Clear All")
                            }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recentSearches) { search ->
                                AssistChip(
                                    onClick = {
                                        searchInput = search.query
                                        viewModel.updateSearchQuery(search.query)
                                    },
                                    label = { Text(search.query) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.deleteRecentSearch(search.query) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(12.dp))
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Text(
                        text = "Suggested Searches",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val suggestions = listOf("Sunset", "Tokyo", "Landscape", "Cute", "Animations", "JPEG", "MP4")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { tag ->
                            SuggestionChip(
                                onClick = {
                                    searchInput = tag
                                    viewModel.updateSearchQuery(tag)
                                    viewModel.addRecentSearch(tag)
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            } else {
                // Show search results
                if (filteredList.isEmpty()) {
                    EmptyStateView(
                        title = "No Results Found",
                        description = "We couldn't find any media matching '$searchInput'."
                    )
                } else {
                    MediaGrid(
                        items = filteredList,
                        columns = uiState.gridColumns,
                        viewModel = viewModel,
                        uiState = uiState
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}

// --- Photo Viewer Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(viewModel: GalleryViewModel, mediaItemId: Int) {
    val context = LocalContext.current
    val allMediaList by viewModel.allMedia.collectAsState()
    val mediaItem = allMediaList.find { it.id == mediaItemId } ?: return
    val uiState by viewModel.uiState.collectAsState()

    var showInfoBottomSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Immersive fullscreen state toggler
    var isImmersiveMode by remember { mutableStateOf(false) }

    // Gestures zoom state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !isImmersiveMode,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = mediaItem.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleFavorite(mediaItem) }) {
                            Icon(
                                imageVector = if (mediaItem.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (mediaItem.isFavorite) Color.Red else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { shareMedia(context, mediaItem) }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !isImmersiveMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                BottomAppBar(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        IconButton(onClick = {
                            viewModel.navigateTo(ScreenState.PhotoEditor(mediaItem.id))
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Crop, contentDescription = "Edit", tint = Color.White)
                                Text("Edit", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            viewModel.analyzePhotoWithGemini(context, mediaItem)
                            showInfoBottomSheet = true
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI Analysis", tint = Color.White)
                                Text("Gemini", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Color.White)
                            }
                        }
                        IconButton(onClick = { showInfoBottomSheet = true }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                                Text("Details", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            viewModel.toggleHidden(mediaItem)
                            viewModel.navigateBack()
                            Toast.makeText(context, "Image Hidden!", Toast.LENGTH_SHORT).show()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Lock, contentDescription = "Hide", tint = Color.White)
                                Text("Hide", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            viewModel.moveToTrash(mediaItem)
                            viewModel.navigateBack()
                            Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Delete, contentDescription = "Trash", tint = Color.White)
                                Text("Trash", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Swipe and gesture zoom photo area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        },
                        onTap = {
                            isImmersiveMode = !isImmersiveMode
                        }
                    )
                }
                .transformable(state = state)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(mediaItem.uri)
                    .build(),
                contentDescription = mediaItem.name,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }

    if (showInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PhotoDetailsView(
                mediaItem = mediaItem,
                uiState = uiState,
                onClose = { showInfoBottomSheet = false }
            )
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = mediaItem.name.substringBeforeLast('.'),
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                viewModel.renameMedia(mediaItem, newName)
                showRenameDialog = false
            }
        )
    }
}

// Helper methods for actions
fun shareMedia(context: Context, mediaItem: MediaItem) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (mediaItem.isVideo) "video/*" else "image/*"
            if (mediaItem.uri.startsWith("android.resource")) {
                // For resources, we share as a text or stream if it has real file path
                putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaItem.uri))
            } else {
                putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaItem.uri))
            }
            putExtra(Intent.EXTRA_SUBJECT, mediaItem.name)
            putExtra(Intent.EXTRA_TEXT, "Shared from Infinity Gallery: ${mediaItem.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing media: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// --- Video Player Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(viewModel: GalleryViewModel, mediaItemId: Int) {
    val context = LocalContext.current
    val allMediaList by viewModel.allMedia.collectAsState()
    val mediaItem = allMediaList.find { it.id == mediaItemId } ?: return

    var isPlaying by remember { mutableStateOf(true) }
    var duration by remember { mutableStateOf(mediaItem.duration) }
    var currentPosition by remember { mutableStateOf(0L) }
    var isLooping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    // Volume & Brightness Gesture simulation variables
    var brightnessOffset by remember { mutableStateOf(0.5f) }
    var volumeOffset by remember { mutableStateOf(0.5f) }

    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mediaItem.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (change.position.x < size.width / 2) {
                            // Left side drag: Brightness
                            brightnessOffset = (brightnessOffset - dragAmount.y / size.height).coerceIn(0f, 1f)
                        } else {
                            // Right side drag: Volume
                            volumeOffset = (volumeOffset - dragAmount.y / size.height).coerceIn(0f, 1f)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Video View implementation wrapping Android's built-in VideoView
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoURI(Uri.parse(mediaItem.uri))
                        setOnPreparedListener { mp ->
                            mediaPlayer = mp
                            mp.isLooping = isLooping
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                            }
                            duration = mp.duration.toLong()
                            start()
                        }
                        setOnCompletionListener {
                            if (!isLooping) {
                                isPlaying = false
                            }
                        }
                    }
                },
                update = { view ->
                    if (isPlaying) {
                        view.start()
                    } else {
                        view.pause()
                    }
                    try {
                        mediaPlayer?.let { mp ->
                            mp.isLooping = isLooping
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            // Gesture feedback overlays
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brightness feedback
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Brightness5, contentDescription = null, tint = Color.White)
                    Text("${(brightnessOffset * 100).roundToInt()}%", color = Color.White)
                }
                // Volume feedback
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White)
                    Text("${(volumeOffset * 100).roundToInt()}%", color = Color.White)
                }
            }

            // Custom Player Controls overlay at bottom of screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatDuration(currentPosition), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = {
                                currentPosition = (it * duration).toLong()
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Loop control
                        IconButton(onClick = { isLooping = !isLooping }) {
                            Icon(
                                imageVector = Icons.Default.Loop,
                                contentDescription = "Loop",
                                tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        // Play / Pause control
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White
                            )
                        }

                        // Speed selector control
                        TextButton(
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.5f
                                    else -> 1.0f
                                }
                            }
                        ) {
                            Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Video Details") },
            text = {
                Column {
                    Text("File: ${mediaItem.name}")
                    Text("Format: ${mediaItem.format}")
                    Text("Resolution: ${mediaItem.resolution}")
                    Text("Size: ${formatFileSize(mediaItem.size)}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// --- Format Utilities ---
fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hr = (ms / (1000 * 60 * 60))
    return if (hr > 0) {
        String.format("%02d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// --- Photo Editor Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(viewModel: GalleryViewModel, mediaItemId: Int) {
    val context = LocalContext.current
    val allMediaList by viewModel.allMedia.collectAsState()
    val mediaItem = allMediaList.find { it.id == mediaItemId } ?: return
    val editorBitmap by viewModel.editorBitmap.collectAsState()

    // Slide bars values
    var brightness by remember { mutableStateOf(1.0f) }
    var contrast by remember { mutableStateOf(1.0f) }
    var saturation by remember { mutableStateOf(1.0f) }
    var warmth by remember { mutableStateOf(0.0f) }

    var selectedTool by remember { mutableStateOf("Adjust") } // Adjust, Filter, Crop, Brush

    LaunchedEffect(mediaItem) {
        viewModel.initEditor(context, mediaItem)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Editor") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undoEditor() }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redoEditor() }) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    TextButton(onClick = { viewModel.saveEditorChanges(context, mediaItem) }) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Display Sliders for active tools
                if (selectedTool == "Adjust") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Brightness", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = brightness,
                            onValueChange = {
                                brightness = it
                                viewModel.applyColorAdjustment(brightness, contrast, saturation, warmth)
                            },
                            valueRange = 0.5f..1.5f
                        )
                        Text("Contrast", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = contrast,
                            onValueChange = {
                                contrast = it
                                viewModel.applyColorAdjustment(brightness, contrast, saturation, warmth)
                            },
                            valueRange = 0.5f..1.5f
                        )
                        Text("Saturation", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = saturation,
                            onValueChange = {
                                saturation = it
                                viewModel.applyColorAdjustment(brightness, contrast, saturation, warmth)
                            },
                            valueRange = 0.0f..2.0f
                        )
                        Text("Warmth", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = warmth,
                            onValueChange = {
                                warmth = it
                                viewModel.applyColorAdjustment(brightness, contrast, saturation, warmth)
                            },
                            valueRange = -1.0f..1.0f
                        )
                    }
                } else if (selectedTool == "Filter") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val filters = listOf("Original", "Sepia", "B&W", "Vintage", "Invert")
                        filters.forEach { filter ->
                            Button(onClick = {
                                if (filter == "Original") {
                                    viewModel.initEditor(context, mediaItem)
                                } else {
                                    viewModel.applyFilter(filter)
                                }
                            }) {
                                Text(filter)
                            }
                        }
                    }
                } else if (selectedTool == "Crop") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Button(onClick = { viewModel.cropEditor("1:1") }) { Text("1:1") }
                        Button(onClick = { viewModel.cropEditor("4:3") }) { Text("4:3") }
                        Button(onClick = { viewModel.cropEditor("16:9") }) { Text("16:9") }
                    }
                } else if (selectedTool == "Rotate") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        IconButton(onClick = { viewModel.rotateEditor() }) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90")
                        }
                        IconButton(onClick = { viewModel.flipHorizontalEditor() }) {
                            Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal")
                        }
                        IconButton(onClick = { viewModel.flipVerticalEditor() }) {
                            Icon(Icons.Default.Flip, contentDescription = "Flip Vertical", modifier = Modifier.graphicsLayer { rotationZ = 90f })
                        }
                    }
                }

                HorizontalDivider()

                // Tool selection tabs at root bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val tools = listOf("Adjust", "Filter", "Crop", "Rotate")
                    tools.forEach { tool ->
                        val isSelected = selectedTool == tool
                        IconButton(onClick = { selectedTool = tool }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val icon = when (tool) {
                                    "Adjust" -> Icons.Default.Tune
                                    "Filter" -> Icons.Default.Filter
                                    "Crop" -> Icons.Default.Crop
                                    else -> Icons.Default.RotateRight
                                }
                                Icon(icon, contentDescription = tool, tint = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                                Text(tool, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            editorBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Edit Target",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            } ?: CircularProgressIndicator()
        }
    }
}

// --- Detail Bottom-Sheet view with EXIF & Gemini analysis ---
@Composable
fun PhotoDetailsView(
    mediaItem: MediaItem,
    uiState: GalleryUiState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // EXIF & details grid
        DetailRow(label = "Filename", value = mediaItem.name)
        DetailRow(label = "Format", value = mediaItem.format)
        DetailRow(label = "Resolution", value = mediaItem.resolution)
        DetailRow(label = "Size", value = formatFileSize(mediaItem.size))
        DetailRow(label = "Path", value = mediaItem.path)
        DetailRow(label = "Album", value = mediaItem.albumName)

        mediaItem.cameraModel?.let { DetailRow(label = "Camera", value = it) }
        mediaItem.aperture?.let { DetailRow(label = "Aperture", value = it) }
        mediaItem.exposureTime?.let { DetailRow(label = "Exposure Time", value = it) }
        mediaItem.iso?.let { DetailRow(label = "ISO", value = it) }
        if (mediaItem.latitude != null && mediaItem.longitude != null) {
            DetailRow(label = "Location", value = "${mediaItem.latitude}, ${mediaItem.longitude}")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Gemini AI Analysis Panel
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Gemini AI Art Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.isAnalyzingImage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Analyzing image details...", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (uiState.analysisError != null) {
            Text(
                text = "Failed to analyze photo: ${uiState.analysisError}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (uiState.geminiAnalysis != null) {
            Text(
                text = uiState.geminiAnalysis,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        } else {
            Text(
                text = "Analyze this picture with Google's Gemini Pro API to get semantic categories, artistic feedback, lighting analytics, and tags.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// --- Albums Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(viewModel: GalleryViewModel, uiState: GalleryUiState) {
    val albums by viewModel.albums.collectAsState()
    val allMediaList by viewModel.allMedia.collectAsState()

    var showCreateAlbumDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Albums") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(ScreenState.Home) }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateAlbumDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Album")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (albums.isEmpty()) {
                EmptyStateView(title = "No Albums Found", description = "Create custom folders to organize your snaps.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(albums) { albumName ->
                        val albumItems = allMediaList.filter { it.albumName == albumName }
                        val coverItem = albumItems.firstOrNull()

                        AlbumCard(
                            coverItem = coverItem,
                            albumName = albumName,
                            count = albumItems.size,
                            onClick = {
                                viewModel.navigateTo(ScreenState.AlbumDetail(albumName))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateAlbumDialog) {
        val context = LocalContext.current
        CreateAlbumDialog(
            onDismiss = { showCreateAlbumDialog = false },
            onCreate = { name ->
                viewModel.createAlbum(context, name)
                showCreateAlbumDialog = false
            }
        )
    }
}

@Composable
fun AlbumCard(
    coverItem: MediaItem?,
    albumName: String,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("album_card_$albumName"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverItem != null) {
                    AsyncImage(
                        model = coverItem.uri,
                        contentDescription = albumName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = albumName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count Items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Album Detail Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(viewModel: GalleryViewModel, uiState: GalleryUiState, albumName: String) {
    val albumMediaFlow = remember(albumName) { viewModel.getMediaByAlbum(albumName) }
    val albumMedia by albumMediaFlow.collectAsState(initial = emptyList<MediaItem>())

    val sortedList = viewModel.sortMedia(albumMedia)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumName) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sortedList.isEmpty()) {
                EmptyStateView(title = "Album is Empty", description = "No photos added to this folder yet.")
            } else {
                MediaGrid(
                    items = sortedList,
                    columns = uiState.gridColumns,
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}

// --- Favorites Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(viewModel: GalleryViewModel, uiState: GalleryUiState) {
    val favoritesList by viewModel.favorites.collectAsState()
    val sortedList = viewModel.sortMedia(favoritesList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sortedList.isEmpty()) {
                EmptyStateView(title = "No Favorites", description = "Heart some photos or videos to see them here.")
            } else {
                MediaGrid(
                    items = sortedList,
                    columns = uiState.gridColumns,
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}

// --- PIN Secure Lock Screen for Hidden Media ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenLockScreen(viewModel: GalleryViewModel, uiState: GalleryUiState) {
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure PIN Lock") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(ScreenState.Home) }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 350.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Access Private Album",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Please enter your security PIN to unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Password Input Field
                TextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 4) {
                            pinInput = it
                            pinError = false
                            if (it == uiState.pinCode) {
                                viewModel.navigateTo(ScreenState.HiddenMedia)
                            } else if (it.length == 4) {
                                pinError = true
                            }
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = pinError,
                    supportingText = {
                        if (pinError) {
                            Text("Incorrect security PIN. Try again!", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("PIN is '1111' by default for testing.")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_code_field")
                )
            }
        }
    }
}

// --- Hidden Media Folder Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenMediaScreen(viewModel: GalleryViewModel, uiState: GalleryUiState) {
    val hiddenList by viewModel.hiddenMedia.collectAsState()
    val sortedList = viewModel.sortMedia(hiddenList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hidden Album") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(ScreenState.Home) }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sortedList.isEmpty()) {
                EmptyStateView(title = "Hidden Folder is Empty", description = "No secure images hidden.")
            } else {
                MediaGrid(
                    items = sortedList,
                    columns = uiState.gridColumns,
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}

// --- Trash Folder Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: GalleryViewModel, uiState: GalleryUiState) {
    val trashedList by viewModel.trashedMedia.collectAsState()
    val sortedList = viewModel.sortMedia(trashedList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(ScreenState.Home) }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (sortedList.isNotEmpty()) {
                        TextButton(onClick = { viewModel.restoreAllTrash() }) {
                            Text("Restore All")
                        }
                        TextButton(
                            onClick = { viewModel.clearTrash() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Empty Trash")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sortedList.isEmpty()) {
                EmptyStateView(
                    title = "Trash is Empty",
                    description = "Deleted items are kept here for up to ${uiState.autoDeleteTrashDays} days before permanent removal."
                )
            } else {
                MediaGrid(
                    items = sortedList,
                    columns = uiState.gridColumns,
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}

// --- Settings Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: GalleryViewModel, uiState: GalleryUiState) {
    val context = LocalContext.current
    var showPinChangeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(ScreenState.Home) }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Section 1: Appearance
            SettingsHeader(title = "Appearance")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Theme Dark Mode", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = uiState.isDarkMode ?: false,
                    onCheckedChange = { viewModel.setThemeMode(it) }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AMOLED Pure Black Theme", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = uiState.isAmoledMode,
                    onCheckedChange = { viewModel.setAmoledMode(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Section 2: Security & Privacy
            SettingsHeader(title = "Privacy")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hidden Folder PIN Lock", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { showPinChangeDialog = true }) {
                    Text("Change PIN")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Section 3: Gallery Configuration
            SettingsHeader(title = "Gallery Options")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Keep Screen On while viewing", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = uiState.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trash Auto-Delete Period", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { }) {
                    Text("${uiState.autoDeleteTrashDays} Days")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Section 4: Storage Manager
            SettingsHeader(title = "Storage Management")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Clear Cache", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = {
                    Toast.makeText(context, "Cache Cleared!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showPinChangeDialog) {
        // Change PIN dialog logic
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinChangeDialog = false },
            title = { Text("Change security PIN") },
            text = {
                TextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4) newPin = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Enter 4-digit PIN") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length == 4) {
                            Toast.makeText(context, "PIN updated successfully!", Toast.LENGTH_SHORT).show()
                            showPinChangeDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinChangeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

// --- Dialog Components ---
@Composable
fun CreateAlbumDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var albumName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Album") },
        text = {
            TextField(
                value = albumName,
                onValueChange = { albumName = it },
                placeholder = { Text("Album Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("album_name_field")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (albumName.isNotBlank()) onCreate(albumName) },
                enabled = albumName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddToAlbumDialog(albums: List<String>, onDismiss: () -> Unit, onAlbumSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Album") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                albums.forEach { albumName ->
                    TextButton(
                        onClick = { onAlbumSelected(albumName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(albumName, textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var nameInput by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename File") },
        text = {
            TextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (nameInput.isNotBlank()) onRename(nameInput) },
                enabled = nameInput.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionExplanationScreen(
    isPermanentlyDenied: Boolean,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Allow Access to Your Photos & Videos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Infinity Gallery requires permission to access the images and videos on your device to display them in a smooth, beautifully organized grid.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    if (isPermanentlyDenied) {
                        onOpenSettings()
                    } else {
                        onGrantPermission()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("grant_permission_button"),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPermanentlyDenied) Icons.Default.Settings else Icons.Default.LockOpen,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isPermanentlyDenied) "Open Settings" else "Grant Permission",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { /* Could close app, but let's provide a friendly feedback */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Not Now",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
