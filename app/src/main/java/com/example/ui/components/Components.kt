package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import com.example.util.toTitleCaseDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.backup.RestoreReport
import com.example.data.model.DuplicateGroup
import com.example.data.model.DuplicateItem
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackEntity
import com.example.data.model.TrackEntity
import com.example.data.repository.DuplicateAction
import com.example.data.repository.ImportSummary
import com.example.player.RepeatMode
import com.example.ui.DuplicatePrompt
import com.example.ui.theme.SpotifyCardBackground
import com.example.ui.theme.SpotifyDarkBackground
import com.example.ui.theme.SpotifyDarkSecondary
import com.example.ui.theme.SpotifyElevated
import com.example.ui.theme.SpotifyError
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyLikedPurple
import com.example.ui.theme.SpotifyLikedRed
import com.example.ui.theme.SpotifyMuted
import com.example.ui.theme.SpotifyPrimaryText
import com.example.ui.theme.SpotifySecondaryText
import com.example.ui.theme.SpotifyWarning
import com.example.util.ColorExtractor
import com.example.util.NormalizationUtils
import com.example.util.PlaylistSortOrder
import com.example.util.SongSortOrder
import java.io.File

@Composable
fun CoverArtImage(
    coverPath: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Cover Art"
) {
    val squareModifier = modifier.aspectRatio(1f)
    if (!coverPath.isNullOrBlank() && File(coverPath).exists()) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = contentDescription,
            modifier = squareModifier.clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = squareModifier
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = contentDescription,
                tint = SpotifyMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: TrackEntity,
    isPlaying: Boolean,
    onTrackClick: () -> Unit,
    onLikeToggle: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    isMissingLocally: Boolean = false,
    onSelectToggle: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) SpotifyElevated.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode && onSelectToggle != null) {
                        onSelectToggle(!isSelected)
                    } else {
                        onTrackClick()
                    }
                },
                onLongClick = {
                    onLongClick?.invoke()
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("track_row_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { checked -> onSelectToggle?.invoke(checked) },
                colors = CheckboxDefaults.colors(
                    checkedColor = SpotifyGreen,
                    uncheckedColor = SpotifySecondaryText,
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        CoverArtImage(
            coverPath = track.coverPath,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.toTitleCaseDisplay,
                color = if (isPlaying) SpotifyGreen else SpotifyPrimaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track.artist.toTitleCaseDisplay} · ${track.album.toTitleCaseDisplay}",
                    color = SpotifySecondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isMissingLocally) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SpotifyError.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Missing",
                            color = SpotifyError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = NormalizationUtils.formatDuration(track.durationMs),
            color = SpotifyMuted,
            fontSize = 12.sp
        )

        if (!isMultiSelectMode) {
            IconButton(
                onClick = onLikeToggle,
                modifier = Modifier.testTag("like_button_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (track.isLiked) "Unlike" else "Like",
                    tint = if (track.isLiked) SpotifyLikedRed else SpotifySecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.testTag("options_button_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = SpotifySecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MissingTrackRowItem(
    entry: PlaylistTrackEntity,
    onLocateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onLocateClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF282828)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = "Unplayable song",
                tint = Color(0xFF6A6A6A),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.csvTitle,
                color = Color(0xFF7A7A7A),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${entry.csvArtist} · Unplayable local song",
                color = Color(0xFF555555),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = NormalizationUtils.formatDuration(entry.csvDurationMs),
            color = Color(0xFF555555),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "Locate",
            color = SpotifyGreen.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable { onLocateClick() }
                .padding(4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistListItem(
    playlist: PlaylistEntity,
    songCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isSelected) SpotifyElevated.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode && onSelectToggle != null) {
                        onSelectToggle(!isSelected)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongClick?.invoke()
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("playlist_item_${playlist.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { checked -> onSelectToggle?.invoke(checked) },
                colors = CheckboxDefaults.colors(
                    checkedColor = SpotifyGreen,
                    uncheckedColor = SpotifySecondaryText,
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        CoverArtImage(
            coverPath = playlist.coverPath,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name.toTitleCaseDisplay,
                color = SpotifyPrimaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Playlist · $songCount songs",
                color = SpotifySecondaryText,
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistFolderListItem(
    folder: com.example.data.model.PlaylistFolderEntity,
    playlistCount: Int,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("folder_item_${folder.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF282828)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = SpotifyGreen,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name.toTitleCaseDisplay,
                color = SpotifyPrimaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Folder · $playlistCount playlists",
                color = SpotifySecondaryText,
                fontSize = 13.sp
            )
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.testTag("folder_menu_${folder.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Folder options",
                    tint = SpotifySecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SpotifyCardBackground)
            ) {
                DropdownMenuItem(
                    text = { Text("Rename Folder", color = SpotifyPrimaryText) },
                    onClick = {
                        showMenu = false
                        onRenameClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (folder.isPinned) "Unpin Folder" else "Pin Folder", color = SpotifyPrimaryText) },
                    onClick = {
                        showMenu = false
                        onTogglePin()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = SpotifyError) },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

@Composable
fun MoveToFolderDialog(
    folders: List<com.example.data.model.PlaylistFolderEntity>,
    currentFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onCreateNewFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyElevated,
        title = {
            Text(
                text = "Move to Folder",
                color = SpotifyPrimaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Select a folder or remove from folder (like Spotify):",
                    color = SpotifySecondaryText,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Root / No folder option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectFolder(null)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = if (currentFolderId == null) SpotifyGreen else SpotifySecondaryText,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Main Library (No Folder)",
                        color = if (currentFolderId == null) SpotifyGreen else SpotifyPrimaryText,
                        fontSize = 14.sp,
                        fontWeight = if (currentFolderId == null) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Existing Folders
                for (f in folders) {
                    val isSelected = currentFolderId == f.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectFolder(f.id)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isSelected) SpotifyGreen else SpotifySecondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = f.name,
                            color = if (isSelected) SpotifyGreen else SpotifyPrimaryText,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onDismiss()
                        onCreateNewFolder()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("+ Create New Folder", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifySecondaryText)
            }
        }
    )
}

@Composable
fun CreateFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifyElevated,
        title = {
            Text("New Playlist Folder", color = SpotifyPrimaryText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Create a Spotify-like folder to organize your playlists.", color = SpotifySecondaryText, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = SpotifyPrimaryText,
                        unfocusedTextColor = SpotifyPrimaryText,
                        focusedContainerColor = SpotifyCardBackground,
                        unfocusedContainerColor = SpotifyCardBackground,
                        focusedIndicatorColor = SpotifyGreen,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("folder_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onConfirm(folderName.trim())
                    }
                },
                enabled = folderName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifySecondaryText)
            }
        }
    )
}

@Composable
fun LikedSongsBannerCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("liked_songs_banner"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(SpotifyLikedPurple, Color(0xFF2E175B), SpotifyCardBackground)
                    )
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Liked Songs",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Liked Songs",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$count songs · Offline",
                        color = SpotifySecondaryText,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    track: TrackEntity?,
    isPlaying: Boolean,
    progress: Float,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onLikeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    val targetCoverColor = remember(track.id, track.coverPath) {
        ColorExtractor.getDominantColor(track.coverPath, track.title, track.artist)
    }
    val animatedCoverColor by animateColorAsState(
        targetValue = targetCoverColor,
        animationSpec = tween(durationMillis = 500),
        label = "mini_player_cover_color"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpandClick() }
            .testTag("mini_player"),
        color = SpotifyElevated,
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = animatedCoverColor,
                trackColor = Color.Transparent,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtImage(
                    coverPath = track.coverPath,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = SpotifyPrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = SpotifySecondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onLikeToggle,
                    modifier = Modifier.testTag("mini_player_like")
                ) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (track.isLiked) SpotifyLikedRed else SpotifySecondaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerModal(
    track: TrackEntity?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onLikeToggle: () -> Unit,
    onOpenQueue: () -> Unit
) {
    if (track == null) return

    val targetDominantColor = remember(track.id, track.coverPath) {
        ColorExtractor.getDominantColor(track.coverPath, track.title, track.artist)
    }
    val animatedCoverColor by animateColorAsState(
        targetValue = targetDominantColor,
        animationSpec = tween(durationMillis = 650),
        label = "full_player_cover_color"
    )

    val playerBackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            animatedCoverColor.copy(alpha = 0.85f),
            animatedCoverColor.copy(alpha = 0.40f),
            SpotifyDarkBackground.copy(alpha = 0.95f),
            SpotifyDarkBackground
        )
    )

    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("full_player"),
        color = SpotifyDarkBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(playerBackgroundGradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_full_player")) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PLAYING FROM LOCAL",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = track.album.ifBlank { "Local Library" },
                            color = SpotifyPrimaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onOpenQueue, modifier = Modifier.testTag("queue_button")) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Large Album Artwork
                Box(
                    modifier = Modifier
                        .size(290.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpotifyElevated)
                ) {
                    CoverArtImage(
                        coverPath = track.coverPath,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Song Info & Like
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = SpotifyPrimaryText,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = track.artist,
                            color = SpotifySecondaryText,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onLikeToggle,
                        modifier = Modifier.testTag("full_player_like")
                    ) {
                        Icon(
                            imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (track.isLiked) SpotifyLikedRed else SpotifySecondaryText,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Seek Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progress,
                        onValueChange = { newProg ->
                            val targetMs = (newProg * durationMs).toLong()
                            onSeek(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = animatedCoverColor,
                            inactiveTrackColor = SpotifyElevated
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("player_seek_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = NormalizationUtils.formatDuration(currentPositionMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = NormalizationUtils.formatDuration(durationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onShuffleToggle,
                        modifier = Modifier.testTag("shuffle_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) animatedCoverColor else SpotifySecondaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.testTag("skip_previous")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(animatedCoverColor)
                            .clickable { onPlayPause() }
                            .testTag("full_player_play_pause"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.testTag("skip_next")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = onRepeatToggle,
                        modifier = Modifier.testTag("repeat_toggle")
                    ) {
                        val icon = when (repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Repeat",
                            tint = if (repeatMode != RepeatMode.OFF) animatedCoverColor else SpotifySecondaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueModal(
    queue: List<TrackEntity>,
    currentTrack: TrackEntity?,
    onTrackSelect: (TrackEntity) -> Unit,
    onClose: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = SpotifyDarkSecondary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Playing Queue",
                color = SpotifyPrimaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (queue.isEmpty()) {
                Text(
                    text = "Queue is empty",
                    color = SpotifySecondaryText,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    items(queue) { t ->
                        val isCurrent = t.id == currentTrack?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackSelect(t) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtImage(coverPath = t.coverPath, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = t.title,
                                    color = if (isCurrent) SpotifyGreen else SpotifyPrimaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = t.artist,
                                    color = SpotifySecondaryText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrent) {
                                Text("Playing", color = SpotifyGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsMenu(
    track: TrackEntity?,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onLikeToggle: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDeleteFromLibrary: () -> Unit
) {
    if (track == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SpotifyDarkSecondary
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Header with song info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArtImage(coverPath = track.coverPath, modifier = Modifier.size(52.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = track.title,
                        color = SpotifyPrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = track.artist,
                        color = SpotifySecondaryText,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }

            // Options
            MenuRow(Icons.Default.PlaylistAdd, "Add to playlist", onAddToPlaylist)
            if (onRemoveFromPlaylist != null) {
                MenuRow(Icons.Default.Close, "Remove from this playlist", onRemoveFromPlaylist)
            }
            MenuRow(
                if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (track.isLiked) "Remove from Liked Songs" else "Save to Liked Songs",
                onLikeToggle
            )
            MenuRow(Icons.Default.PlayArrow, "Play next", onPlayNext)
            MenuRow(Icons.Default.QueueMusic, "Add to queue", onAddToQueue)
            MenuRow(Icons.Default.Delete, "Delete from library", onDeleteFromLibrary, isDestructive = true)
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isDestructive) SpotifyError else SpotifySecondaryText,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = if (isDestructive) SpotifyError else SpotifyPrimaryText,
            fontSize = 15.sp
        )
    }
}

@Composable
fun DuplicatePlaylistDialog(
    prompt: DuplicatePrompt,
    onResolve: (DuplicateAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onResolve(DuplicateAction.CANCEL) },
        title = { Text("Playlist already exists", color = SpotifyPrimaryText) },
        text = {
            Text(
                "A playlist named '${prompt.existingPlaylistName}' already exists in your library. How would you like to handle this import?",
                color = SpotifySecondaryText
            )
        },
        confirmButton = {
            Button(
                onClick = { onResolve(DuplicateAction.REPLACE) },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Text("Replace (Default)", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onResolve(DuplicateAction.MERGE) }) {
                    Text("Merge", color = SpotifyPrimaryText)
                }
                TextButton(onClick = { onResolve(DuplicateAction.CANCEL) }) {
                    Text("Cancel", color = SpotifyMuted)
                }
            }
        },
        containerColor = SpotifyCardBackground
    )
}

@Composable
fun ImportSummaryDialog(
    summary: ImportSummary,
    onDismiss: () -> Unit,
    onViewPlaylist: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CSV Import Complete", color = SpotifyPrimaryText) },
        text = {
            Column {
                Text("Playlist: ${summary.playlistName}", color = SpotifyGreen, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Total CSV tracks: ${summary.totalTracks}", color = SpotifyPrimaryText)
                Text("✓ Matched accurately: ${summary.matchedCount}", color = SpotifyGreen)
                if (summary.missingCount > 0) {
                    Text("⚠ Missing local files: ${summary.missingCount}", color = SpotifyWarning)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onViewPlaylist(summary.playlistId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Text("View Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SpotifySecondaryText)
            }
        },
        containerColor = SpotifyCardBackground
    )
}

@Composable
fun RestoreReportDialog(
    report: RestoreReport,
    onDismiss: () -> Unit,
    onViewMissing: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RESTORE COMPLETE", color = SpotifyPrimaryText, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Playlists restored: ${report.playlistsRestored}", color = SpotifyPrimaryText)
                Text("Tracks matched: ${report.tracksMatched}", color = SpotifyGreen)
                Text("Tracks missing: ${report.tracksMissing}", color = if (report.tracksMissing > 0) SpotifyWarning else SpotifySecondaryText)
                Text("Liked songs restored: ${report.likedSongsRestored}", color = SpotifyPrimaryText)
                Text("Playlist covers restored: ${report.playlistCoversRestored}", color = SpotifyPrimaryText)
            }
        },
        confirmButton = {
            if (report.tracksMissing > 0) {
                Button(
                    onClick = {
                        onDismiss()
                        onViewMissing()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text("View Missing Songs", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (report.tracksMissing > 0) {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = SpotifySecondaryText)
                }
            }
        },
        containerColor = SpotifyCardBackground
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist", color = SpotifyPrimaryText) },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists created yet. Create a playlist from your library first.", color = SpotifySecondaryText)
            } else {
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(playlists) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlaylist(p) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtImage(coverPath = p.coverPath, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = p.name, color = SpotifyPrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifySecondaryText)
            }
        },
        containerColor = SpotifyCardBackground
    )
}

@Composable
fun DeleteConfirmationDialog(
    track: TrackEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove from Library?", color = SpotifyPrimaryText) },
        text = {
            Text(
                "Are you sure you want to remove '${track.title}' from your library? (Your local file on device will not be deleted)",
                color = SpotifySecondaryText
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyError)
            ) {
                Text("Remove", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifySecondaryText)
            }
        },
        containerColor = SpotifyCardBackground
    )
}

@Composable
fun BatchSongActionBar(
    selectedCount: Int,
    totalCount: Int,
    isInPlaylist: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onExport: () -> Unit,
    onRemove: () -> Unit, // Removes from playlist or library
    onCheckDuplicates: (() -> Unit)? = null, // Custom callback for duplicates
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("batch_song_action_bar"),
        color = SpotifyElevated,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection", tint = SpotifyPrimaryText)
                    }
                    Text(
                        text = "$selectedCount selected",
                        color = SpotifyPrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onSelectAll) {
                    Text(
                        text = if (selectedCount == totalCount) "Deselect All" else "Select All",
                        color = SpotifyGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAddToQueue, modifier = Modifier.testTag("batch_action_queue")) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.QueueMusic, contentDescription = "Add to Queue", tint = SpotifyPrimaryText, modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.testTag("batch_action_playlist")) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to Playlist", tint = SpotifyPrimaryText, modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = onToggleLike, modifier = Modifier.testTag("batch_action_like")) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Favorite, contentDescription = "Like/Unlike", tint = SpotifyLikedRed, modifier = Modifier.size(22.dp))
                    }
                }
                if (onCheckDuplicates != null) {
                    IconButton(onClick = onCheckDuplicates, modifier = Modifier.testTag("batch_action_duplicates")) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Check Duplicates", tint = SpotifyGreen, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                IconButton(onClick = onExport, modifier = Modifier.testTag("batch_action_export")) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Share, contentDescription = "Export Selection", tint = SpotifyPrimaryText, modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.testTag("batch_action_delete")) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Delete, contentDescription = if (isInPlaylist) "Remove from Playlist" else "Delete", tint = SpotifyError, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BatchPlaylistActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMoveToFolder: () -> Unit,
    onLikeAllSongs: () -> Unit,
    onExportPlaylists: () -> Unit,
    onDeletePlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("batch_playlist_action_bar"),
        color = SpotifyElevated,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection", tint = SpotifyPrimaryText)
                    }
                    Text(
                        text = "$selectedCount playlists selected",
                        color = SpotifyPrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onSelectAll) {
                    Text(
                        text = if (selectedCount == totalCount) "Deselect All" else "Select All",
                        color = SpotifyGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveToFolder,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                        .testTag("batch_move_playlists_folder")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Move to Folder",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onLikeAllSongs,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SpotifyLikedPurple)
                        .testTag("batch_like_playlists_songs")
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Add Songs to Liked",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onExportPlaylists,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SpotifyDarkSecondary)
                        .testTag("batch_export_playlists")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Playlists",
                        tint = SpotifyPrimaryText,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onDeletePlaylists,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SpotifyError)
                        .testTag("batch_delete_playlists")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Playlists",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SongSortDropdownMenu(
    expanded: Boolean,
    currentSort: SongSortOrder,
    onSortSelected: (SongSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(SpotifyCardBackground)
    ) {
        SongSortOrder.values().forEach { order ->
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = order.label,
                            color = if (order == currentSort) SpotifyGreen else SpotifyPrimaryText,
                            fontWeight = if (order == currentSort) FontWeight.Bold else FontWeight.Normal
                        )
                        if (order == currentSort) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                onClick = {
                    onSortSelected(order)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun PlaylistSortDropdownMenu(
    expanded: Boolean,
    currentSort: PlaylistSortOrder,
    onSortSelected: (PlaylistSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(SpotifyCardBackground)
    ) {
        PlaylistSortOrder.values().forEach { order ->
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = order.label,
                            color = if (order == currentSort) SpotifyGreen else SpotifyPrimaryText,
                            fontWeight = if (order == currentSort) FontWeight.Bold else FontWeight.Normal
                        )
                        if (order == currentSort) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                onClick = {
                    onSortSelected(order)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun DuplicateReviewDialog(
    duplicateGroups: List<DuplicateGroup>,
    onRemoveDuplicates: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    // Default to selecting the redundant copies (items except the first in each group)
    var selectedEntryIds by remember(duplicateGroups) {
        mutableStateOf(
            duplicateGroups.flatMap { group -> group.items.drop(1).map { it.entryId } }.toSet()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Duplicate Songs Detected", color = SpotifyPrimaryText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Matched by Artist + Title + Duration (±2s). Choose duplicates to remove from playlist (local files are NOT deleted):",
                    color = SpotifySecondaryText,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (duplicateGroups.isEmpty()) {
                    Text("No duplicate entries found in this playlist!", color = SpotifyGreen)
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(duplicateGroups) { group ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = SpotifyElevated)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${group.artist} - ${group.title}",
                                        color = SpotifyPrimaryText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Duration: ${NormalizationUtils.formatDuration(group.durationMs)} · ${group.items.size} copies",
                                        color = SpotifyMuted,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    group.items.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedEntryIds = if (selectedEntryIds.contains(item.entryId)) {
                                                        selectedEntryIds - item.entryId
                                                    } else {
                                                        selectedEntryIds + item.entryId
                                                    }
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = selectedEntryIds.contains(item.entryId),
                                                onCheckedChange = { checked ->
                                                    selectedEntryIds = if (checked) {
                                                        selectedEntryIds + item.entryId
                                                    } else {
                                                        selectedEntryIds - item.entryId
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = SpotifyError,
                                                    uncheckedColor = SpotifySecondaryText,
                                                    checkmarkColor = Color.White
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = if (index == 0) "Copy #1 (Original keep)" else "Copy #${index + 1} (Duplicate)",
                                                    color = if (index == 0) SpotifyGreen else SpotifySecondaryText,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = "Added: position #${item.orderIndex}",
                                                    color = SpotifyMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (duplicateGroups.isNotEmpty()) {
                Button(
                    onClick = {
                        onRemoveDuplicates(selectedEntryIds.toList())
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyError),
                    enabled = selectedEntryIds.isNotEmpty()
                ) {
                    Text("Remove Selected (${selectedEntryIds.size})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifySecondaryText)
            }
        },
        containerColor = SpotifyCardBackground
    )
}

