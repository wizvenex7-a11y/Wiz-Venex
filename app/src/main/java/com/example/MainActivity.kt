package com.example

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.DuplicatePlaylistDialog
import com.example.ui.components.FullPlayerModal
import com.example.ui.components.ImportSummaryDialog
import com.example.ui.components.MiniPlayer
import com.example.ui.components.QueueModal
import com.example.ui.components.RestoreReportDialog
import com.example.ui.components.SongOptionsMenu
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LikedSongsScreen
import com.example.ui.screens.MissingTracksScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SpotifyDarkBackground
import com.example.ui.theme.SpotifyDarkSecondary
import com.example.ui.theme.SpotifyElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyPrimaryText
import com.example.ui.theme.SpotifySecondaryText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(permissionToRequest)
        }
    }

    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPos by viewModel.currentPositionMs.collectAsState()
    val duration by viewModel.durationMs.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val queue by viewModel.queue.collectAsState()

    val isFullPlayerExpanded by viewModel.isFullPlayerExpanded.collectAsState()
    val isQueueVisible by viewModel.isQueueVisible.collectAsState()

    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgressCount by viewModel.scanProgressCount.collectAsState()
    val scanTotalCount by viewModel.scanTotalCount.collectAsState()
    val scanProgressPercent by viewModel.scanProgressPercent.collectAsState()

    val statusMessage by viewModel.statusMessage.collectAsState()
    val duplicatePrompt by viewModel.duplicatePrompt.collectAsState()
    val importSummary by viewModel.lastImportSummary.collectAsState()
    val restoreReport by viewModel.restoreReport.collectAsState()

    val selectedTrackForOptions by viewModel.selectedTrackForOptions.collectAsState()
    val selectedTrackForAddToPlaylist by viewModel.selectedTrackForAddToPlaylist.collectAsState()
    val isDeleteConfirmationVisible by viewModel.isDeleteConfirmationVisible.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val activePlaylist by viewModel.activePlaylist.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val activity = context as? Activity
    BackHandler(enabled = true) {
        when {
            isFullPlayerExpanded -> viewModel.collapseFullPlayer()
            isQueueVisible -> viewModel.setQueueVisible(false)
            selectedTrackForOptions != null -> viewModel.setSelectedTrackForOptions(null)
            selectedTrackForAddToPlaylist != null -> viewModel.setSelectedTrackForAddToPlaylist(null)
            currentScreen is Screen.PlaylistDetail || currentScreen is Screen.LikedSongs || currentScreen is Screen.MissingTracks -> {
                viewModel.navigateTo(Screen.Library)
            }
            currentScreen is Screen.Search || currentScreen is Screen.Settings || currentScreen is Screen.Library -> {
                viewModel.navigateTo(Screen.Home)
            }
            currentScreen is Screen.Home -> {
                activity?.moveTaskToBack(true)
            }
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val progress = if (duration > 0) (currentPos.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SpotifyDarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                // Mini Player docked right above bottom navigation (always visible when music is active)
                if (currentTrack != null) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        progress = progress,
                        onExpandClick = { viewModel.expandFullPlayer() },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onLikeToggle = { currentTrack?.let { viewModel.toggleLike(it) } }
                    )
                }

                // Persistent Bottom Navigation Bar across ALL screens
                val isLibraryActive = currentScreen is Screen.Library ||
                        currentScreen is Screen.PlaylistDetail ||
                        currentScreen is Screen.LikedSongs ||
                        currentScreen is Screen.MissingTracks

                NavigationBar(
                    containerColor = SpotifyDarkSecondary,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Home,
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen is Screen.Home) Icons.Default.Home else Icons.Outlined.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Home", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifySecondaryText,
                            unselectedTextColor = SpotifySecondaryText,
                            indicatorColor = SpotifyElevated
                        ),
                        modifier = Modifier.testTag("nav_home")
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Search,
                        onClick = { viewModel.navigateTo(Screen.Search) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen is Screen.Search) Icons.Default.Search else Icons.Outlined.Search,
                                contentDescription = "Search"
                            )
                        },
                        label = { Text("Search", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifySecondaryText,
                            unselectedTextColor = SpotifySecondaryText,
                            indicatorColor = SpotifyElevated
                        ),
                        modifier = Modifier.testTag("nav_search")
                    )

                    NavigationBarItem(
                        selected = isLibraryActive,
                        onClick = { viewModel.navigateTo(Screen.Library) },
                        icon = {
                            Icon(
                                imageVector = if (isLibraryActive) Icons.Default.QueueMusic else Icons.Outlined.QueueMusic,
                                contentDescription = "Your Library"
                            )
                        },
                        label = { Text("Your Library", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifySecondaryText,
                            unselectedTextColor = SpotifySecondaryText,
                            indicatorColor = SpotifyElevated
                        ),
                        modifier = Modifier.testTag("nav_library")
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Settings,
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen is Screen.Settings) Icons.Default.Settings else Icons.Outlined.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifySecondaryText,
                            unselectedTextColor = SpotifySecondaryText,
                            indicatorColor = SpotifyElevated
                        ),
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isScanning) {
                Surface(
                    color = SpotifyElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_progress_banner")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Importing songs to library...",
                                color = SpotifyPrimaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (scanTotalCount > 0) "$scanProgressCount / $scanTotalCount (${(scanProgressPercent * 100).toInt()}%)" else "$scanProgressCount found",
                                color = SpotifyGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { scanProgressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = SpotifyGreen,
                            trackColor = SpotifyDarkSecondary
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val screen = currentScreen) {
                    is Screen.Home -> HomeScreen(viewModel = viewModel)
                    is Screen.Search -> SearchScreen(viewModel = viewModel)
                    is Screen.Library -> LibraryScreen(viewModel = viewModel)
                    is Screen.Settings -> SettingsScreen(viewModel = viewModel)
                    is Screen.LikedSongs -> LikedSongsScreen(viewModel = viewModel)
                    is Screen.MissingTracks -> MissingTracksScreen(viewModel = viewModel)
                    is Screen.PlaylistDetail -> {
                        val p = activePlaylist ?: allPlaylists.firstOrNull { it.id == screen.playlistId }
                        if (p != null) {
                            PlaylistDetailScreen(playlist = p, viewModel = viewModel)
                        } else {
                            LibraryScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    // Full Player Modal
    AnimatedVisibility(
        visible = isFullPlayerExpanded,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        FullPlayerModal(
            track = currentTrack,
            isPlaying = isPlaying,
            currentPositionMs = currentPos,
            durationMs = duration,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            onClose = { viewModel.collapseFullPlayer() },
            onPlayPause = { viewModel.togglePlayPause() },
            onSeek = { viewModel.seekTo(it) },
            onNext = { viewModel.skipNext() },
            onPrevious = { viewModel.skipPrevious() },
            onShuffleToggle = { viewModel.toggleShuffle() },
            onRepeatToggle = { viewModel.toggleRepeat() },
            onLikeToggle = { currentTrack?.let { viewModel.toggleLike(it) } },
            onOpenQueue = { viewModel.setQueueVisible(true) }
        )
    }

    // Queue Sheet
    if (isQueueVisible) {
        QueueModal(
            queue = queue,
            currentTrack = currentTrack,
            onTrackSelect = { viewModel.playTrack(it, queue) },
            onClose = { viewModel.setQueueVisible(false) }
        )
    }

    // Track Options Menu
    if (selectedTrackForOptions != null) {
        val track = selectedTrackForOptions
        SongOptionsMenu(
            track = track,
            onDismiss = { viewModel.setSelectedTrackForOptions(null) },
            onAddToPlaylist = {
                viewModel.setSelectedTrackForAddToPlaylist(track)
                viewModel.setSelectedTrackForOptions(null)
            },
            onRemoveFromPlaylist = null,
            onLikeToggle = {
                track?.let { viewModel.toggleLike(it) }
                viewModel.setSelectedTrackForOptions(null)
            },
            onPlayNext = {
                track?.let { viewModel.playNext(it) }
                viewModel.setSelectedTrackForOptions(null)
            },
            onAddToQueue = {
                track?.let { viewModel.addToQueue(it) }
                viewModel.setSelectedTrackForOptions(null)
            },
            onDeleteFromLibrary = {
                viewModel.showDeleteConfirmation(true)
            }
        )
    }

    // Add to Playlist Dialog
    if (selectedTrackForAddToPlaylist != null) {
        val track = selectedTrackForAddToPlaylist
        AddToPlaylistDialog(
            playlists = allPlaylists,
            onSelectPlaylist = { p ->
                if (track != null) {
                    viewModel.addTrackToPlaylist(p.id, track)
                }
            },
            onDismiss = { viewModel.setSelectedTrackForAddToPlaylist(null) }
        )
    }

    // Delete Confirmation Dialog
    if (isDeleteConfirmationVisible && selectedTrackForOptions != null) {
        DeleteConfirmationDialog(
            track = selectedTrackForOptions!!,
            onConfirm = { viewModel.confirmDeleteTrackFromLibrary() },
            onDismiss = { viewModel.showDeleteConfirmation(false) }
        )
    }

    // Duplicate Playlist Import Dialog
    duplicatePrompt?.let { prompt ->
        DuplicatePlaylistDialog(
            prompt = prompt,
            onResolve = { action -> viewModel.resolveDuplicate(action) }
        )
    }

    // Import Summary Dialog
    importSummary?.let { summary ->
        ImportSummaryDialog(
            summary = summary,
            onDismiss = { viewModel.clearImportSummary() },
            onViewPlaylist = { pId ->
                val p = allPlaylists.firstOrNull { it.id == pId }
                if (p != null) {
                    viewModel.openPlaylist(p)
                }
            }
        )
    }

    // Restore Report Dialog
    restoreReport?.let { report ->
        RestoreReportDialog(
            report = report,
            onDismiss = { viewModel.clearRestoreReport() },
            onViewMissing = { viewModel.openMissingTracksScreen() }
        )
    }
}
