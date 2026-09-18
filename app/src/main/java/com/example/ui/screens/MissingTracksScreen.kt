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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlaylistTrackEntity
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.components.CoverArtImage
import com.example.ui.theme.SpotifyCardBackground
import com.example.ui.theme.SpotifyDarkBackground
import com.example.ui.theme.SpotifyElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyMuted
import com.example.ui.theme.SpotifyPrimaryText
import com.example.ui.theme.SpotifySecondaryText
import com.example.ui.theme.SpotifyWarning
import com.example.util.NormalizationUtils

@Composable
fun MissingTracksScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val missingTracks by viewModel.allMissingTracks.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    var locatingEntry by remember { mutableStateOf<PlaylistTrackEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyDarkBackground)
            .testTag("missing_tracks_screen"),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Back & Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(Screen.Library) },
                    modifier = Modifier.testTag("missing_tracks_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = SpotifyPrimaryText
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Missing Songs Report",
                    color = SpotifyPrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SpotifyElevated),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = SpotifyWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${missingTracks.size} songs could not be matched automatically",
                            color = SpotifyPrimaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "You can manually match each missing song with a local file in your library, or rescan after moving files.",
                        color = SpotifySecondaryText,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (missingTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓ All songs in your playlists are matched with local files!",
                        color = SpotifyGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            items(missingTracks) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = SpotifyCardBackground),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SpotifyElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚠", fontSize = 20.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.csvTitle,
                                color = SpotifyPrimaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Artist: ${entry.csvArtist}",
                                color = SpotifySecondaryText,
                                fontSize = 12.sp
                            )
                            if (entry.csvAlbum.isNotBlank()) {
                                Text(
                                    text = "Expected Album: ${entry.csvAlbum}",
                                    color = SpotifyMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "Duration: ${NormalizationUtils.formatDuration(entry.csvDurationMs)}",
                                color = SpotifyMuted,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { locatingEntry = entry },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Locate", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Manual Locate Track Dialog
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
}
