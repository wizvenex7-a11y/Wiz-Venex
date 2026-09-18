package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackEntity
import com.example.data.model.TrackEntity
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.BatchSongActionBar
import com.example.ui.components.CoverArtImage
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.DuplicateReviewDialog
import com.example.ui.components.MissingTrackRowItem
import com.example.ui.components.MoveToFolderDialog
import com.example.ui.components.SongSortDropdownMenu
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.SpotifyCardBackground
import com.example.ui.theme.SpotifyDarkBackground
import com.example.ui.theme.SpotifyElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyPrimaryText
import com.example.ui.theme.SpotifySecondaryText
import com.example.ui.theme.SpotifyWarning
import com.example.util.SortUtils
import com.example.util.toTitleCaseDisplay

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTracks by viewModel.allTracks.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()
    val currentPlayingTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val hideUnplayableSongs by viewModel.hideUnplayableSongs.collectAsState()
    val songSortOrder by viewModel.songSortOrder.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val duplicateGroups by viewModel.activePlaylistDuplicates.collectAsState()
    val missingFileTrackIds by viewModel.missingFileTrackIds.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()

    val entriesFlow = remember(playlist.id) { viewModel.repository.getPlaylistEntries(playlist.id) }
    val rawEntries by entriesFlow.collectAsState(initial = emptyList())

    val tracksMap = remember(allTracks) { allTracks.associateBy { it.id } }

    val sortedEntries = remember(rawEntries, songSortOrder, tracksMap) {
        val mappedList = rawEntries.map { entry ->
            val track = if (!entry.isMissing && entry.trackId != null) tracksMap[entry.trackId] else null
            entry to track
        }
        val sortedList = SortUtils.sortPlaylistEntries(mappedList, songSortOrder)
        sortedList.map { it.first }
    }

    val matchedTracks = sortedEntries.mapNotNull { entry ->
        if (!entry.isMissing && entry.trackId != null) tracksMap[entry.trackId] else null
    }
    val missingCount = sortedEntries.count { it.isMissing || it.trackId == null }
    val displayEntries = remember(sortedEntries, hideUnplayableSongs) {
        if (hideUnplayableSongs) {
            sortedEntries.filterNot { it.isMissing || it.trackId == null }
        } else {
            sortedEntries
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var locatingEntry by remember { mutableStateOf<PlaylistTrackEntity?>(null) }
    val allFolders by viewModel.allFolders.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SpotifyDarkBackground)
                .testTag("playlist_detail_screen"),
            contentPadding = PaddingValues(bottom = if (isMultiSelectMode) 180.dp else 120.dp)
        ) {
            // Top Navigation Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isMultiSelectMode) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.navigateTo(Screen.Library)
                            }
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SpotifyPrimaryText
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = SpotifyPrimaryText
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isMultiSelectMode) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.setMultiSelectMode(true)
                                }
                            },
                            modifier = Modifier.testTag("multi_select_toggle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Multi Select",
                                tint = if (isMultiSelectMode) SpotifyGreen else SpotifySecondaryText
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("playlist_more_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = SpotifySecondaryText
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(SpotifyCardBackground)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Find & Remove Duplicates", color = SpotifyPrimaryText) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.findDuplicatesInActivePlaylist(playlist.id)
                                        showDuplicatesDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Check Local Files on Device", color = SpotifyPrimaryText) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.checkLocalFilesExistence()
                                        Toast.makeText(context, "Checked all local audio files", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (hideUnplayableSongs) "Show unplayable songs" else "Hide unplayable songs",
                                            color = SpotifyPrimaryText
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleHideUnplayableSongs()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Folder", color = SpotifyPrimaryText) },
                                    onClick = {
                                        showMenu = false
                                        showMoveFolderDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Playlist (TXT/CSV)", color = SpotifyPrimaryText) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.exportPlaylistTxt(context, playlist.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Retry matching missing songs", color = SpotifyGreen) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.retryMissingMatches(playlist.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete playlist", color = Color.Red) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Header: Cover + Title + Details
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CoverArtImage(
                        coverPath = playlist.coverPath,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = playlist.name.toTitleCaseDisplay,
                        color = SpotifyPrimaryText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Playlist · ${sortedEntries.size} songs ${if (missingCount > 0) "($missingCount unplayable)" else ""} · Sorted by ${songSortOrder.label}",
                        color = SpotifySecondaryText,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Play and Shuffle Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (matchedTracks.isNotEmpty()) {
                                    if (!isShuffle) {
                                        viewModel.toggleShuffle()
                                    }
                                    val randomTrack = matchedTracks.random()
                                    viewModel.playTrack(randomTrack, matchedTracks)
                                }
                            },
                            modifier = Modifier.testTag("playlist_shuffle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffle) SpotifyGreen else SpotifySecondaryText,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen)
                                .clickable {
                                    if (matchedTracks.isNotEmpty()) {
                                        viewModel.playTrack(matchedTracks.first(), matchedTracks)
                                    }
                                }
                                .testTag("playlist_play_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // Missing Songs Banner (if any in this playlist)
            if (missingCount > 0) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("missing_songs_card"),
                        colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = "Unplayable",
                                    tint = SpotifySecondaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hideUnplayableSongs) "$missingCount unplayable songs (hidden)" else "$missingCount unplayable songs",
                                    color = SpotifyPrimaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.toggleHideUnplayableSongs() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        if (hideUnplayableSongs) "Show Unplayable" else "Hide Unplayable",
                                        fontSize = 12.sp,
                                        color = SpotifyPrimaryText
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.retryMissingMatches(playlist.id) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry Matching", fontSize = 12.sp, color = SpotifyGreen)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Ordered Playlist Tracks
            items(displayEntries) { entry ->
                if (entry.isMissing || entry.trackId == null) {
                    MissingTrackRowItem(
                        entry = entry,
                        onLocateClick = { locatingEntry = entry }
                    )
                } else {
                    val track = tracksMap[entry.trackId]
                    if (track != null) {
                        val isSelected = selectedSongIds.contains(track.id)
                        val isMissingOnDisk = missingFileTrackIds.contains(track.id)
                        TrackRowItem(
                            track = track,
                            isPlaying = isPlaying && currentPlayingTrack?.id == track.id,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = isSelected,
                            isMissingLocally = isMissingOnDisk,
                            onSelectToggle = { checked ->
                                viewModel.toggleSongSelection(track.id)
                            },
                            onLongClick = {
                                viewModel.setMultiSelectMode(true)
                                viewModel.toggleSongSelection(track.id)
                            },
                            onTrackClick = { viewModel.playTrack(track, matchedTracks) },
                            onLikeToggle = { viewModel.toggleLike(track) },
                            onOptionsClick = { viewModel.setSelectedTrackForOptions(track) }
                        )
                    }
                }
            }
        }

        // Persistent Batch Action Bar for Songs
        if (isMultiSelectMode && selectedSongIds.isNotEmpty()) {
            val allTrackIds = matchedTracks.map { it.id }
            BatchSongActionBar(
                selectedCount = selectedSongIds.size,
                totalCount = allTrackIds.size,
                isInPlaylist = true,
                onSelectAll = {
                    if (selectedSongIds.size == allTrackIds.size) {
                        viewModel.clearSelection()
                    } else {
                        viewModel.selectAllSongs(allTrackIds)
                    }
                },
                onClearSelection = { viewModel.clearSelection() },
                onAddToQueue = {
                    viewModel.batchAddSelectedSongsToQueue()
                    Toast.makeText(context, "Added ${selectedSongIds.size} songs to queue", Toast.LENGTH_SHORT).show()
                },
                onAddToPlaylist = {
                    showAddToPlaylistDialog = true
                },
                onToggleLike = {
                    viewModel.batchLikeSelectedSongs(true)
                    Toast.makeText(context, "Updated liked songs", Toast.LENGTH_SHORT).show()
                },
                onExport = {
                    viewModel.exportSelectedSongsTxt(context)
                },
                onRemove = {
                    viewModel.batchRemoveSelectedEntriesFromPlaylist(playlist.id)
                    Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Sort Dropdown Menu
    SongSortDropdownMenu(
        expanded = showSortMenu,
        currentSort = songSortOrder,
        onSortSelected = { newSort ->
            viewModel.setSongSortOrder(newSort)
        },
        onDismiss = { showSortMenu = false }
    )

    // Duplicates Dialog
    if (showDuplicatesDialog) {
        DuplicateReviewDialog(
            duplicateGroups = duplicateGroups,
            onRemoveDuplicates = { entryIds ->
                viewModel.removeDuplicatesFromPlaylist(playlist.id, entryIds)
                Toast.makeText(context, "Removed duplicate songs", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showDuplicatesDialog = false }
        )
    }

    // Batch Add to Playlist Dialog
    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            onSelectPlaylist = { targetPlaylist ->
                viewModel.batchAddSelectedSongsToPlaylist(targetPlaylist.id)
                showAddToPlaylistDialog = false
                Toast.makeText(context, "Added to ${targetPlaylist.name}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    // Delete Playlist Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete playlist?", color = SpotifyPrimaryText) },
            text = {
                Text(
                    "Are you sure you want to delete '${playlist.name}'? (Your local music files will NOT be deleted)",
                    color = SpotifySecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deletePlaylist(playlist.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = SpotifySecondaryText)
                }
            },
            containerColor = SpotifyCardBackground
        )
    }

    // Manual Locate Track Picker Dialog
    if (locatingEntry != null) {
        AlertDialog(
            onDismissRequest = { locatingEntry = null },
            title = { Text("Locate Song in Library", color = SpotifyPrimaryText) },
            text = {
                Column {
                    Text(
                        text = "Match '${locatingEntry?.csvTitle}' with one of your local files:",
                        color = SpotifySecondaryText,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(allTracks) { localTrack ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val entry = locatingEntry
                                        if (entry != null) {
                                            viewModel.manualLocateTrack(entry.entryId, localTrack)
                                        }
                                        locatingEntry = null
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoverArtImage(coverPath = localTrack.coverPath, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = localTrack.title,
                                        color = SpotifyPrimaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = localTrack.artist,
                                        color = SpotifySecondaryText,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { locatingEntry = null }) {
                    Text("Cancel", color = SpotifySecondaryText)
                }
            },
            containerColor = SpotifyCardBackground
        )
    }

    if (showMoveFolderDialog) {
        MoveToFolderDialog(
            folders = allFolders,
            currentFolderId = playlist.folderId,
            onSelectFolder = { targetFolderId ->
                viewModel.movePlaylistToFolder(playlist.id, targetFolderId)
                showMoveFolderDialog = false
            },
            onCreateNewFolder = {
                showCreateFolderDialog = true
            },
            onDismiss = { showMoveFolderDialog = false }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { folderName ->
                viewModel.createFolder(folderName)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
}
