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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.SpotifyCardBackground
import com.example.ui.theme.SpotifyDarkBackground
import com.example.ui.theme.SpotifyDarkSecondary
import com.example.ui.theme.SpotifyElevated
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyPrimaryText
import com.example.ui.theme.SpotifySecondaryText
import java.io.File
import java.io.FileOutputStream

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsState()
    val hideUnplayable by viewModel.hideUnplayableSongs.collectAsState()
    val folderPaths by viewModel.musicFolderPaths.collectAsState()
    val persistedUris by viewModel.persistedFolderUris.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val scanProgressCount by viewModel.scanProgressCount.collectAsState()
    val scanTotalCount by viewModel.scanTotalCount.collectAsState()
    val scanProgressPercent by viewModel.scanProgressPercent.collectAsState()
    val lastBackupPath by viewModel.lastBackupPath.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var includeAudioInBackup by remember { mutableStateOf(false) }

    // SAF folder picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.scanDocumentTree(uri)
        }
    }

    // SAF ZIP file picker for Restore
    val restorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val cacheZip = File(context.cacheDir, "restore_temp.zip")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheZip).use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.restoreFullBackup(cacheZip)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyDarkBackground)
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
    ) {
        item {
            Text(
                text = "Settings & Sources",
                color = SpotifyPrimaryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section: Automatic Storage Indexing
        item {
            SettingsCategoryHeader("MANUAL STORAGE IMPORT")
            Card(
                colors = CardDefaults.cardColors(containerColor = SpotifyElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Imported Music Library",
                        color = SpotifyPrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Imports music from your local folder directories. No background scanning is active. Click the button below to manually scan and import files.",
                        color = SpotifySecondaryText,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.scanAllMusicSources() },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        modifier = Modifier.fillMaxWidth().testTag("scan_all_sources_btn")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scanning Storage...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Storage Directories", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpotifyCardBackground, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isScanning) {
                                    if (scanTotalCount > 0) {
                                        "Importing... $scanProgressCount / $scanTotalCount songs (${(scanProgressPercent * 100).toInt()}%)"
                                    } else {
                                        "Importing... $scanProgressCount songs found so far"
                                    }
                                } else {
                                    "Imported: ${allTracks.size} total songs in library"
                                },
                                color = SpotifyGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (isScanning) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { scanProgressPercent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = SpotifyGreen,
                                    trackColor = SpotifyDarkSecondary
                                )
                            }

                            if (!statusMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = statusMessage.orEmpty(),
                                    color = SpotifySecondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Section: Choose Folders & Scan
        item {
            SettingsCategoryHeader("CHOOSE FOLDERS & MANUAL SCAN")
            Card(
                colors = CardDefaults.cardColors(containerColor = SpotifyElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Music Directory Sources",
                        color = SpotifyPrimaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add specific folder paths or select folders to scan for local audio. Scanning is manual—folders are not automatically scanned until you click 'Scan'.",
                        color = SpotifySecondaryText,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Manual Path Input
                    var manualPathText by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualPathText,
                            onValueChange = { manualPathText = it },
                            placeholder = { Text("e.g. /storage/emulated/0/Music", color = SpotifySecondaryText, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SpotifyCardBackground,
                                unfocusedContainerColor = SpotifyCardBackground,
                                focusedTextColor = SpotifyPrimaryText,
                                unfocusedTextColor = SpotifyPrimaryText,
                                focusedIndicatorColor = SpotifyGreen,
                                unfocusedIndicatorColor = SpotifyCardBackground
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (manualPathText.isNotBlank()) {
                                    viewModel.addMusicFolderPath(manualPathText)
                                    manualPathText = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(SpotifyGreen, RoundedCornerShape(8.dp))
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Path", tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. SAF Folder Picker Button
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyPrimaryText)
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = SpotifyGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Custom Folder via Picker", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // List of Folder Paths
                    if (folderPaths.isNotEmpty() || persistedUris.isNotEmpty()) {
                        Text(
                            text = "Configured Folders:",
                            color = SpotifyPrimaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Render Manual Folder Paths
                        for (path in folderPaths) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(SpotifyCardBackground, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = path, color = SpotifyPrimaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = "Manual Local Path", color = SpotifySecondaryText, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { viewModel.scanSingleFolderPath(path) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Scan", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { viewModel.removeMusicFolderPath(path) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                                    }
                                }
                            }
                        }

                        // Render SAF Persisted URIs
                        for (uriStr in persistedUris) {
                            val decodedName = remember(uriStr) {
                                try {
                                    val uri = Uri.parse(uriStr)
                                    uri.lastPathSegment ?: uri.path ?: "Selected Folder"
                                } catch (_: Exception) {
                                    "Selected Folder"
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(SpotifyCardBackground, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = decodedName, color = SpotifyPrimaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = "External SAF Picker", color = SpotifySecondaryText, fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { viewModel.scanSingleDocumentTree(uriStr) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Scan", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { viewModel.removePersistedUri(uriStr) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No custom folder sources added yet.",
                            color = SpotifySecondaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Section: Spotify-style Local File Preferences
        item {
            SettingsCategoryHeader("SPOTIFY LOCAL MUSIC PREFERENCES")
            Card(
                colors = CardDefaults.cardColors(containerColor = SpotifyElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide unplayable songs",
                                color = SpotifyPrimaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Hide playlist songs not found in your local music sources (like Spotify)",
                                color = SpotifySecondaryText,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = hideUnplayable,
                            onCheckedChange = { viewModel.setHideUnplayableSongs(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SpotifyGreen,
                                uncheckedThumbColor = SpotifySecondaryText,
                                uncheckedTrackColor = SpotifyCardBackground
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Section: Backup & Restore
        item {
            SettingsCategoryHeader("BACKUP & RESTORE")
            Card(
                colors = CardDefaults.cardColors(containerColor = SpotifyElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeAudioInBackup,
                            onCheckedChange = { includeAudioInBackup = it },
                            colors = CheckboxDefaults.colors(checkedColor = SpotifyGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Include physical music files",
                                color = SpotifyPrimaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "OFF by default (keeps backup compact & fast)",
                                color = SpotifySecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.createFullBackup(includeAudioInBackup) },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        modifier = Modifier.fillMaxWidth().testTag("create_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Backup All Data (ZIP)",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            restorePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("restore_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore",
                            tint = SpotifyPrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restore All Data (from ZIP)",
                            color = SpotifyPrimaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Display saved path if available
                    if (!lastBackupPath.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpotifyCardBackground, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Backup Saved File Path:",
                                    color = SpotifyGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = lastBackupPath.orEmpty(),
                                    color = SpotifyPrimaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Existing local backups list if any
                    val backupsDir = File(context.filesDir, "backups")
                    val publicDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "LocalMusicBackups")
                    val internalFiles = backupsDir.listFiles()?.filter { it.extension.lowercase() == "zip" } ?: emptyList()
                    val publicFiles = if (publicDir.exists()) publicDir.listFiles()?.filter { it.extension.lowercase() == "zip" } ?: emptyList() else emptyList()
                    val existingBackups = (internalFiles + publicFiles).distinctBy { it.name }

                    if (existingBackups.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Saved Backups Folder Location:",
                            color = SpotifySecondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (publicDir.exists()) publicDir.absolutePath else backupsDir.absolutePath,
                            color = SpotifyGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        for (backup in existingBackups.sortedByDescending { it.lastModified() }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SpotifyCardBackground, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.restoreFullBackup(backup) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = backup.name,
                                        color = SpotifyPrimaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${backup.absolutePath} (${backup.length() / 1024} KB)",
                                        color = SpotifySecondaryText,
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore", color = SpotifyGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Section: About & Privacy
        item {
            SettingsCategoryHeader("ABOUT")
            Card(
                colors = CardDefaults.cardColors(containerColor = SpotifyElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Local Music Player",
                        color = SpotifyPrimaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "100% Offline · Local Folder Sources Only · Accurate Title/Artist/Duration Matching · Hide Unplayable Songs · Spotify CSV & ZIP Backup/Restore",
                        color = SpotifySecondaryText,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun SettingsCategoryHeader(text: String) {
    Text(
        text = text,
        color = SpotifySecondaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = SpotifyGreen,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SpotifyPrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = SpotifySecondaryText,
                fontSize = 12.sp
            )
        }
    }
}

