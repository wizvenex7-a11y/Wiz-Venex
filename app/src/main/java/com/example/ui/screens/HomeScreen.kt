package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.demo.DemoMusicManager
import com.example.ui.MainViewModel
import com.example.ui.components.CoverArtImage
import com.example.ui.components.LikedSongsBannerCard
import com.example.ui.components.TrackRowItem
import com.example.util.toTitleCaseDisplay
import com.example.ui.theme.SpotifyCardBackground
import com.example.ui.theme.SpotifyDarkBackground
import com.example.ui.theme.SpotifyElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyPrimaryText
import com.example.ui.theme.SpotifySecondaryText
import java.util.Calendar

import androidx.compose.material.icons.filled.Checklist
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import com.example.ui.components.BatchSongActionBar
import com.example.ui.components.AddToPlaylistDialog

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTracks by viewModel.allTracks.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()
    val currentPlayingTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val isMultiSelectMode by viewModel.isSongMultiSelectActive.collectAsState()
    val selectedTrackIds by viewModel.selectedSongIds.collectAsState()
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SpotifyDarkBackground)
                .testTag("home_screen"),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Top Bar & Greeting
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = greeting,
                            color = SpotifyPrimaryText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Liked Songs Quick Card
                    LikedSongsBannerCard(
                        count = likedTracks.size,
                        onClick = { viewModel.openLikedSongs() }
                    )
                }
            }

            // Recently Played Section
            if (recentlyPlayed.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Recently Played",
                        color = SpotifyPrimaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(recentlyPlayed.take(8)) { track ->
                            Column(
                                modifier = Modifier
                                    .width(120.dp)
                                    .clickable { viewModel.playTrack(track, recentlyPlayed) }
                            ) {
                                CoverArtImage(
                                    coverPath = track.coverPath,
                                    modifier = Modifier.size(120.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = track.title.toTitleCaseDisplay,
                                    color = SpotifyPrimaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist.toTitleCaseDisplay,
                                    color = SpotifySecondaryText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Playlists Horizontal Section
            if (allPlaylists.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Your Playlists",
                        color = SpotifyPrimaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(allPlaylists) { playlist ->
                            Column(
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable { viewModel.openPlaylist(playlist) }
                                    .testTag("playlist_card_${playlist.id}")
                            ) {
                                CoverArtImage(
                                    coverPath = playlist.coverPath,
                                    modifier = Modifier.size(130.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = playlist.name.toTitleCaseDisplay,
                                    color = SpotifyPrimaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Playlist",
                                    color = SpotifySecondaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // All Songs List Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Imported Music (${allTracks.size})",
                        color = SpotifyPrimaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = {
                            if (isMultiSelectMode) {
                                viewModel.clearSongSelection()
                            } else {
                                viewModel.setSongMultiSelectActive(true)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Multi Select",
                                tint = if (isMultiSelectMode) SpotifyGreen else SpotifySecondaryText
                            )
                        }
                        if (allTracks.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.playTrack(allTracks.first(), allTracks)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play all",
                                    tint = SpotifyGreen
                                )
                            }
                        }
                    }
                }
            }

            items(allTracks) { track ->
                val isSelected = selectedTrackIds.contains(track.id)
                TrackRowItem(
                    track = track,
                    isPlaying = isPlaying && currentPlayingTrack?.id == track.id,
                    isMultiSelectMode = isMultiSelectMode,
                    isSelected = isSelected,
                    onSelectToggle = { checked ->
                        viewModel.toggleSongSelection(track.id)
                    },
                    onLongClick = {
                        viewModel.setSongMultiSelectActive(true)
                        viewModel.toggleSongSelection(track.id)
                    },
                    onTrackClick = { viewModel.playTrack(track, allTracks) },
                    onLikeToggle = { viewModel.toggleLike(track) },
                    onOptionsClick = { viewModel.setSelectedTrackForOptions(track) }
                )
            }
        }

        if (isMultiSelectMode && selectedTrackIds.isNotEmpty()) {
            val allIds = allTracks.map { it.id }
            val selectedTracks = allTracks.filter { selectedTrackIds.contains(it.id) }
            BatchSongActionBar(
                selectedCount = selectedTrackIds.size,
                totalCount = allIds.size,
                isInPlaylist = false,
                onSelectAll = {
                    if (selectedTrackIds.size == allIds.size) {
                        viewModel.clearSongSelection()
                    } else {
                        viewModel.selectAllSongs(allIds)
                    }
                },
                onClearSelection = { viewModel.clearSongSelection() },
                onAddToQueue = { viewModel.addBatchToQueue(selectedTracks) },
                onAddToPlaylist = { showAddToPlaylistDialog = true },
                onToggleLike = { viewModel.batchLikeSelectedSongs(true) }, // Like all selected
                onExport = { /* export selection */ },
                onRemove = { viewModel.batchDeleteSelectedSongsFromLibrary() }, // Delete from library
                onCheckDuplicates = { viewModel.batchCheckAndCleanDuplicates(selectedTrackIds.toList()) }, // Fast clean duplicates
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showAddToPlaylistDialog) {
            AddToPlaylistDialog(
                playlists = allPlaylists,
                onSelectPlaylist = { playlist ->
                    val selectedTracks = allTracks.filter { selectedTrackIds.contains(it.id) }
                    selectedTracks.forEach { tr ->
                        viewModel.addTrackToPlaylist(playlist.id, tr)
                    }
                    showAddToPlaylistDialog = false
                    viewModel.clearSongSelection()
                },
                onDismiss = { showAddToPlaylistDialog = false }
            )
        }
    }
}
