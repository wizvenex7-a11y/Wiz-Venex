package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.SpotifyDarkBackground
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyLikedPurple
import com.example.ui.theme.SpotifyLikedRed
import com.example.ui.theme.SpotifyPrimaryText
import com.example.ui.theme.SpotifySecondaryText

import androidx.compose.material.icons.filled.Checklist
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.BatchSongActionBar

@Composable
fun LikedSongsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val likedTracks by viewModel.likedTracks.collectAsState()
    val currentPlayingTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val isMultiSelectMode by viewModel.isSongMultiSelectActive.collectAsState()
    val selectedTrackIds by viewModel.selectedSongIds.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SpotifyDarkBackground)
                .testTag("liked_songs_screen"),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Back Button & MultiSelect Toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        modifier = Modifier.testTag("liked_songs_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SpotifyPrimaryText
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isMultiSelectMode) {
                                viewModel.clearSongSelection()
                            } else {
                                viewModel.setSongMultiSelectActive(true)
                            }
                        },
                        modifier = Modifier.testTag("liked_songs_multiselect")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = "Multi Select",
                            tint = if (isMultiSelectMode) SpotifyGreen else SpotifySecondaryText
                        )
                    }
                }
            }

            // Header with gradient art
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(SpotifyLikedPurple, Color(0xFF450AF5), Color(0xFFC4EFD9))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart",
                            tint = Color.White,
                            modifier = Modifier.size(90.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Liked Songs",
                        color = SpotifyPrimaryText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${likedTracks.size} songs · Auto-synced permanent playlist",
                        color = SpotifySecondaryText,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Play & Shuffle controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (likedTracks.isNotEmpty()) {
                                    viewModel.toggleShuffle()
                                    viewModel.playTrack(likedTracks.first(), likedTracks)
                                }
                            },
                            modifier = Modifier.testTag("liked_songs_shuffle")
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
                                    if (likedTracks.isNotEmpty()) {
                                        viewModel.playTrack(likedTracks.first(), likedTracks)
                                    }
                                }
                                .testTag("liked_songs_play"),
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

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (likedTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Songs you like will appear here.\nTap the heart icon on any song to save it!",
                            color = SpotifySecondaryText,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(likedTracks) { track ->
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
                        onTrackClick = { viewModel.playTrack(track, likedTracks) },
                        onLikeToggle = { viewModel.toggleLike(track) },
                        onQuickAddToPlaylist = {
                            if (!viewModel.quickAddToLastPlaylist(track)) {
                                viewModel.setSelectedTrackForAddToPlaylist(track)
                            }
                        },
                        onOptionsClick = { viewModel.setSelectedTrackForOptions(track) }
                    )
                }
            }
        }

        if (isMultiSelectMode && selectedTrackIds.isNotEmpty()) {
            val allIds = likedTracks.map { it.id }
            val selectedTracks = likedTracks.filter { selectedTrackIds.contains(it.id) }
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
                onToggleLike = { viewModel.removeBatchFromLiked(selectedTrackIds.toList()) },
                onRemove = { viewModel.removeBatchFromLiked(selectedTrackIds.toList()) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showAddToPlaylistDialog) {
            AddToPlaylistDialog(
                playlists = allPlaylists,
                onSelectPlaylist = { playlist ->
                    viewModel.batchAddSelectedSongsToPlaylist(playlist.id)
                    showAddToPlaylistDialog = false
                },
                onDismiss = { showAddToPlaylistDialog = false },
                onCreatePlaylist = { name ->
                    viewModel.createPlaylistAndAddSelectedSongs(name) { showAddToPlaylistDialog = false }
                }
            )
        }
    }
}
