package com.example.data.repository

import android.content.Context
import com.example.cover.PlaylistCoverGenerator
import com.example.data.db.AppDatabase
import com.example.data.model.DuplicateGroup
import com.example.data.model.DuplicateItem
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackEntity
import com.example.data.model.TrackEntity
import com.example.matcher.FastTrackMatcher
import com.example.matcher.MatchResult
import com.example.matcher.SongMatcher
import com.example.util.CsvTrackRow
import com.example.util.CsvUtils
import com.example.util.NormalizationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

enum class DuplicateAction {
    REPLACE,
    MERGE,
    CANCEL
}

data class ImportSummary(
    val playlistId: String,
    val playlistName: String,
    val totalTracks: Int,
    val matchedCount: Int,
    val missingCount: Int,
    val ambiguousCount: Int
)

class MusicRepository(private val context: Context, private val database: AppDatabase) {

    val allTracks: Flow<List<TrackEntity>> = database.trackDao().getAllTracks()
    val likedTracks: Flow<List<TrackEntity>> = database.trackDao().getLikedTracks()
    val recentlyPlayed: Flow<List<TrackEntity>> = database.trackDao().getRecentlyPlayed()
    val allPlaylists: Flow<List<PlaylistEntity>> = database.playlistDao().getAllPlaylists()
    val rootPlaylists: Flow<List<PlaylistEntity>> = database.playlistDao().getRootPlaylists()
    val allFolders: Flow<List<com.example.data.model.PlaylistFolderEntity>> = database.playlistFolderDao().getAllFolders()
    val allMissingTracks: Flow<List<PlaylistTrackEntity>> = database.playlistDao().getAllMissingEntries()

    fun getPlaylistsInFolder(folderId: String): Flow<List<PlaylistEntity>> =
        database.playlistDao().getPlaylistsInFolder(folderId)

    suspend fun getPlaylistById(id: String): PlaylistEntity? = database.playlistDao().getPlaylistById(id)

    suspend fun getPlaylistByName(name: String): PlaylistEntity? = database.playlistDao().getPlaylistByName(name)

    suspend fun createFolder(name: String): com.example.data.model.PlaylistFolderEntity = withContext(Dispatchers.IO) {
        val folder = com.example.data.model.PlaylistFolderEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim()
        )
        database.playlistFolderDao().insertFolder(folder)
        folder
    }

    suspend fun renameFolder(folderId: String, newName: String) = withContext(Dispatchers.IO) {
        val folder = database.playlistFolderDao().getFolderById(folderId) ?: return@withContext
        database.playlistFolderDao().updateFolder(folder.copy(name = newName.trim()))
    }

    suspend fun setFolderPinned(folderId: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        database.playlistFolderDao().setFolderPinned(folderId, isPinned)
    }

    suspend fun deleteFolder(folderId: String) = withContext(Dispatchers.IO) {
        database.playlistFolderDao().detachPlaylistsFromFolder(folderId)
        database.playlistFolderDao().deleteFolderById(folderId)
    }

    suspend fun movePlaylistToFolder(playlistId: String, folderId: String?) = withContext(Dispatchers.IO) {
        database.playlistDao().setPlaylistFolder(playlistId, folderId)
    }

    suspend fun setPlaylistPinned(playlistId: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        database.playlistDao().setPlaylistPinned(playlistId, isPinned)
    }

    fun getPlaylistEntries(playlistId: String): Flow<List<PlaylistTrackEntity>> =
        database.playlistDao().getPlaylistEntries(playlistId)

    suspend fun searchTracks(query: String): Flow<List<TrackEntity>> =
        database.trackDao().searchTracks(query)

    suspend fun setLiked(trackId: String, isLiked: Boolean) = withContext(Dispatchers.IO) {
        database.trackDao().setLiked(trackId, isLiked)
    }

    suspend fun deleteTrackFromLibrary(trackId: String) = withContext(Dispatchers.IO) {
        database.trackDao().deleteTrackById(trackId)
    }

    suspend fun createPlaylist(name: String, folderId: String? = null, description: String = ""): PlaylistEntity = withContext(Dispatchers.IO) {
        val playlist = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            folderId = folderId
        )
        database.playlistDao().insertPlaylist(playlist)
        playlist
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        database.playlistDao().deletePlaylistById(playlistId)
        database.playlistDao().deletePlaylistEntries(playlistId)
    }

    suspend fun removeTrackFromPlaylist(entryId: Long) = withContext(Dispatchers.IO) {
        // Only removes the entry from playlist; NEVER touches local file
        database.playlistDao().deletePlaylistEntryById(entryId)
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: TrackEntity) = withContext(Dispatchers.IO) {
        val existingEntries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
        val newOrder = existingEntries.size
        val entry = PlaylistTrackEntity(
            playlistId = playlistId,
            trackId = track.id,
            orderIndex = newOrder,
            isMissing = false,
            csvTitle = track.title,
            csvArtist = track.artist,
            csvAlbum = track.album,
            csvDurationMs = track.durationMs,
            resolvedFilePath = track.filePath
        )
        database.playlistDao().insertPlaylistEntries(listOf(entry))

        // Regenerate cover
        updatePlaylistCover(playlistId)
    }

    suspend fun ignoreMissingTracks(playlistId: String) = withContext(Dispatchers.IO) {
        database.playlistDao().ignoreMissingTracksInPlaylist(playlistId)
    }

    suspend fun manualLocateTrack(entryId: Long, chosenTrack: TrackEntity) = withContext(Dispatchers.IO) {
        val entries = database.playlistDao().getAllMissingEntriesSnapshot()
        val target = entries.firstOrNull { it.entryId == entryId } ?: return@withContext
        val updated = target.copy(
            trackId = chosenTrack.id,
            isMissing = false,
            resolvedFilePath = chosenTrack.filePath
        )
        database.playlistDao().updatePlaylistEntry(updated)
        updatePlaylistCover(target.playlistId)
    }

    /**
     * Imports a CSV stream and resolves matches with user-selected duplicate strategy
     */
    suspend fun importCsvPlaylist(
        rawFileName: String,
        inputStream: InputStream,
        duplicateAction: DuplicateAction = DuplicateAction.REPLACE
    ): ImportSummary = withContext(Dispatchers.IO) {
        val cleanPlaylistName = NormalizationUtils.sanitizePlaylistName(rawFileName)
        val csvRows = CsvUtils.parseSpotifyCsv(inputStream)
        val localTracks = database.trackDao().getAllTracksSnapshot()
        val matcher = FastTrackMatcher(localTracks)

        val existingPlaylist = database.playlistDao().getPlaylistByName(cleanPlaylistName)

        val playlistId = if (existingPlaylist != null) {
            when (duplicateAction) {
                DuplicateAction.CANCEL -> return@withContext ImportSummary(
                    existingPlaylist.id,
                    cleanPlaylistName,
                    0, 0, 0, 0
                )
                DuplicateAction.REPLACE -> {
                    database.playlistDao().deletePlaylistEntries(existingPlaylist.id)
                    existingPlaylist.id
                }
                DuplicateAction.MERGE -> existingPlaylist.id
            }
        } else {
            val newId = UUID.randomUUID().toString()
            val newPlaylist = PlaylistEntity(
                id = newId,
                name = cleanPlaylistName,
                description = "Imported from $rawFileName",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.playlistDao().insertPlaylist(newPlaylist)
            newId
        }

        var matchedCount = 0
        var missingCount = 0
        var ambiguousCount = 0

        val currentMaxOrder = if (duplicateAction == DuplicateAction.MERGE && existingPlaylist != null) {
            database.playlistDao().getPlaylistEntriesSnapshot(existingPlaylist.id).size
        } else 0

        val newEntries = mutableListOf<PlaylistTrackEntity>()
        val matchedTracksForCover = mutableListOf<TrackEntity>()

        for ((idx, csvRow) in csvRows.withIndex()) {
            val match = matcher.matchCsvTrack(csvRow)
            val orderIndex = currentMaxOrder + idx

            when (match) {
                is MatchResult.SingleMatch -> {
                    newEntries.add(
                        PlaylistTrackEntity(
                            playlistId = playlistId,
                            trackId = match.track.id,
                            orderIndex = orderIndex,
                            isMissing = false,
                            csvTitle = csvRow.trackName,
                            csvArtist = csvRow.artistNames,
                            csvAlbum = csvRow.albumName,
                            csvDurationMs = csvRow.durationMs,
                            csvTrackUri = csvRow.trackUri,
                            resolvedFilePath = match.track.filePath
                        )
                    )
                    matchedTracksForCover.add(match.track)
                    matchedCount++
                }
                is MatchResult.AmbiguousMatch -> {
                    // Ambiguous match: take the closest candidate but flag for user review if needed
                    val bestCandidate = match.candidates.first()
                    newEntries.add(
                        PlaylistTrackEntity(
                            playlistId = playlistId,
                            trackId = bestCandidate.id,
                            orderIndex = orderIndex,
                            isMissing = false,
                            csvTitle = csvRow.trackName,
                            csvArtist = csvRow.artistNames,
                            csvAlbum = csvRow.albumName,
                            csvDurationMs = csvRow.durationMs,
                            csvTrackUri = csvRow.trackUri,
                            resolvedFilePath = bestCandidate.filePath
                        )
                    )
                    matchedTracksForCover.add(bestCandidate)
                    matchedCount++
                    ambiguousCount++
                }
                MatchResult.NoMatch -> {
                    newEntries.add(
                        PlaylistTrackEntity(
                            playlistId = playlistId,
                            trackId = null,
                            orderIndex = orderIndex,
                            isMissing = true,
                            csvTitle = csvRow.trackName,
                            csvArtist = csvRow.artistNames,
                            csvAlbum = csvRow.albumName,
                            csvDurationMs = csvRow.durationMs,
                            csvTrackUri = csvRow.trackUri,
                            resolvedFilePath = null
                        )
                    )
                    missingCount++
                }
            }
        }

        database.playlistDao().insertPlaylistEntries(newEntries)

        // Generate 2x2 cover from distinct albums
        val coverPath = PlaylistCoverGenerator.generateCoverForPlaylist(
            context = context,
            playlistId = playlistId,
            tracks = matchedTracksForCover,
            forceRegenerate = true
        )

        val targetPlaylist = database.playlistDao().getPlaylistById(playlistId)
        if (targetPlaylist != null && coverPath != null) {
            database.playlistDao().updatePlaylist(
                targetPlaylist.copy(
                    coverPath = coverPath,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        ImportSummary(
            playlistId = playlistId,
            playlistName = cleanPlaylistName,
            totalTracks = csvRows.size,
            matchedCount = matchedCount,
            missingCount = missingCount,
            ambiguousCount = ambiguousCount
        )
    }

    /**
     * Imports a TXT stream formatted with lines of <artist>:<title>:<milliseconds>
     * Keeps the exact order of TXT lines.
     */
    suspend fun importTxtPlaylist(
        rawFileName: String,
        inputStream: InputStream,
        duplicateAction: DuplicateAction = DuplicateAction.REPLACE
    ): ImportSummary = withContext(Dispatchers.IO) {
        val cleanPlaylistName = NormalizationUtils.sanitizePlaylistName(rawFileName)
        val txtRows = com.example.util.TxtPlaylistUtils.parseTxtPlaylist(inputStream)
        val localTracks = database.trackDao().getAllTracksSnapshot()
        val matcher = FastTrackMatcher(localTracks)

        val existingPlaylist = database.playlistDao().getPlaylistByName(cleanPlaylistName)

        val playlistId = if (existingPlaylist != null) {
            when (duplicateAction) {
                DuplicateAction.CANCEL -> return@withContext ImportSummary(
                    existingPlaylist.id,
                    cleanPlaylistName,
                    0, 0, 0, 0
                )
                DuplicateAction.REPLACE -> {
                    database.playlistDao().deletePlaylistEntries(existingPlaylist.id)
                    existingPlaylist.id
                }
                DuplicateAction.MERGE -> existingPlaylist.id
            }
        } else {
            val newId = UUID.randomUUID().toString()
            val newPlaylist = PlaylistEntity(
                id = newId,
                name = cleanPlaylistName,
                description = "Imported from $rawFileName",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.playlistDao().insertPlaylist(newPlaylist)
            newId
        }

        var matchedCount = 0
        var missingCount = 0

        val currentMaxOrder = if (duplicateAction == DuplicateAction.MERGE && existingPlaylist != null) {
            database.playlistDao().getPlaylistEntriesSnapshot(existingPlaylist.id).size
        } else 0

        val newEntries = mutableListOf<PlaylistTrackEntity>()
        val matchedTracksForCover = mutableListOf<TrackEntity>()

        for ((idx, txtRow) in txtRows.withIndex()) {
            val match = matcher.matchTxtTrack(txtRow, toleranceMs = 3000L)
            val orderIndex = currentMaxOrder + idx

            when (match) {
                is MatchResult.SingleMatch -> {
                    newEntries.add(
                        PlaylistTrackEntity(
                            playlistId = playlistId,
                            trackId = match.track.id,
                            orderIndex = orderIndex,
                            isMissing = false,
                            csvTitle = txtRow.title,
                            csvArtist = txtRow.artist,
                            csvAlbum = match.track.album,
                            csvDurationMs = txtRow.durationMs,
                            csvTrackUri = null,
                            resolvedFilePath = match.track.filePath
                        )
                    )
                    matchedTracksForCover.add(match.track)
                    matchedCount++
                }
                is MatchResult.AmbiguousMatch -> {
                    val best = match.candidates.first()
                    newEntries.add(
                        PlaylistTrackEntity(
                            playlistId = playlistId,
                            trackId = best.id,
                            orderIndex = orderIndex,
                            isMissing = false,
                            csvTitle = txtRow.title,
                            csvArtist = txtRow.artist,
                            csvAlbum = best.album,
                            csvDurationMs = txtRow.durationMs,
                            csvTrackUri = null,
                            resolvedFilePath = best.filePath
                        )
                    )
                    matchedTracksForCover.add(best)
                    matchedCount++
                }
                MatchResult.NoMatch -> {
                    newEntries.add(
                        PlaylistTrackEntity(
                            playlistId = playlistId,
                            trackId = null,
                            orderIndex = orderIndex,
                            isMissing = true,
                            csvTitle = txtRow.title,
                            csvArtist = txtRow.artist,
                            csvAlbum = "",
                            csvDurationMs = txtRow.durationMs,
                            csvTrackUri = null,
                            resolvedFilePath = null
                        )
                    )
                    missingCount++
                }
            }
        }

        database.playlistDao().insertPlaylistEntries(newEntries)

        val coverPath = PlaylistCoverGenerator.generateCoverForPlaylist(
            context = context,
            playlistId = playlistId,
            tracks = matchedTracksForCover,
            forceRegenerate = true
        )

        val targetPlaylist = database.playlistDao().getPlaylistById(playlistId)
        if (targetPlaylist != null && coverPath != null) {
            database.playlistDao().updatePlaylist(
                targetPlaylist.copy(
                    coverPath = coverPath,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        ImportSummary(
            playlistId = playlistId,
            playlistName = cleanPlaylistName,
            totalTracks = txtRows.size,
            matchedCount = matchedCount,
            missingCount = missingCount,
            ambiguousCount = 0
        )
    }

    /**
     * Detects duplicate songs in a playlist using Artist + Title + Duration (ms)
     * Groups multiple occurrences together and returns the groups.
     */
    suspend fun findDuplicatesInPlaylist(playlistId: String): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val entries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
        val allTracks = database.trackDao().getAllTracksSnapshot().associateBy { it.id }

        val items = entries.map { entry ->
            val track = entry.trackId?.let { allTracks[it] }
            val title = track?.title ?: entry.csvTitle
            val artist = track?.artist ?: entry.csvArtist
            val duration = track?.durationMs ?: entry.csvDurationMs
            DuplicateItem(
                entryId = entry.entryId,
                trackId = entry.trackId,
                title = title,
                artist = artist,
                durationMs = duration,
                orderIndex = entry.orderIndex
            )
        }

        // Group by normalized (Artist + Title + Duration bucket)
        val groups = items.groupBy { item ->
            val normArt = NormalizationUtils.pythonNormalize(item.artist)
            val normTitle = NormalizationUtils.pythonNormalize(item.title)
            val durBucket = item.durationMs / 2000L // 2s bucket
            "$normArt|$normTitle|$durBucket"
        }

        groups.filter { it.value.size > 1 }.map { entry ->
            val first = entry.value.first()
            DuplicateGroup(
                groupKey = entry.key,
                title = first.title,
                artist = first.artist,
                durationMs = first.durationMs,
                items = entry.value
            )
        }
    }

    /**
     * Removes chosen duplicate playlist entries without deleting local audio files.
     */
    suspend fun removeDuplicateEntriesFromPlaylist(playlistId: String, entryIdsToRemove: List<Long>) = withContext(Dispatchers.IO) {
        for (id in entryIdsToRemove) {
            database.playlistDao().deletePlaylistEntryById(id)
        }
        updatePlaylistCover(playlistId)
    }

    /**
     * Checks local files existence on storage for all tracks.
     * Returns a set of track IDs that are missing from storage. Never deletes them automatically.
     */
    suspend fun checkLocalFilesExistence(): Set<String> = withContext(Dispatchers.IO) {
        val tracks = database.trackDao().getAllTracksSnapshot()
        val missingIds = mutableSetOf<String>()
        for (track in tracks) {
            if (track.id.startsWith("demo_")) continue // Built-in demo tracks
            val file = File(track.filePath)
            if (!file.exists()) {
                missingIds.add(track.id)
            }
        }
        missingIds
    }

    /**
     * Batch adds tracks to a playlist
     */
    suspend fun batchAddTracksToPlaylist(playlistId: String, trackIds: List<String>) = withContext(Dispatchers.IO) {
        val existingEntries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
        val startOrder = existingEntries.size
        val allTracks = database.trackDao().getAllTracksSnapshot().associateBy { it.id }

        val newEntries = trackIds.mapIndexedNotNull { index, id ->
            val track = allTracks[id] ?: return@mapIndexedNotNull null
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                orderIndex = startOrder + index,
                isMissing = false,
                csvTitle = track.title,
                csvArtist = track.artist,
                csvAlbum = track.album,
                csvDurationMs = track.durationMs,
                resolvedFilePath = track.filePath
            )
        }

        database.playlistDao().insertPlaylistEntries(newEntries)
        updatePlaylistCover(playlistId)
    }

    suspend fun retryMissingMatches(playlistId: String): Int = withContext(Dispatchers.IO) {
        val entries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
        val missingEntries = entries.filter { it.isMissing }
        if (missingEntries.isEmpty()) return@withContext 0

        val localTracks = database.trackDao().getAllTracksSnapshot()
        val matcher = FastTrackMatcher(localTracks)
        var recovered = 0

        for (entry in missingEntries) {
            val dummyRow = CsvTrackRow(
                trackName = entry.csvTitle,
                artistNames = entry.csvArtist,
                albumName = entry.csvAlbum,
                durationMs = entry.csvDurationMs,
                trackUri = entry.csvTrackUri ?: ""
            )
            val match = matcher.matchCsvTrack(dummyRow)
            if (match is MatchResult.SingleMatch) {
                val updated = entry.copy(
                    trackId = match.track.id,
                    isMissing = false,
                    resolvedFilePath = match.track.filePath
                )
                database.playlistDao().updatePlaylistEntry(updated)
                recovered++
            }
        }

        if (recovered > 0) {
            updatePlaylistCover(playlistId)
        }
        recovered
    }

    private suspend fun updatePlaylistCover(playlistId: String) {
        val playlist = database.playlistDao().getPlaylistById(playlistId) ?: return
        val entries = database.playlistDao().getPlaylistEntriesSnapshot(playlistId)
        val trackIds = entries.mapNotNull { it.trackId }
        val allLocal = database.trackDao().getAllTracksSnapshot().associateBy { it.id }
        val tracks = trackIds.mapNotNull { allLocal[it] }

        val cover = PlaylistCoverGenerator.generateCoverForPlaylist(
            context = context,
            playlistId = playlistId,
            tracks = tracks,
            forceRegenerate = true
        )
        if (cover != null) {
            database.playlistDao().updatePlaylist(playlist.copy(coverPath = cover))
        }
    }
}
