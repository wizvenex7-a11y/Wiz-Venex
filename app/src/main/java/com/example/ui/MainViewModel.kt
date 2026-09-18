package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupRestoreManager
import com.example.backup.RestoreReport
import com.example.data.db.AppDatabase
import com.example.data.model.DuplicateGroup
import com.example.data.model.DuplicateItem
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackEntity
import com.example.data.model.TrackEntity
import com.example.data.repository.DuplicateAction
import com.example.data.repository.ImportSummary
import com.example.data.repository.MusicRepository
import com.example.demo.DemoMusicManager
import com.example.player.AudioPlayerManager
import com.example.player.RepeatMode
import com.example.scanner.MusicScanner
import com.example.util.ImportedManifestManager
import com.example.util.NormalizationUtils
import com.example.util.PlaylistSortOrder
import com.example.util.SongSortOrder
import com.example.util.TxtPlaylistUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

data class DuplicatePrompt(
    val rawFileName: String,
    val inputStream: InputStream,
    val existingPlaylistName: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getInstance(context)
    val repository = MusicRepository(context, database)
    val playerManager = AudioPlayerManager.getInstance(context)

    // SharedPreferences for folder sources and Spotify-like playback preferences
    private val prefs = context.getSharedPreferences("music_sources_prefs", Context.MODE_PRIVATE)

    // Hide unplayable songs (like Spotify local files)
    private val _hideUnplayableSongs = MutableStateFlow(prefs.getBoolean("hide_unplayable_songs", false))
    val hideUnplayableSongs: StateFlow<Boolean> = _hideUnplayableSongs.asStateFlow()

    // Added local folder paths
    private val defaultFolderPaths = setOf(
        "/storage/emulated/0/Music",
        "/storage/emulated/0/Download",
        "/sdcard/Music"
    )
    private val _musicFolderPaths = MutableStateFlow(
        prefs.getStringSet("folder_paths", defaultFolderPaths)?.toList() ?: defaultFolderPaths.toList()
    )
    val musicFolderPaths: StateFlow<List<String>> = _musicFolderPaths.asStateFlow()

    // Persisted SAF document tree URIs
    private val _persistedFolderUris = MutableStateFlow(
        prefs.getStringSet("persisted_uris", emptySet())?.toList() ?: emptyList()
    )
    val persistedFolderUris: StateFlow<List<String>> = _persistedFolderUris.asStateFlow()

    // UI state flows from repository
    val allTracks: StateFlow<List<TrackEntity>> = repository.allTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val likedTracks: StateFlow<List<TrackEntity>> = repository.likedTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentlyPlayed: StateFlow<List<TrackEntity>> = repository.recentlyPlayed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allPlaylists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val rootPlaylists: StateFlow<List<PlaylistEntity>> = repository.rootPlaylists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allFolders: StateFlow<List<com.example.data.model.PlaylistFolderEntity>> = repository.allFolders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allMissingTracks: StateFlow<List<PlaylistTrackEntity>> = repository.allMissingTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allPlaylistTracks: StateFlow<List<PlaylistTrackEntity>> = database.playlistDao().getAllPlaylistTracks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playlistSongCounts: StateFlow<Map<String, Int>> = allPlaylistTracks.map { entries ->
        entries.groupBy { it.playlistId }.mapValues { it.value.size }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Active screen navigation state
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _activePlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val activePlaylist: StateFlow<PlaylistEntity?> = _activePlaylist.asStateFlow()

    // Player states
    val currentTrack = playerManager.currentTrack
    val isPlaying = playerManager.isPlaying
    val currentPositionMs = playerManager.currentPositionMs
    val durationMs = playerManager.durationMs
    val isShuffle = playerManager.isShuffle
    val repeatMode = playerManager.repeatMode
    val queue = playerManager.queue

    private val _isFullPlayerExpanded = MutableStateFlow(false)
    val isFullPlayerExpanded: StateFlow<Boolean> = _isFullPlayerExpanded.asStateFlow()

    private val _isQueueVisible = MutableStateFlow(false)
    val isQueueVisible: StateFlow<Boolean> = _isQueueVisible.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTracks: StateFlow<List<TrackEntity>> = combine(allTracks, searchQuery) { tracks, query ->
        if (query.isBlank()) tracks
        else {
            val q = NormalizationUtils.normalizeText(query)
            tracks.filter {
                NormalizationUtils.normalizeText(it.title).contains(q) ||
                        NormalizationUtils.normalizeText(it.artist).contains(q) ||
                        NormalizationUtils.normalizeText(it.album).contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dialog and Sheet States
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _duplicatePrompt = MutableStateFlow<DuplicatePrompt?>(null)
    val duplicatePrompt: StateFlow<DuplicatePrompt?> = _duplicatePrompt.asStateFlow()

    private val _lastImportSummary = MutableStateFlow<ImportSummary?>(null)
    val lastImportSummary: StateFlow<ImportSummary?> = _lastImportSummary.asStateFlow()

    private val _restoreReport = MutableStateFlow<RestoreReport?>(null)
    val restoreReport: StateFlow<RestoreReport?> = _restoreReport.asStateFlow()

    private val _lastBackupPath = MutableStateFlow<String?>(null)
    val lastBackupPath: StateFlow<String?> = _lastBackupPath.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgressCount = MutableStateFlow(0)
    val scanProgressCount: StateFlow<Int> = _scanProgressCount.asStateFlow()

    private val _scanTotalCount = MutableStateFlow(0)
    val scanTotalCount: StateFlow<Int> = _scanTotalCount.asStateFlow()

    private val _scanProgressPercent = MutableStateFlow(0f)
    val scanProgressPercent: StateFlow<Float> = _scanProgressPercent.asStateFlow()

    private val _selectedTrackForOptions = MutableStateFlow<TrackEntity?>(null)
    val selectedTrackForOptions: StateFlow<TrackEntity?> = _selectedTrackForOptions.asStateFlow()

    private val _selectedTrackForAddToPlaylist = MutableStateFlow<TrackEntity?>(null)
    val selectedTrackForAddToPlaylist: StateFlow<TrackEntity?> = _selectedTrackForAddToPlaylist.asStateFlow()

    private val _isDeleteConfirmationVisible = MutableStateFlow(false)
    val isDeleteConfirmationVisible: StateFlow<Boolean> = _isDeleteConfirmationVisible.asStateFlow()

    // Multi-Select for Songs
    private val _selectedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongIds: StateFlow<Set<String>> = _selectedSongIds.asStateFlow()

    private val _isSongMultiSelectActive = MutableStateFlow(false)
    val isSongMultiSelectActive: StateFlow<Boolean> = _isSongMultiSelectActive.asStateFlow()

    // Multi-Select for Playlists
    private val _selectedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlaylistIds: StateFlow<Set<String>> = _selectedPlaylistIds.asStateFlow()

    private val _isPlaylistMultiSelectActive = MutableStateFlow(false)
    val isPlaylistMultiSelectActive: StateFlow<Boolean> = _isPlaylistMultiSelectActive.asStateFlow()

    // Sorting State
    private val _songSortOrder = MutableStateFlow(SongSortOrder.CUSTOM)
    val songSortOrder: StateFlow<SongSortOrder> = _songSortOrder.asStateFlow()

    private val _playlistSortOrder = MutableStateFlow(PlaylistSortOrder.RECENTLY_UPDATED)
    val playlistSortOrder: StateFlow<PlaylistSortOrder> = _playlistSortOrder.asStateFlow()

    // Check Local Files State
    private val _missingLocalTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val missingLocalTrackIds: StateFlow<Set<String>> = _missingLocalTrackIds.asStateFlow()
    val missingFileTrackIds: StateFlow<Set<String>> = _missingLocalTrackIds.asStateFlow()

    // Active duplicate groups for review modal
    private val _playlistDuplicates = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val playlistDuplicates: StateFlow<List<DuplicateGroup>> = _playlistDuplicates.asStateFlow()
    val activePlaylistDuplicates: StateFlow<List<DuplicateGroup>> = _playlistDuplicates.asStateFlow()

    val isMultiSelectMode: StateFlow<Boolean> = combine(_isSongMultiSelectActive, _isPlaylistMultiSelectActive) { song, pl ->
        song || pl
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Startup: clean any previously injected synthetic demo tracks so only real local music is used!
        viewModelScope.launch(Dispatchers.IO) {
            val all = database.trackDao().getAllTracksSnapshot()
            val demo = all.filter { it.id.startsWith("demo_") || it.filePath.contains("demo_music") }
            for (d in demo) {
                database.trackDao().deleteTrackById(d.id)
            }
        }
    }

    fun setHideUnplayableSongs(hide: Boolean) {
        _hideUnplayableSongs.value = hide
        prefs.edit().putBoolean("hide_unplayable_songs", hide).apply()
    }

    fun toggleHideUnplayableSongs() {
        setHideUnplayableSongs(!_hideUnplayableSongs.value)
    }

    fun addMusicFolderPath(path: String) {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return
        val current = _musicFolderPaths.value.toMutableList()
        if (!current.contains(trimmed)) {
            current.add(trimmed)
            _musicFolderPaths.value = current
            prefs.edit().putStringSet("folder_paths", current.toSet()).apply()
            _statusMessage.value = "Added music source: $trimmed. Click Scan to import."
        }
    }

    fun removeMusicFolderPath(path: String) {
        val current = _musicFolderPaths.value.toMutableList()
        if (current.remove(path)) {
            _musicFolderPaths.value = current
            prefs.edit().putStringSet("folder_paths", current.toSet()).apply()
            _statusMessage.value = "Removed folder path"
        }
    }

    fun removePersistedUri(uriString: String) {
        val current = _persistedFolderUris.value.toMutableList()
        if (current.remove(uriString)) {
            _persistedFolderUris.value = current
            prefs.edit().putStringSet("persisted_uris", current.toSet()).apply()
            _statusMessage.value = "Removed folder source"
        }
    }

    fun scanAllMusicSources() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanProgressCount.value = 0
            _scanTotalCount.value = 0
            _scanProgressPercent.value = 0f
            _statusMessage.value = "Scanning internal storage..."

            try {
                val existingTracks = database.trackDao().getAllTracksSnapshot()
                val existingPaths = existingTracks.map { it.filePath.lowercase() }.toMutableSet()
                val existingKeys = existingTracks.map { NormalizationUtils.songKey(it.artist, it.title) }.toMutableSet()

                val scannedCandidates = mutableListOf<TrackEntity>()
                val seenPaths = mutableSetOf<String>()

                fun addCandidates(tracks: List<TrackEntity>) {
                    for (t in tracks) {
                        // Strictly verify path is on internal shared storage (exclude external SD cards)
                        if (MusicScanner.isInternalSharedStoragePath(t.filePath) && seenPaths.add(t.filePath)) {
                            scannedCandidates.add(t)
                        }
                    }
                    _scanTotalCount.value = scannedCandidates.size
                }

                // 1. Scan MediaStore FIRST
                try {
                    val msTracks = MusicScanner.scanMediaStore(context)
                    addCandidates(msTracks)
                } catch (_: Exception) {}

                // 2. Scan standard device internal storage directories (Music, Download, Podcasts)
                val standardDirs = listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                    File("/storage/emulated/0/Music"),
                    File("/storage/emulated/0/Download"),
                    File("/storage/emulated/0/Audio"),
                    File("/sdcard/Music")
                ).filter { it.exists() && it.isDirectory }

                val standardTracks = MusicScanner.scanMultipleDirectories(context, standardDirs)
                addCandidates(standardTracks)

                // 3. Scan added folder paths
                val dirsToScan = _musicFolderPaths.value.map { File(it) }
                val dirTracks = MusicScanner.scanMultipleDirectories(context, dirsToScan)
                addCandidates(dirTracks)

                // 4. Scan SAF trees
                for (uriStr in _persistedFolderUris.value) {
                    try {
                        val treeTracks = MusicScanner.scanDocumentTree(context, Uri.parse(uriStr))
                        addCandidates(treeTracks)
                    } catch (_: Exception) {}
                }

                val totalDiscovered = scannedCandidates.distinctBy { it.id }
                _scanTotalCount.value = totalDiscovered.size

                val discoveredPathsSet = totalDiscovered.map { it.filePath.lowercase() }.toSet()

                // Remove tracks from All Songs database that no longer exist on internal storage
                val missingTrackIds = mutableListOf<String>()
                for (existing in existingTracks) {
                    val pathLower = existing.filePath.lowercase()
                    if (pathLower.startsWith("/") && !File(existing.filePath).exists() && !discoveredPathsSet.contains(pathLower)) {
                        missingTrackIds.add(existing.id)
                        existingPaths.remove(pathLower)
                        existingKeys.remove(NormalizationUtils.songKey(existing.artist, existing.title))
                    }
                }

                if (missingTrackIds.isNotEmpty()) {
                    database.trackDao().deleteTracksByIds(missingTrackIds)
                    database.playlistDao().markTracksAsMissing(missingTrackIds)
                }

                if (totalDiscovered.isNotEmpty()) {
                    val pendingChunk = mutableListOf<TrackEntity>()
                    var importedNewCount = 0
                    var skippedDuplicateCount = 0

                    for ((index, track) in totalDiscovered.withIndex()) {
                        val pathLower = track.filePath.lowercase()
                        val key = NormalizationUtils.songKey(track.artist, track.title)

                        if (existingPaths.contains(pathLower)) {
                            skippedDuplicateCount++
                        } else {
                            existingPaths.add(pathLower)
                            pendingChunk.add(track)
                            importedNewCount++
                        }

                        // Live insert every 50 songs (or last song) so tracks pop up on All Songs screen live while scanning!
                        if (pendingChunk.size >= 50 || index == totalDiscovered.lastIndex) {
                            if (pendingChunk.isNotEmpty()) {
                                database.trackDao().insertTracks(pendingChunk)
                                pendingChunk.clear()
                            }
                        }

                        val processed = index + 1
                        _scanProgressCount.value = processed
                        _scanProgressPercent.value = (processed.toFloat() / totalDiscovered.size.toFloat()).coerceIn(0f, 1f)
                    }

                    // Re-link missing tracks in all playlists
                    val playlists = database.playlistDao().getAllPlaylistsSnapshot()
                    for (p in playlists) {
                        repository.retryMissingMatches(p.id)
                    }

                    // Save JSON manifest remembering all imported paths, titles, artists, and metadata
                    val updatedLibrary = database.trackDao().getAllTracksSnapshot()
                    ImportedManifestManager.saveManifest(context, updatedLibrary)

                    val removedMsg = if (missingTrackIds.isNotEmpty()) " (Removed ${missingTrackIds.size} missing files)" else ""
                    if (importedNewCount > 0) {
                        _statusMessage.value = "Imported $importedNewCount NEW songs!$removedMsg (Skipped $skippedDuplicateCount duplicates)"
                    } else {
                        _statusMessage.value = "Library up to date.$removedMsg (0 duplicates imported)"
                    }
                } else {
                    val existing = database.trackDao().getAllTracksSnapshot()
                    if (existing.isEmpty()) {
                        val demoTracks = DemoMusicManager.generateDemoLibrary(context)
                        database.trackDao().insertTracks(demoTracks)
                        _scanProgressCount.value = demoTracks.size
                        _scanTotalCount.value = demoTracks.size
                        _scanProgressPercent.value = 1f
                        _statusMessage.value = "Loaded ${demoTracks.size} sample songs"
                        ImportedManifestManager.saveManifest(context, demoTracks)
                    } else {
                        _statusMessage.value = "No audio files found on internal storage"
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Scan error: ${e.message}"
            } finally {
                _scanProgressPercent.value = 1f
                _isScanning.value = false
            }
        }
    }

    fun batchLikeSongsOfSelectedPlaylists() {
        val selected = _selectedPlaylistIds.value.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var likedCount = 0
                for (playlistId in selected) {
                    val entries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
                    val trackIds = entries.mapNotNull { it.trackId }.filter { it.isNotBlank() }
                    if (trackIds.isNotEmpty()) {
                        val tracks = database.trackDao().getTracksByIds(trackIds)
                        for (t in tracks) {
                            if (!t.isLiked) {
                                database.trackDao().setLiked(t.id, true)
                                likedCount++
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Added $likedCount songs from selected playlists to Liked Songs!"
                    clearSelection()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error liking playlist songs: ${e.message}"
                }
            }
        }
    }

    fun exportSelectedPlaylistsAsZip(context: Context) {
        val selected = _selectedPlaylistIds.value.toList()
        if (selected.isEmpty()) {
            _statusMessage.value = "No playlists selected to export"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure the public downloads backup directory exists
                val backupDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LocalMusicBackups")
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }
                // Fallback direct path creation
                val directBackupDir = File("/storage/emulated/0/Download/LocalMusicBackups")
                if (!directBackupDir.exists()) {
                    directBackupDir.mkdirs()
                }

                val finalDir = if (backupDir.exists()) backupDir else directBackupDir

                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val zipFile = File(finalDir, "Playlists_Backup_$timestamp.zip")

                val allTracksMap = allTracks.value.associateBy { it.id }

                java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zipOut ->
                    for (playlistId in selected) {
                        val playlist = database.playlistDao().getPlaylistById(playlistId) ?: continue
                        val entries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
                        
                        // Sanitize filename to avoid any invalid chars
                        val safeName = playlist.name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                        val entryName = "$safeName.txt"

                        val txtContent = buildString {
                            for (entry in entries) {
                                val track = entry.trackId?.let { allTracksMap[it] }
                                val artist = track?.artist ?: entry.csvArtist
                                val title = track?.title ?: entry.csvTitle
                                val duration = track?.durationMs ?: entry.csvDurationMs
                                appendLine("$artist:$title:$duration")
                            }
                        }

                        val zipEntry = java.util.zip.ZipEntry(entryName)
                        zipOut.putNextEntry(zipEntry)
                        zipOut.write(txtContent.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                    }
                }

                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Saved backup ZIP to ${zipFile.absolutePath}"
                    clearSelection()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "ZIP export failed: ${e.message}"
                }
            }
        }
    }

    fun clearDemoLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = database.trackDao().getAllTracksSnapshot()
            val demo = all.filter { it.id.startsWith("demo_") || it.filePath.contains("demo_music") || it.filePath.contains("sample_data") }
            for (d in demo) {
                database.trackDao().deleteTrackById(d.id)
            }
            _statusMessage.value = "Removed ${demo.size} demo tracks from library"
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun openPlaylist(playlist: PlaylistEntity) {
        _activePlaylist.value = playlist
        _currentScreen.value = Screen.PlaylistDetail(playlist.id)
    }

    fun openLikedSongs() {
        _currentScreen.value = Screen.LikedSongs
    }

    fun openMissingTracksScreen() {
        _currentScreen.value = Screen.MissingTracks
    }

    fun expandFullPlayer() {
        _isFullPlayerExpanded.value = true
    }

    fun collapseFullPlayer() {
        _isFullPlayerExpanded.value = false
    }

    fun setQueueVisible(visible: Boolean) {
        _isQueueVisible.value = visible
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearImportSummary() {
        _lastImportSummary.value = null
    }

    fun clearRestoreReport() {
        _restoreReport.value = null
    }

    // Audio Controls
    fun playTrack(track: TrackEntity, newQueue: List<TrackEntity>? = null) {
        playerManager.playTrack(track, newQueue)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun skipNext() {
        playerManager.skipNext()
    }

    fun skipPrevious() {
        playerManager.skipPrevious()
    }

    fun toggleShuffle() {
        playerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        playerManager.toggleRepeat()
    }

    fun toggleLike(track: TrackEntity) {
        val newLiked = !track.isLiked
        viewModelScope.launch {
            repository.setLiked(track.id, newLiked)
            if (currentTrack.value?.id == track.id) {
                playerManager.toggleLikeCurrentTrack()
            }
        }
    }

    fun removeBatchFromLiked(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            for (id in trackIds) {
                repository.setLiked(id, false)
            }
            clearSongSelection()
            _statusMessage.value = "Removed ${trackIds.size} songs from Liked Songs"
        }
    }

    fun addToQueue(track: TrackEntity) {
        playerManager.addToQueue(track)
        _statusMessage.value = "Added to queue: ${track.title}"
    }

    fun addBatchToQueue(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        playerManager.addToQueueBatch(tracks)
        clearSongSelection()
        _statusMessage.value = "Added ${tracks.size} songs to queue"
    }

    fun playNext(track: TrackEntity) {
        playerManager.playNext(track)
        _statusMessage.value = "Playing next: ${track.title}"
    }

    fun playNextBatch(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        playerManager.playNextBatch(tracks)
        clearSongSelection()
        _statusMessage.value = "Playing next: ${tracks.size} songs"
    }

    fun createPlaylist(name: String, folderId: String? = null) {
        viewModelScope.launch {
            val p = repository.createPlaylist(name, folderId)
            _statusMessage.value = "Created playlist: ${p.name}"
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _statusMessage.value = "Deleted playlist"
            if (_activePlaylist.value?.id == playlistId) {
                _activePlaylist.value = null
                _currentScreen.value = Screen.Library
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val f = repository.createFolder(name)
            _statusMessage.value = "Created folder: ${f.name}"
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            repository.renameFolder(folderId, newName)
            _statusMessage.value = "Renamed folder to $newName"
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            _statusMessage.value = "Deleted folder (playlists moved to main library)"
        }
    }

    fun setFolderPinned(folderId: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.setFolderPinned(folderId, isPinned)
        }
    }

    fun movePlaylistToFolder(playlistId: String, folderId: String?) {
        viewModelScope.launch {
            repository.movePlaylistToFolder(playlistId, folderId)
            _statusMessage.value = if (folderId != null) "Moved playlist to folder" else "Moved to main library"
        }
    }

    fun setPlaylistPinned(playlistId: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.setPlaylistPinned(playlistId, isPinned)
        }
    }

    fun batchMovePlaylistsToFolder(folderId: String?) {
        val selected = _selectedPlaylistIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            for (pId in selected) {
                repository.movePlaylistToFolder(pId, folderId)
            }
            clearSelection()
            _statusMessage.value = "Moved ${selected.size} playlists"
        }
    }

    fun importPlaylistsFromFolderTree(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val docDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                if (docDir == null || !docDir.isDirectory) {
                    _statusMessage.value = "Invalid folder selected"
                    return@launch
                }

                val filesToImport = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                fun traverse(dir: androidx.documentfile.provider.DocumentFile) {
                    for (file in dir.listFiles()) {
                        if (file.isDirectory) {
                            if (!file.name.orEmpty().startsWith(".")) {
                                traverse(file)
                            }
                        } else if (file.isFile) {
                            val name = file.name.orEmpty().lowercase()
                            if (name.endsWith(".txt") || name.endsWith(".csv")) {
                                filesToImport.add(file)
                            }
                        }
                    }
                }
                traverse(docDir)

                if (filesToImport.isEmpty()) {
                    _statusMessage.value = "No .txt or .csv playlist files found in selected folder"
                    return@launch
                }

                var successCount = 0
                for (doc in filesToImport) {
                    val fileName = doc.name ?: "Playlist"
                    try {
                        context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                            if (fileName.endsWith(".txt", ignoreCase = true)) {
                                repository.importTxtPlaylist(fileName, stream, DuplicateAction.REPLACE)
                            } else {
                                repository.importCsvPlaylist(fileName, stream, DuplicateAction.REPLACE)
                            }
                            successCount++
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                _statusMessage.value = "Imported $successCount playlists from folder"
            } catch (e: Exception) {
                _statusMessage.value = "Folder import failed: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun setSelectedTrackForOptions(track: TrackEntity?) {
        _selectedTrackForOptions.value = track
    }

    fun setSelectedTrackForAddToPlaylist(track: TrackEntity?) {
        _selectedTrackForAddToPlaylist.value = track
    }

    fun showDeleteConfirmation(show: Boolean) {
        _isDeleteConfirmationVisible.value = show
    }

    fun confirmDeleteTrackFromLibrary() {
        val track = _selectedTrackForOptions.value ?: return
        viewModelScope.launch {
            repository.deleteTrackFromLibrary(track.id)
            _selectedTrackForOptions.value = null
            _isDeleteConfirmationVisible.value = false
            _statusMessage.value = "Removed from library: ${track.title}"
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: TrackEntity) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, track)
            _selectedTrackForAddToPlaylist.value = null
            _statusMessage.value = "Added to playlist!"
        }
    }

    fun removeEntryFromPlaylist(entryId: Long) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(entryId)
            _statusMessage.value = "Removed from playlist"
        }
    }

    fun ignoreMissingTracks(playlistId: String) {
        viewModelScope.launch {
            repository.ignoreMissingTracks(playlistId)
            _statusMessage.value = "Ignored missing tracks"
        }
    }

    fun manualLocateTrack(entryId: Long, chosenTrack: TrackEntity) {
        viewModelScope.launch {
            repository.manualLocateTrack(entryId, chosenTrack)
            _statusMessage.value = "Matched '${chosenTrack.title}'!"
        }
    }

    fun retryMissingMatches(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recovered = repository.retryMissingMatches(playlistId)
                
                // Also automatically run check & clean duplicates for the library
                val tracks = database.trackDao().getAllTracksSnapshot()
                val grouped = tracks.groupBy { it.filePath.lowercase() }
                var dupsRemoved = 0
                val idsToRemove = mutableListOf<String>()
                for ((_, trackList) in grouped) {
                    if (trackList.size > 1) {
                        dupsRemoved += (trackList.size - 1)
                        idsToRemove.addAll(trackList.drop(1).map { it.id })
                    }
                }
                if (idsToRemove.isNotEmpty()) {
                    database.trackDao().deleteTracksByIds(idsToRemove)
                    database.playlistDao().markTracksAsMissing(idsToRemove)
                    val updatedLibrary = database.trackDao().getAllTracksSnapshot()
                    ImportedManifestManager.saveManifest(context, updatedLibrary)
                }

                withContext(Dispatchers.Main) {
                    _statusMessage.value = if (recovered > 0) {
                        if (dupsRemoved > 0) "Recovered $recovered track(s) & cleaned $dupsRemoved library duplicates!" else "Recovered $recovered track(s)!"
                    } else {
                        if (dupsRemoved > 0) "Cleaned $dupsRemoved duplicate song entries!" else "No additional matches found"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error retrying matches: ${e.message}"
                }
            }
        }
    }

    // Sorting Operations
    fun setSongSortOrder(order: com.example.util.SongSortOrder) {
        _songSortOrder.value = order
    }

    fun setPlaylistSortOrder(order: com.example.util.PlaylistSortOrder) {
        _playlistSortOrder.value = order
    }

    // Song Multi-Select Operations
    fun toggleSongSelection(trackId: String) {
        val current = _selectedSongIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _selectedSongIds.value = current
        _isSongMultiSelectActive.value = current.isNotEmpty()
    }

    fun selectAllSongs(trackIds: List<String>) {
        _selectedSongIds.value = trackIds.toSet()
        _isSongMultiSelectActive.value = trackIds.isNotEmpty()
    }

    fun clearSongSelection() {
        _selectedSongIds.value = emptySet()
        _isSongMultiSelectActive.value = false
    }

    fun setSongMultiSelectActive(active: Boolean) {
        _isSongMultiSelectActive.value = active
        if (!active) {
            _selectedSongIds.value = emptySet()
        }
    }

    fun batchLikeSelectedSongs(isLiked: Boolean) {
        val ids = _selectedSongIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (id in ids) {
                repository.setLiked(id, isLiked)
            }
            clearSongSelection()
            _statusMessage.value = if (isLiked) "Added ${ids.size} songs to Liked" else "Removed ${ids.size} songs from Liked"
        }
    }

    fun batchAddSelectedSongsToQueue() {
        val ids = _selectedSongIds.value
        if (ids.isEmpty()) return
        val all = allTracks.value.associateBy { it.id }
        var added = 0
        for (id in ids) {
            val track = all[id]
            if (track != null) {
                playerManager.addToQueue(track)
                added++
            }
        }
        clearSongSelection()
        _statusMessage.value = "Added $added songs to queue"
    }

    fun batchAddSelectedSongsToPlaylist(playlistId: String) {
        val ids = _selectedSongIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchAddTracksToPlaylist(playlistId, ids)
            clearSongSelection()
            _statusMessage.value = "Added ${ids.size} songs to playlist"
        }
    }

    fun batchDeleteSelectedSongsFromLibrary() {
        val ids = _selectedSongIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (id in ids) {
                repository.deleteTrackFromLibrary(id)
            }
            clearSongSelection()
            _statusMessage.value = "Removed ${ids.size} songs from library"
        }
    }

    fun batchCheckAndCleanDuplicates(targetIds: List<String> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val idsToCheck = targetIds.ifEmpty { _selectedSongIds.value.toList() }
                val tracksToAnalyze = if (idsToCheck.isNotEmpty()) {
                    database.trackDao().getTracksByIds(idsToCheck)
                } else {
                    database.trackDao().getAllTracksSnapshot()
                }

                if (tracksToAnalyze.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "No songs to check for duplicates"
                    }
                    return@launch
                }

                // Group strictly by full path and format (lowercase file path) to avoid skipping tracks with same artist/title but different paths or formats
                val grouped = tracksToAnalyze.groupBy {
                    it.filePath.lowercase()
                }

                val duplicateTracksToRemove = mutableListOf<TrackEntity>()
                var duplicatePairsCount = 0

                for ((_, trackList) in grouped) {
                    if (trackList.size > 1) {
                        // Keep the first one, mark the rest for removal
                        duplicatePairsCount += (trackList.size - 1)
                        duplicateTracksToRemove.addAll(trackList.drop(1))
                    }
                }

                if (duplicateTracksToRemove.isNotEmpty()) {
                    val idsToRemove = duplicateTracksToRemove.map { it.id }
                    database.trackDao().deleteTracksByIds(idsToRemove)
                    database.playlistDao().markTracksAsMissing(idsToRemove)
                    
                    // Update manifest
                    val updatedLibrary = database.trackDao().getAllTracksSnapshot()
                    ImportedManifestManager.saveManifest(context, updatedLibrary)
                    
                    withContext(Dispatchers.Main) {
                        clearSongSelection()
                        _statusMessage.value = "Found and removed $duplicatePairsCount duplicate song entries from library!"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        clearSongSelection()
                        _statusMessage.value = "Perfect! No duplicate songs found."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error checking duplicates: ${e.message}"
                }
            }
        }
    }

    fun batchRemoveSelectedEntriesFromPlaylist(playlistId: String) {
        val ids = _selectedSongIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val entries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
            val entryIdsToRemove = entries.filter { it.trackId != null && ids.contains(it.trackId) }.map { it.entryId }
            for (id in entryIdsToRemove) {
                repository.removeTrackFromPlaylist(id)
            }
            clearSongSelection()
            _statusMessage.value = "Removed ${entryIdsToRemove.size} songs from playlist"
        }
    }

    fun batchRemoveSelectedEntriesFromPlaylist(playlistId: String, entryIds: List<Long>) {
        if (entryIds.isEmpty()) return
        viewModelScope.launch {
            for (id in entryIds) {
                repository.removeTrackFromPlaylist(id)
            }
            clearSongSelection()
            _statusMessage.value = "Removed ${entryIds.size} songs from playlist"
        }
    }

    fun exportSelectedSongsAsTxt(trackIds: Set<String>): String {
        val all = allTracks.value.associateBy { it.id }
        val tracksToExport = trackIds.mapNotNull { all[it] }
        return TxtPlaylistUtils.formatTracksAsTxt(tracksToExport)
    }

    fun setMultiSelectMode(active: Boolean) {
        setSongMultiSelectActive(active)
        setPlaylistMultiSelectActive(active)
    }

    fun clearSelection() {
        clearSongSelection()
        clearPlaylistSelection()
    }

    fun openSettings() {
        _currentScreen.value = Screen.Settings
    }

    fun exportPlaylistTxt(context: Context, playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = database.playlistDao().getPlaylistById(playlistId) ?: return@launch
            val entries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
            val all = allTracks.value.associateBy { it.id }
            val txtContent = buildString {
                for (entry in entries) {
                    val track = entry.trackId?.let { all[it] }
                    val artist = track?.artist ?: entry.csvArtist
                    val title = track?.title ?: entry.csvTitle
                    val duration = track?.durationMs ?: entry.csvDurationMs
                    appendLine("$artist:$title:$duration")
                }
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Playlist Export: ${playlist.name}")
                putExtra(Intent.EXTRA_TEXT, txtContent)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Playlist TXT").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            _statusMessage.value = "Exported playlist '${playlist.name}' as TXT"
        }
    }

    fun exportSelectedSongsTxt(context: Context) {
        val ids = _selectedSongIds.value
        if (ids.isEmpty()) return
        val all = allTracks.value.associateBy { it.id }
        val tracks = ids.mapNotNull { all[it] }
        val txtContent = TxtPlaylistUtils.formatTracksAsTxt(tracks)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Exported Songs")
            putExtra(Intent.EXTRA_TEXT, txtContent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Selected Songs TXT").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        _statusMessage.value = "Exported ${tracks.size} songs as TXT"
    }

    // Playlist Multi-Select Operations
    fun togglePlaylistSelection(playlistId: String) {
        val current = _selectedPlaylistIds.value.toMutableSet()
        if (current.contains(playlistId)) {
            current.remove(playlistId)
        } else {
            current.add(playlistId)
        }
        _selectedPlaylistIds.value = current
        _isPlaylistMultiSelectActive.value = current.isNotEmpty()
    }

    fun selectAllPlaylists(playlistIds: List<String>) {
        _selectedPlaylistIds.value = playlistIds.toSet()
        _isPlaylistMultiSelectActive.value = playlistIds.isNotEmpty()
    }

    fun clearPlaylistSelection() {
        _selectedPlaylistIds.value = emptySet()
        _isPlaylistMultiSelectActive.value = false
    }

    fun setPlaylistMultiSelectActive(active: Boolean) {
        _isPlaylistMultiSelectActive.value = active
        if (!active) {
            _selectedPlaylistIds.value = emptySet()
        }
    }

    fun batchDeleteSelectedPlaylists() {
        val ids = _selectedPlaylistIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (id in ids) {
                repository.deletePlaylist(id)
            }
            clearPlaylistSelection()
            _statusMessage.value = "Deleted ${ids.size} playlists"
        }
    }

    // Check Local Files
    fun checkLocalFilesExistence() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val missing = repository.checkLocalFilesExistence()
                _missingLocalTrackIds.value = missing
                if (missing.isEmpty()) {
                    _statusMessage.value = "All songs exist on device storage! (0 missing)"
                } else {
                    _statusMessage.value = "Local file check: ${missing.size} songs not found on storage"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Check error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    // Duplicate Detection & Removal
    fun findDuplicatesInActivePlaylist(playlistId: String) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val dups = repository.findDuplicatesInPlaylist(playlistId)
                _playlistDuplicates.value = dups
                if (dups.isEmpty()) {
                    _statusMessage.value = "No duplicate songs found in playlist!"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Duplicates check error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearPlaylistDuplicates() {
        _playlistDuplicates.value = emptyList()
    }

    fun removeDuplicatesFromPlaylist(playlistId: String, entryIdsToRemove: List<Long>) {
        if (entryIdsToRemove.isEmpty()) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.removeDuplicateEntriesFromPlaylist(playlistId, entryIdsToRemove)
                _playlistDuplicates.value = emptyList()
                _statusMessage.value = "Removed ${entryIdsToRemove.size} duplicate entries from playlist"
            } catch (e: Exception) {
                _statusMessage.value = "Error removing duplicates: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    // General Playlist File Import (supports .csv, .txt, etc.)
    fun importPlaylistFile(fileName: String, inputStream: InputStream) {
        if (fileName.endsWith(".txt", ignoreCase = true)) {
            importTxt(fileName, inputStream)
        } else {
            importCsv(fileName, inputStream)
        }
    }

    // TXT Playlist Import (<artist>:<title>:<milliseconds>)
    fun importTxt(fileName: String, inputStream: InputStream) {
        viewModelScope.launch {
            val sanitized = NormalizationUtils.sanitizePlaylistName(fileName)
            val existing = repository.getPlaylistByName(sanitized)
            if (existing != null) {
                _duplicatePrompt.value = DuplicatePrompt(fileName, inputStream, sanitized)
            } else {
                executeTxtImport(fileName, inputStream, DuplicateAction.REPLACE)
            }
        }
    }

    private suspend fun executeTxtImport(
        fileName: String,
        inputStream: InputStream,
        action: DuplicateAction
    ) {
        _isScanning.value = true
        try {
            val summary = repository.importTxtPlaylist(fileName, inputStream, action)
            _lastImportSummary.value = summary
            _statusMessage.value = "Restored TXT playlist '${summary.playlistName}': ${summary.matchedCount}/${summary.totalTracks} matched"
        } catch (e: Exception) {
            e.printStackTrace()
            _statusMessage.value = "Error importing TXT: ${e.message}"
        } finally {
            _isScanning.value = false
        }
    }

    // CSV Import
    fun importCsv(fileName: String, inputStream: InputStream) {
        viewModelScope.launch {
            val sanitized = NormalizationUtils.sanitizePlaylistName(fileName)
            val existing = repository.getPlaylistByName(sanitized)
            if (existing != null) {
                _duplicatePrompt.value = DuplicatePrompt(fileName, inputStream, sanitized)
            } else {
                executeCsvImport(fileName, inputStream, DuplicateAction.REPLACE)
            }
        }
    }

    fun resolveDuplicate(action: DuplicateAction) {
        val prompt = _duplicatePrompt.value ?: return
        _duplicatePrompt.value = null
        if (action == DuplicateAction.CANCEL) return
        viewModelScope.launch {
            if (prompt.rawFileName.endsWith(".txt", ignoreCase = true)) {
                executeTxtImport(prompt.rawFileName, prompt.inputStream, action)
            } else {
                executeCsvImport(prompt.rawFileName, prompt.inputStream, action)
            }
        }
    }

    private suspend fun executeCsvImport(
        fileName: String,
        inputStream: InputStream,
        action: DuplicateAction
    ) {
        _isScanning.value = true
        try {
            val summary = repository.importCsvPlaylist(fileName, inputStream, action)
            _lastImportSummary.value = summary
            _statusMessage.value = "Imported '${summary.playlistName}': ${summary.matchedCount} matched"
        } catch (e: Exception) {
            e.printStackTrace()
            _statusMessage.value = "Error importing CSV: ${e.message}"
        } finally {
            _isScanning.value = false
        }
    }

    // Import sample files bundled in DemoMusicManager
    fun importSampleCsv(csvFile: File) {
        try {
            importCsv(csvFile.name, FileInputStream(csvFile))
        } catch (e: Exception) {
            _statusMessage.value = "Error opening sample CSV: ${e.message}"
        }
    }

    // Scanning
    fun scanLocalFolder(folder: File) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val scanned = MusicScanner.scanDirectory(context, folder)
                database.trackDao().insertTracks(scanned)
                _statusMessage.value = "Scanned ${scanned.size} tracks from ${folder.name}"
            } catch (e: Exception) {
                _statusMessage.value = "Scan error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanDocumentTree(uri: Uri) {
        viewModelScope.launch {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                val uriStr = uri.toString()
                val current = _persistedFolderUris.value.toMutableList()
                if (!current.contains(uriStr)) {
                    current.add(uriStr)
                    _persistedFolderUris.value = current
                    prefs.edit().putStringSet("persisted_uris", current.toSet()).apply()
                }
                _statusMessage.value = "Selected folder added! Click Scan next to it to import."
            } catch (e: Exception) {
                _statusMessage.value = "Failed to add folder: ${e.message}"
            }
        }
    }

    fun scanSingleDocumentTree(uriStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanProgressCount.value = 0
            _scanProgressPercent.value = 0f
            try {
                val uri = Uri.parse(uriStr)
                val scanned = MusicScanner.scanDocumentTree(context, uri)
                if (scanned.isNotEmpty()) {
                    database.trackDao().insertTracks(scanned)
                    val playlists = database.playlistDao().getAllPlaylistsSnapshot()
                    for (p in playlists) {
                        repository.retryMissingMatches(p.id)
                    }
                }
                _statusMessage.value = "Imported ${scanned.size} tracks from folder!"
            } catch (e: Exception) {
                _statusMessage.value = "Scan error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanSingleFolderPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _scanProgressCount.value = 0
            _scanProgressPercent.value = 0f
            try {
                val scanned = MusicScanner.scanMultipleDirectories(context, listOf(File(path)))
                if (scanned.isNotEmpty()) {
                    database.trackDao().insertTracks(scanned)
                    val playlists = database.playlistDao().getAllPlaylistsSnapshot()
                    for (p in playlists) {
                        repository.retryMissingMatches(p.id)
                    }
                }
                _statusMessage.value = "Imported ${scanned.size} tracks from folder path!"
            } catch (e: Exception) {
                _statusMessage.value = "Scan error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanDeviceMediaStore() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val scanned = MusicScanner.scanMediaStore(context)
                database.trackDao().insertTracks(scanned)
                _statusMessage.value = "Found ${scanned.size} device audio tracks"
            } catch (e: Exception) {
                _statusMessage.value = "MediaStore scan: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun reloadDemoLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val demoTracks = DemoMusicManager.generateDemoLibrary(context)
                database.trackDao().insertTracks(demoTracks)
                _statusMessage.value = "Loaded ${demoTracks.size} sample songs and playlists"
            } catch (e: Exception) {
                _statusMessage.value = "Error loading demo library: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun retryMissingMatchesForAllPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isScanning.value = true
            }
            try {
                val playlists = database.playlistDao().getAllPlaylistsSnapshot()
                var totalRecovered = 0
                for (p in playlists) {
                    val count = repository.retryMissingMatches(p.id)
                    totalRecovered += count
                }

                // Also automatically run check & clean duplicates for the library
                val tracks = database.trackDao().getAllTracksSnapshot()
                val grouped = tracks.groupBy { it.filePath.lowercase() }
                var dupsRemoved = 0
                val idsToRemove = mutableListOf<String>()
                for ((_, trackList) in grouped) {
                    if (trackList.size > 1) {
                        dupsRemoved += (trackList.size - 1)
                        idsToRemove.addAll(trackList.drop(1).map { it.id })
                    }
                }
                if (idsToRemove.isNotEmpty()) {
                    database.trackDao().deleteTracksByIds(idsToRemove)
                    database.playlistDao().markTracksAsMissing(idsToRemove)
                    val updatedLibrary = database.trackDao().getAllTracksSnapshot()
                    ImportedManifestManager.saveManifest(context, updatedLibrary)
                }

                val remainingMissing = database.playlistDao().getAllMissingEntriesSnapshot().size
                withContext(Dispatchers.Main) {
                    if (totalRecovered > 0) {
                        _statusMessage.value = if (dupsRemoved > 0) {
                            "Matched $totalRecovered missing songs & cleaned $dupsRemoved library duplicates! ($remainingMissing missing remaining)"
                        } else {
                            "Matched $totalRecovered missing songs! ($remainingMissing missing remaining)"
                        }
                    } else {
                        _statusMessage.value = if (dupsRemoved > 0) {
                            "Cleaned $dupsRemoved library duplicates! ($remainingMissing missing songs remaining)"
                        } else {
                            "No new matches found ($remainingMissing missing songs remaining)"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Retry error: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isScanning.value = false
                }
            }
        }
    }

    // Backup & Restore
    fun createFullBackup(includeAudioFiles: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isScanning.value = true
            }
            try {
                val file = BackupRestoreManager.createFullBackup(context, database, includeAudioFiles)
                val displayPath = file.absolutePath
                withContext(Dispatchers.Main) {
                    _lastBackupPath.value = displayPath
                    _statusMessage.value = "Full Backup ZIP saved successfully!\nSaved at: $displayPath"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Backup failed: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isScanning.value = false
                }
            }
        }
    }

    fun restoreFullBackup(backupFile: File) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val result = BackupRestoreManager.restoreAllData(context, database, backupFile)
                result.onSuccess { report ->
                    _restoreReport.value = report
                    _statusMessage.value = "Restore completed! ${report.playlistsRestored} playlists restored."
                }.onFailure { err ->
                    _statusMessage.value = "Restore failed: ${err.message}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Restore error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}

sealed class Screen {
    object Home : Screen()
    object Search : Screen()
    object Library : Screen()
    object Settings : Screen()
    object LikedSongs : Screen()
    object MissingTracks : Screen()
    data class PlaylistDetail(val playlistId: String) : Screen()
}
