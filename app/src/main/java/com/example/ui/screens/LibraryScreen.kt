package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistFolderEntity
import com.example.ui.MainViewModel
import com.example.ui.components.BatchPlaylistActionBar
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.LikedSongsBannerCard
import com.example.ui.components.MoveToFolderDialog
import com.example.ui.components.PlaylistFolderListItem
import com.example.ui.components.PlaylistListItem
import com.example.ui.components.PlaylistSortDropdownMenu
import com.example.ui.theme.SpotifyCardBackground
import com.example.ui.theme.SpotifyDarkBackground
import com.example.ui.theme.SpotifyElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyLikedPurple
import com.example.ui.theme.SpotifyPrimaryText
import com.example.ui.theme.SpotifySecondaryText
import com.example.ui.theme.SpotifyWarning
import com.example.util.SortUtils

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val allMissingTracks by viewModel.allMissingTracks.collectAsState()
    val playlistSortOrder by viewModel.playlistSortOrder.collectAsState()
    val selectedPlaylistIds by viewModel.selectedPlaylistIds.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val playlistSongCounts by viewModel.playlistSongCounts.collectAsState()

    var currentFolderId by remember { mutableStateOf<String?>(null) }
    val currentFolder = remember(allFolders, currentFolderId) {
        allFolders.find { it.id == currentFolderId }
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameFolderTarget by remember { mutableStateOf<PlaylistFolderEntity?>(null) }
    var renameFolderNewName by remember { mutableStateOf("") }
    var showBatchMoveFolderDialog by remember { mutableStateOf(false) }
    var playlistToMove by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val displayedPlaylists = remember(allPlaylists, currentFolderId, playlistSortOrder) {
        val filtered = if (currentFolderId == null) {
            allPlaylists.filter { it.folderId == null }
        } else {
            allPlaylists.filter { it.folderId == currentFolderId }
        }
        SortUtils.sortPlaylists(filtered, playlistSortOrder)
    }

    // Import multiple / single files (TXT or CSV)
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                val fileName = cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) it.getString(nameIdx) else null
                    } else null
                } ?: "Playlist"

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    if (fileName.endsWith(".txt", ignoreCase = true)) {
                        viewModel.importTxt(fileName, inputStream)
                    } else {
                        viewModel.importCsv(fileName, inputStream)
                    }
                }
            }
        }
    }

    // Import whole folder tree (detects all CSV and TXT files inside folder)
    val importFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            viewModel.importPlaylistsFromFolderTree(treeUri)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SpotifyDarkBackground)
                .testTag("library_screen"),
            contentPadding = PaddingValues(bottom = if (isMultiSelectMode) 180.dp else 120.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentFolderId != null) {
                                IconButton(
                                    onClick = { currentFolderId = null },
                                    modifier = Modifier.padding(end = 4.dp).testTag("folder_back_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Library",
                                        tint = SpotifyPrimaryText
                                    )
                                }
                            }
                            Text(
                                text = currentFolder?.name ?: "Your Library",
                                color = SpotifyPrimaryText,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("library_sort_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Playlists",
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
                                modifier = Modifier.testTag("library_multiselect_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Multi Select",
                                    tint = if (isMultiSelectMode) SpotifyGreen else SpotifySecondaryText
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showOptionsMenu = true },
                                    modifier = Modifier.testTag("library_more_menu")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add / Import",
                                        tint = SpotifyPrimaryText
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false },
                                    modifier = Modifier.background(SpotifyCardBackground)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Create Playlist", color = SpotifyPrimaryText) },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = SpotifyGreen) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showCreatePlaylistDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Create Playlist Folder (Spotify style)", color = SpotifyPrimaryText) },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = SpotifyGreen) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showCreateFolderDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Import Files (TXT / CSV)", color = SpotifyPrimaryText) },
                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = SpotifyPrimaryText) },
                                        onClick = {
                                            showOptionsMenu = false
                                            importFilesLauncher.launch(arrayOf("text/*", "*/*"))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Import Whole Folder of Playlists", color = SpotifyPrimaryText) },
                                        leadingIcon = { Icon(Icons.Default.DriveFolderUpload, contentDescription = null, tint = SpotifyPrimaryText) },
                                        onClick = {
                                            showOptionsMenu = false
                                            importFolderTreeLauncher.launch(null)
                                        }
                                    )
                                     DropdownMenuItem(
                                        text = { 
                                            Text(
                                                "Retry Matching Missing Songs (${allMissingTracks.size} missing)", 
                                                color = if (allMissingTracks.isNotEmpty()) SpotifyWarning else SpotifyPrimaryText,
                                                fontWeight = FontWeight.SemiBold
                                            ) 
                                        },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = SpotifyGreen) },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.retryMissingMatchesForAllPlaylists()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Check Local Files on Device", color = SpotifySecondaryText) },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.checkLocalFilesExistence()
                                            Toast.makeText(context, "Scanned and verified local music files", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Restore / Backup Data", color = SpotifySecondaryText) },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.openSettings()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (currentFolderId == null) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Liked Songs Item
                        LikedSongsBannerCard(
                            count = likedTracks.size,
                            onClick = { viewModel.openLikedSongs() }
                        )

                        // Missing Songs Banner (if any)
                        if (allMissingTracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openMissingTracksScreen() }
                                    .testTag("missing_songs_banner"),
                                colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SpotifyWarning.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Missing songs",
                                            tint = SpotifyWarning,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${allMissingTracks.size} Missing Songs Detected",
                                            color = SpotifyPrimaryText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tap to review, retry matching, or locate files",
                                            color = SpotifySecondaryText,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Playlist Folders Section (Only at Root)
            if (currentFolderId == null && allFolders.isNotEmpty()) {
                item {
                    Text(
                        text = "Folders (${allFolders.size})",
                        color = SpotifySecondaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                items(allFolders) { folder ->
                    val count = allPlaylists.count { it.folderId == folder.id }
                    PlaylistFolderListItem(
                        folder = folder,
                        playlistCount = count,
                        onClick = { currentFolderId = folder.id },
                        onRenameClick = {
                            renameFolderTarget = folder
                            renameFolderNewName = folder.name
                        },
                        onDeleteClick = {
                            viewModel.deleteFolder(folder.id)
                        },
                        onTogglePin = {
                            viewModel.setFolderPinned(folder.id, !folder.isPinned)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Playlists Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentFolderId == null) "Playlists (${displayedPlaylists.size})" else "Playlists in Folder (${displayedPlaylists.size})",
                        color = SpotifyPrimaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = playlistSortOrder.label,
                        color = SpotifySecondaryText,
                        fontSize = 12.sp
                    )
                }
            }

            if (displayedPlaylists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (currentFolderId == null) "No playlists yet. Tap + to create or import." else "This folder is empty.",
                            color = SpotifySecondaryText,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            items(displayedPlaylists) { playlist ->
                val isSelected = selectedPlaylistIds.contains(playlist.id)
                PlaylistListItem(
                    playlist = playlist,
                    songCount = playlistSongCounts[playlist.id] ?: 0,
                    isMultiSelectMode = isMultiSelectMode,
                    isSelected = isSelected,
                    onSelectToggle = { checked ->
                        viewModel.togglePlaylistSelection(playlist.id)
                    },
                    onLongClick = {
                        viewModel.setMultiSelectMode(true)
                        viewModel.togglePlaylistSelection(playlist.id)
                    },
                    onClick = { viewModel.openPlaylist(playlist) }
                )
            }
        }

        // Batch Playlist Action Bar
        if (isMultiSelectMode && selectedPlaylistIds.isNotEmpty()) {
            val allIds = displayedPlaylists.map { it.id }
            BatchPlaylistActionBar(
                selectedCount = selectedPlaylistIds.size,
                totalCount = allIds.size,
                onSelectAll = {
                    if (selectedPlaylistIds.size == allIds.size) {
                        viewModel.clearSelection()
                    } else {
                        viewModel.selectAllPlaylists(allIds)
                    }
                },
                onClearSelection = { viewModel.clearSelection() },
                onMoveToFolder = { showBatchMoveFolderDialog = true },
                onLikeAllSongs = { viewModel.batchLikeSongsOfSelectedPlaylists() },
                onExportPlaylists = {
                    viewModel.exportSelectedPlaylistsAsZip(context)
                },
                onDeletePlaylists = {
                    showBatchDeleteConfirm = true
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Sort Dropdown Menu
    PlaylistSortDropdownMenu(
        expanded = showSortMenu,
        currentSort = playlistSortOrder,
        onSortSelected = { newSort ->
            viewModel.setPlaylistSortOrder(newSort)
        },
        onDismiss = { showSortMenu = false }
    )

    // Move to Folder Dialog (Single Playlist or Batch)
    if (playlistToMove != null) {
        MoveToFolderDialog(
            folders = allFolders,
            currentFolderId = playlistToMove?.folderId,
            onSelectFolder = { targetFolderId ->
                playlistToMove?.let { p ->
                    viewModel.movePlaylistToFolder(p.id, targetFolderId)
                }
                playlistToMove = null
            },
            onCreateNewFolder = {
                showCreateFolderDialog = true
            },
            onDismiss = { playlistToMove = null }
        )
    }

    if (showBatchMoveFolderDialog) {
        MoveToFolderDialog(
            folders = allFolders,
            currentFolderId = currentFolderId,
            onSelectFolder = { targetFolderId ->
                viewModel.batchMovePlaylistsToFolder(targetFolderId)
                showBatchMoveFolderDialog = false
            },
            onCreateNewFolder = {
                showCreateFolderDialog = true
            },
            onDismiss = { showBatchMoveFolderDialog = false }
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onConfirm = { folderName ->
                viewModel.createFolder(folderName)
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    // Rename Folder Dialog
    if (renameFolderTarget != null) {
        AlertDialog(
            onDismissRequest = { renameFolderTarget = null },
            containerColor = SpotifyElevated,
            title = { Text("Rename Folder", color = SpotifyPrimaryText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameFolderNewName,
                    onValueChange = { renameFolderNewName = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = SpotifyPrimaryText,
                        unfocusedTextColor = SpotifyPrimaryText,
                        focusedContainerColor = SpotifyCardBackground,
                        unfocusedContainerColor = SpotifyCardBackground,
                        focusedIndicatorColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameFolderNewName.isNotBlank()) {
                            renameFolderTarget?.let { f ->
                                viewModel.renameFolder(f.id, renameFolderNewName.trim())
                            }
                            renameFolderTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text("Rename", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameFolderTarget = null }) {
                    Text("Cancel", color = SpotifySecondaryText)
                }
            }
        )
    }

    // Batch Delete Dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("Delete selected playlists?", color = SpotifyPrimaryText) },
            text = {
                Text(
                    "Are you sure you want to delete ${selectedPlaylistIds.size} playlists? (Your local music files on device will NOT be deleted)",
                    color = SpotifySecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.batchDeleteSelectedPlaylists()
                        showBatchDeleteConfirm = false
                        Toast.makeText(context, "Deleted selected playlists", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel", color = SpotifySecondaryText)
                }
            },
            containerColor = SpotifyCardBackground
        )
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Give your playlist a name", color = SpotifyPrimaryText) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("My Playlist #1") },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = SpotifyPrimaryText,
                        unfocusedTextColor = SpotifyPrimaryText,
                        focusedContainerColor = SpotifyElevated,
                        unfocusedContainerColor = SpotifyElevated,
                        focusedIndicatorColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName.trim(), currentFolderId)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = SpotifySecondaryText)
                }
            },
            containerColor = SpotifyCardBackground
        )
    }
}

