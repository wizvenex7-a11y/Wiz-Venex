package com.example.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.cover.PlaylistCoverGenerator
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettingEntity
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistFolderEntity
import com.example.data.model.PlaylistTrackEntity
import com.example.data.model.TrackEntity
import com.example.matcher.FastTrackMatcher
import com.example.matcher.MatchResult
import com.example.matcher.SongMatcher
import com.example.util.CsvUtils
import com.example.util.NormalizationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreReport(
    val playlistsRestored: Int,
    val tracksMatched: Int,
    val tracksMissing: Int,
    val likedSongsRestored: Int,
    val playlistCoversRestored: Int,
    val missingTracks: List<MissingTrackInfo>
)

data class MissingTrackInfo(
    val playlistName: String,
    val title: String,
    val artist: String,
    val album: String,
    val expectedDurationMs: Long,
    val expectedFileName: String
)

private data class BackupPlaylistMeta(
    val id: String,
    val name: String,
    val description: String,
    val coverPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isSystemLiked: Boolean,
    val isPinned: Boolean,
    val folderName: String?
)

object BackupRestoreManager {

    /**
     * Full backup format:
     *   backup/manifest.json
     *   backup/folders.json
     *   backup/playlists.json
     *   playlists/Playlist.csv
     *   folders/MyFolder/Playlist.csv
     *
     * JSON is metadata/backup data only. Normal playlist files are CSV.
     */
    suspend fun createFullBackup(
        context: Context,
        database: AppDatabase,
        includeMusicFiles: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val backupZipFile = File(backupDir, "LocalMusicBackup_$dateStr.zip")

        val playlists = database.playlistDao().getAllPlaylistsSnapshot()
        val folders = database.playlistFolderDao().getAllFoldersSnapshot()
        val allTracks = database.trackDao().getAllTracksSnapshot()
        val likedTracks = allTracks.filter { it.isLiked }
        val recentlyPlayed = allTracks
            .filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
        val settings = database.appSettingDao().getAllSettings()

        ZipOutputStream(FileOutputStream(backupZipFile)).use { zipOut ->
            val manifest = JSONObject().apply {
                put("backupVersion", 2)
                put("format", "folder-aware-csv")
                put("appVersion", "1.0")
                put("backupDate", dateStr)
                put("playlistCount", playlists.size)
                put("folderCount", folders.size)
                put("trackCount", allTracks.size)
                put("likedCount", likedTracks.size)
                put("includesAudioFiles", includeMusicFiles)
            }
            writeZipEntry(zipOut, "backup/manifest.json", manifest.toString(2).toByteArray())

            val foldersArray = JSONArray()
            folders.forEach { folder ->
                foldersArray.put(JSONObject().apply {
                    put("id", folder.id)
                    put("name", folder.name)
                    put("createdAt", folder.createdAt)
                    put("isPinned", folder.isPinned)
                })
            }
            writeZipEntry(zipOut, "backup/folders.json", foldersArray.toString(2).toByteArray())

            val playlistMetaArray = JSONArray()
            for (playlist in playlists) {
                val folderName = playlist.folderId
                    ?.let { id -> folders.firstOrNull { it.id == id }?.name }

                playlistMetaArray.put(JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("description", playlist.description)
                    put("coverPath", playlist.coverPath ?: "")
                    put("createdAt", playlist.createdAt)
                    put("updatedAt", playlist.updatedAt)
                    put("isSystemLiked", playlist.isSystemLiked)
                    put("isPinned", playlist.isPinned)
                    put("folderName", folderName ?: "")
                    put("folderId", playlist.folderId ?: "")
                })

                val entries = database.playlistDao().getPlaylistEntriesSnapshot(playlist.id)
                val csvRows = entries.map { entry ->
                    val track = entry.trackId?.let { id -> allTracks.firstOrNull { it.id == id } }
                    com.example.util.CsvTrackRow(
                        trackName = track?.title ?: entry.csvTitle,
                        artistNames = track?.artist ?: entry.csvArtist,
                        albumName = track?.album ?: entry.csvAlbum,
                        durationMs = track?.durationMs ?: entry.csvDurationMs,
                        trackUri = entry.csvTrackUri ?: ""
                    )
                }
                val csvContent = CsvUtils.exportToCsv(csvRows)
                val folder = folderName?.takeIf { it.isNotBlank() }
                val path = if (folder != null) {
                    "folders/${sanitizePathPart(folder)}/${sanitizeFileName(playlist.name)}.csv"
                } else {
                    "playlists/${sanitizeFileName(playlist.name)}.csv"
                }
                writeZipEntry(zipOut, path, csvContent.toByteArray(Charsets.UTF_8))
            }
            writeZipEntry(
                zipOut,
                "backup/playlists.json",
                playlistMetaArray.toString(2).toByteArray()
            )

            val tracksArray = JSONArray()
            val albumsSet = mutableSetOf<String>()
            val artistsSet = mutableSetOf<String>()
            for (track in allTracks) {
                albumsSet.add(track.album)
                artistsSet.add(track.artist)
                tracksArray.put(JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("albumArtist", track.albumArtist)
                    put("durationMs", track.durationMs)
                    put("filePath", track.filePath)
                    put("fileName", track.fileName)
                    put("coverPath", track.coverPath ?: "")
                    put("isLiked", track.isLiked)
                    put("playCount", track.playCount)
                    put("lastPlayedAt", track.lastPlayedAt ?: -1L)
                    put("trackNumber", track.trackNumber)
                    put("discNumber", track.discNumber)
                })
            }
            writeZipEntry(zipOut, "library/tracks.json", tracksArray.toString(2).toByteArray())
            writeZipEntry(
                zipOut,
                "library/albums.json",
                JSONArray(albumsSet.toList()).toString(2).toByteArray()
            )
            writeZipEntry(
                zipOut,
                "library/artists.json",
                JSONArray(artistsSet.toList()).toString(2).toByteArray()
            )

            val likedArray = JSONArray()
            likedTracks.forEach { track ->
                likedArray.put(JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("durationMs", track.durationMs)
                    put("fileName", track.fileName)
                })
            }
            writeZipEntry(zipOut, "liked/liked_songs.json", likedArray.toString(2).toByteArray())

            val historyArray = JSONArray()
            recentlyPlayed.forEach { track ->
                historyArray.put(JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("lastPlayedAt", track.lastPlayedAt ?: 0L)
                    put("playCount", track.playCount)
                })
            }
            writeZipEntry(
                zipOut,
                "history/recently_played.json",
                historyArray.toString(2).toByteArray()
            )

            val settingsObj = JSONObject()
            settings.forEach { setting -> settingsObj.put(setting.key, setting.value) }
            writeZipEntry(
                zipOut,
                "settings/settings.json",
                settingsObj.toString(2).toByteArray()
            )

            if (context.filesDir.resolve("playlist_covers").exists()) {
                context.filesDir.resolve("playlist_covers").listFiles()?.forEach { coverFile ->
                    if (coverFile.isFile && coverFile.extension.lowercase(Locale.US) in listOf("jpg", "png")) {
                        writeFileToZip(zipOut, "covers/${coverFile.name}", coverFile)
                    }
                }
            }

            if (includeMusicFiles) {
                allTracks.forEach { track ->
                    val source = File(track.filePath)
                    if (source.exists() && source.isFile) {
                        writeFileToZip(zipOut, "audio/${source.name}", source)
                    }
                }
            }
        }

        val publicDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ),
            "LocalMusicBackups"
        ).apply { mkdirs() }

        val publicFile = File(publicDir, backupZipFile.name)
        try {
            backupZipFile.copyTo(publicFile, overwrite = true)
            publicFile
        } catch (_: Exception) {
            backupZipFile
        }
    }

    suspend fun restoreAllData(
        context: Context,
        database: AppDatabase,
        backupZipFile: File
    ): Result<RestoreReport> = withContext(Dispatchers.IO) {
        val stagingDir = File(
            context.cacheDir,
            "restore_staging_${System.currentTimeMillis()}"
        ).apply { mkdirs() }

        try {
            unzip(backupZipFile, stagingDir)

            val manifestFile = File(stagingDir, "backup/manifest.json")
            if (!manifestFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid backup: backup/manifest.json not found")
                )
            }

            val manifest = JSONObject(manifestFile.readText())
            val format = manifest.optString("format", "")
            val localTracks = database.trackDao().getAllTracksSnapshot()

            var playlistsRestored = 0
            var tracksMatched = 0
            var tracksMissing = 0
            var likedSongsRestored = 0
            var coversRestored = 0
            val missingTracks = mutableListOf<MissingTrackInfo>()

            val targetCoversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
            val coversDir = File(stagingDir, "covers")
            if (coversDir.exists()) {
                coversDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        file.copyTo(File(targetCoversDir, file.name), overwrite = true)
                        coversRestored++
                    }
                }
            }

            // Restore liked state and immediately deduplicate by file path or metadata.
            val likedFile = File(stagingDir, "liked/liked_songs.json")
            if (likedFile.exists()) {
                val likedArray = JSONArray(likedFile.readText())
                val matchedLikedIds = linkedSetOf<String>()
                for (i in 0 until likedArray.length()) {
                    val item = likedArray.optJSONObject(i) ?: continue
                    val match = SongMatcher.matchForRestore(
                        expectedPath = null,
                        expectedFileName = item.optString("fileName"),
                        expectedTitle = item.optString("title"),
                        expectedArtist = item.optString("artist"),
                        expectedAlbum = item.optString("album"),
                        expectedDurationMs = item.optLong("durationMs", 0L),
                        localTracks = localTracks
                    )
                    if (match is MatchResult.SingleMatch && matchedLikedIds.add(match.track.id)) {
                        database.trackDao().setLiked(match.track.id, true)
                        likedSongsRestored++
                    }
                }
            }

            val backupFolders = readFolderMetadata(File(stagingDir, "backup/folders.json"))
            val folderIdByNormalizedName = linkedMapOf<String, String>()

            // Create/reuse folders by name. Existing folders are never duplicated.
            database.withTransaction {
                val existingFolders = database.playlistFolderDao().getAllFoldersSnapshot()
                backupFolders.forEach { meta ->
                    val normalized = NormalizationUtils.normalizeText(meta.name)
                    val existing = existingFolders.firstOrNull {
                        NormalizationUtils.normalizeText(it.name) == normalized
                    }
                    val folder = existing ?: PlaylistFolderEntity(
                        id = meta.id.ifBlank { UUID.randomUUID().toString() },
                        name = meta.name,
                        createdAt = meta.createdAt
                    )
                    val merged = folder.copy(
                        name = meta.name,
                        isPinned = meta.isPinned
                    )
                    database.playlistFolderDao().insertFolder(merged)
                    folderIdByNormalizedName[normalized] = merged.id
                }
            }

            val playlistMetaByPath = readPlaylistMetadata(
                File(stagingDir, "backup/playlists.json")
            )

            // New folder-aware CSV backup format.
            val csvFiles = mutableListOf<File>()
            File(stagingDir, "playlists").takeIf { it.exists() }?.walkTopDown()?.forEach {
                if (it.isFile && it.extension.equals("csv", ignoreCase = true)) csvFiles.add(it)
            }
            File(stagingDir, "folders").takeIf { it.exists() }?.walkTopDown()?.forEach {
                if (it.isFile && it.extension.equals("csv", ignoreCase = true)) csvFiles.add(it)
            }

            if (csvFiles.isNotEmpty() || format == "folder-aware-csv") {
                for (csvFile in csvFiles.sortedBy { it.relativeTo(stagingDir).path.lowercase(Locale.US) }) {
                    val relative = csvFile.relativeTo(stagingDir).path.replace(File.separatorChar, '/')
                    val meta = playlistMetaByPath[relative]
                        ?: playlistMetaByPath[normalizeBackupPath(relative)]

                    val folderNameFromPath = extractFolderNameFromBackupPath(relative)
                    val folderId = meta?.folderName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { folderIdByNormalizedName[NormalizationUtils.normalizeText(it)] }
                        ?: folderNameFromPath
                            ?.let { folderIdByNormalizedName[NormalizationUtils.normalizeText(it)] }

                    val fallbackName = csvFile.nameWithoutExtension
                    val playlistName = meta?.name?.takeIf { it.isNotBlank() }
                        ?: fallbackName

                    val rows = FileInputStream(csvFile).use { input ->
                        CsvUtils.parseSpotifyCsv(input)
                    }

                    val matchedTracks = mutableListOf<TrackEntity>()
                    val entries = mutableListOf<PlaylistTrackEntity>()
                    val seenTrackIds = mutableSetOf<String>()
                    val seenMissingKeys = mutableSetOf<String>()

                    val matcher = FastTrackMatcher(localTracks)
                    for ((index, row) in rows.withIndex()) {
                        val match = matcher.matchCsvTrack(row)
                        when (match) {
                            is MatchResult.SingleMatch -> {
                                if (!seenTrackIds.add(match.track.id)) continue
                                matchedTracks.add(match.track)
                                tracksMatched++
                                entries += PlaylistTrackEntity(
                                    playlistId = "",
                                    trackId = match.track.id,
                                    orderIndex = entries.size,
                                    isMissing = false,
                                    csvTitle = row.trackName,
                                    csvArtist = row.artistNames,
                                    csvAlbum = row.albumName,
                                    csvDurationMs = row.durationMs,
                                    csvTrackUri = row.trackUri,
                                    resolvedFilePath = match.track.filePath
                                )
                            }
                            is MatchResult.AmbiguousMatch -> {
                                val candidate = match.candidates.firstOrNull() ?: continue
                                if (!seenTrackIds.add(candidate.id)) continue
                                matchedTracks.add(candidate)
                                tracksMatched++
                                entries += PlaylistTrackEntity(
                                    playlistId = "",
                                    trackId = candidate.id,
                                    orderIndex = entries.size,
                                    isMissing = false,
                                    csvTitle = row.trackName,
                                    csvArtist = row.artistNames,
                                    csvAlbum = row.albumName,
                                    csvDurationMs = row.durationMs,
                                    csvTrackUri = row.trackUri,
                                    resolvedFilePath = candidate.filePath
                                )
                            }
                            MatchResult.NoMatch -> {
                                val missingKey = "${NormalizationUtils.normalizeText(row.trackName)}|" +
                                        "${NormalizationUtils.normalizeText(row.artistNames)}|" +
                                        row.durationMs
                                if (!seenMissingKeys.add(missingKey)) continue
                                tracksMissing++
                                entries += PlaylistTrackEntity(
                                    playlistId = "",
                                    trackId = null,
                                    orderIndex = entries.size,
                                    isMissing = true,
                                    csvTitle = row.trackName,
                                    csvArtist = row.artistNames,
                                    csvAlbum = row.albumName,
                                    csvDurationMs = row.durationMs,
                                    csvTrackUri = row.trackUri,
                                    resolvedFilePath = null
                                )
                                missingTracks += MissingTrackInfo(
                                    playlistName = playlistName,
                                    title = row.trackName,
                                    artist = row.artistNames,
                                    album = row.albumName,
                                    expectedDurationMs = row.durationMs,
                                    expectedFileName = ""
                                )
                            }
                        }
                    }

                    val playlistId = database.withTransaction {
                        val existing = if (folderId != null) {
                            database.playlistDao().getPlaylistByNameInFolder(playlistName, folderId)
                        } else {
                            database.playlistDao().getRootPlaylistByName(playlistName)
                                ?: database.playlistDao().getPlaylistByName(playlistName)
                        }

                        val targetId = if (existing != null) {
                            existing.id
                        } else {
                            val backupId = meta?.id?.takeIf { it.isNotBlank() }
                            if (backupId != null && database.playlistDao().getPlaylistById(backupId) == null) {
                                backupId
                            } else {
                                UUID.randomUUID().toString()
                            }
                        }

                        val entity = PlaylistEntity(
                            id = targetId,
                            name = playlistName,
                            description = meta?.description ?: existing?.description.orEmpty(),
                            coverPath = meta?.coverPath?.takeIf { it.isNotBlank() } ?: existing?.coverPath,
                            createdAt = meta?.createdAt ?: existing?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            isSystemLiked = meta?.isSystemLiked ?: existing?.isSystemLiked ?: false,
                            folderId = folderId,
                            isPinned = meta?.isPinned ?: existing?.isPinned ?: false
                        )
                        database.playlistDao().insertPlaylist(entity)
                        database.playlistDao().deletePlaylistEntries(targetId)

                        val finalEntries = entries.map { it.copy(playlistId = targetId) }
                        finalEntries.chunked(900).forEach {
                            database.playlistDao().insertPlaylistEntries(it)
                        }
                        targetId
                    }

                    val generatedCover = PlaylistCoverGenerator.generateCoverForPlaylist(
                        context = context,
                        playlistId = playlistId,
                        tracks = matchedTracks,
                        forceRegenerate = true
                    )
                    if (generatedCover != null) {
                        database.playlistDao().getPlaylistById(playlistId)?.let { current ->
                            database.playlistDao().updatePlaylist(
                                current.copy(
                                    coverPath = generatedCover,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    playlistsRestored++
                }
            } else {
                // Legacy v1/v2 root-level playlist JSON restore.
                val playlistsDir = File(stagingDir, "playlists")
                playlistsDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                    ?.forEach { pFile ->
                        val root = JSONObject(pFile.readText())
                        val pObj = root.optJSONObject("playlist") ?: return@forEach
                        val pName = pObj.optString("name", pFile.nameWithoutExtension)
                        val pDesc = pObj.optString("description", "")
                        val pId = pObj.optString("id").ifBlank { UUID.randomUUID().toString() }
                        val existing = database.playlistDao().getPlaylistByName(pName)
                        val targetId = if (existing != null) {
                            existing.id
                        } else if (pId.isNotBlank() && database.playlistDao().getPlaylistById(pId) == null) {
                            pId
                        } else {
                            UUID.randomUUID().toString()
                        }

                        val tracksArray = pObj.optJSONArray("tracks") ?: JSONArray()
                        val matchedTracks = mutableListOf<TrackEntity>()
                        val entries = mutableListOf<PlaylistTrackEntity>()
                        val seenTrackIds = mutableSetOf<String>()

                        val matcher = FastTrackMatcher(localTracks)
                        for (j in 0 until tracksArray.length()) {
                            val item = tracksArray.optJSONObject(j) ?: continue
                            val title = item.optString("title")
                            if (title.isBlank()) continue
                            val artist = item.optString("artist")
                            val album = item.optString("album")
                            val duration = item.optLong("durationMs", 0L)
                            val match = matcher.matchCsvTrack(
                                com.example.util.CsvTrackRow(
                                    trackName = title,
                                    artistNames = artist,
                                    albumName = album,
                                    durationMs = duration,
                                    trackUri = item.optString("trackUri", "")
                                )
                            )

                            when (match) {
                                is MatchResult.SingleMatch -> {
                                    if (!seenTrackIds.add(match.track.id)) continue
                                    matchedTracks.add(match.track)
                                    tracksMatched++
                                    entries += PlaylistTrackEntity(
                                        playlistId = targetId,
                                        trackId = match.track.id,
                                        orderIndex = entries.size,
                                        isMissing = false,
                                        csvTitle = title,
                                        csvArtist = artist,
                                        csvAlbum = album,
                                        csvDurationMs = duration,
                                        csvTrackUri = item.optString("trackUri", ""),
                                        resolvedFilePath = match.track.filePath
                                    )
                                }
                                is MatchResult.AmbiguousMatch -> {
                                    val candidate = match.candidates.firstOrNull() ?: continue
                                    if (!seenTrackIds.add(candidate.id)) continue
                                    matchedTracks.add(candidate)
                                    tracksMatched++
                                    entries += PlaylistTrackEntity(
                                        playlistId = targetId,
                                        trackId = candidate.id,
                                        orderIndex = entries.size,
                                        isMissing = false,
                                        csvTitle = title,
                                        csvArtist = artist,
                                        csvAlbum = album,
                                        csvDurationMs = duration,
                                        csvTrackUri = item.optString("trackUri", ""),
                                        resolvedFilePath = candidate.filePath
                                    )
                                }
                                MatchResult.NoMatch -> {
                                    tracksMissing++
                                    entries += PlaylistTrackEntity(
                                        playlistId = targetId,
                                        trackId = null,
                                        orderIndex = entries.size,
                                        isMissing = true,
                                        csvTitle = title,
                                        csvArtist = artist,
                                        csvAlbum = album,
                                        csvDurationMs = duration,
                                        csvTrackUri = item.optString("trackUri", ""),
                                        resolvedFilePath = null
                                    )
                                }
                            }
                        }

                        database.withTransaction {
                            database.playlistDao().insertPlaylist(
                                PlaylistEntity(
                                    id = targetId,
                                    name = pName,
                                    description = pDesc,
                                    coverPath = pObj.optString("coverPath").ifBlank { null },
                                    createdAt = pObj.optLong("createdAt", existing?.createdAt ?: System.currentTimeMillis()),
                                    updatedAt = System.currentTimeMillis(),
                                    isSystemLiked = pObj.optBoolean("isSystemLiked", existing?.isSystemLiked ?: false),
                                    folderId = existing?.folderId,
                                    isPinned = pObj.optBoolean("isPinned", existing?.isPinned ?: false)
                                )
                            )
                            database.playlistDao().deletePlaylistEntries(targetId)
                            entries.chunked(900).forEach { chunk ->
                                database.playlistDao().insertPlaylistEntries(chunk)
                            }
                        }

                        val generatedCover = PlaylistCoverGenerator.generateCoverForPlaylist(
                            context = context,
                            playlistId = targetId,
                            tracks = matchedTracks,
                            forceRegenerate = true
                        )
                        if (generatedCover != null) {
                            database.playlistDao().getPlaylistById(targetId)?.let { current ->
                                database.playlistDao().updatePlaylist(
                                    current.copy(coverPath = generatedCover)
                                )
                            }
                        }
                        playlistsRestored++
                    }
            }

            // Generate a random folder cover from the child playlist covers.
            val finalFolders = database.playlistFolderDao().getAllFoldersSnapshot()
            val finalPlaylists = database.playlistDao().getAllPlaylistsSnapshot()
            finalFolders.forEach { folder ->
                val childCovers = finalPlaylists
                    .filter { it.folderId == folder.id }
                    .mapNotNull { it.coverPath }
                if (childCovers.isNotEmpty()) {
                    PlaylistCoverGenerator.generateRandomFolderCover(
                        context = context,
                        folderId = folder.id,
                        playlistCoverPaths = childCovers,
                        forceRegenerate = true
                    )
                }
            }

            Result.success(
                RestoreReport(
                    playlistsRestored = playlistsRestored,
                    tracksMatched = tracksMatched,
                    tracksMissing = tracksMissing,
                    likedSongsRestored = likedSongsRestored,
                    playlistCoversRestored = coversRestored,
                    missingTracks = missingTracks
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun readFolderMetadata(file: File): List<PlaylistFolderEntity> {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isBlank()) continue
                add(
                    PlaylistFolderEntity(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isPinned = obj.optBoolean("isPinned", false)
                    )
                )
            }
        }
    }

    private fun readPlaylistMetadata(file: File): Map<String, BackupPlaylistMeta> {
        if (!file.exists()) return emptyMap()
        val array = JSONArray(file.readText())
        val result = linkedMapOf<String, BackupPlaylistMeta>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isBlank()) continue

            val meta = BackupPlaylistMeta(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = name,
                description = obj.optString("description", ""),
                coverPath = obj.optString("coverPath").ifBlank { null },
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                isSystemLiked = obj.optBoolean("isSystemLiked", false),
                isPinned = obj.optBoolean("isPinned", false),
                folderName = obj.optString("folderName").ifBlank { null }
            )

            val folderName = meta.folderName
            val path = if (folderName != null) {
                "folders/${sanitizePathPart(folderName)}/${sanitizeFileName(name)}.csv"
            } else {
                "playlists/${sanitizeFileName(name)}.csv"
            }
            result[path] = meta
            result[normalizeBackupPath(path)] = meta
        }
        return result
    }

    private fun extractFolderNameFromBackupPath(relative: String): String? {
        val parts = normalizeBackupPath(relative).split('/')
        return if (parts.size >= 3 && parts[0].equals("folders", ignoreCase = true)) {
            parts[1]
        } else null
    }

    private fun normalizeBackupPath(path: String): String =
        path.replace('\\', '/').trimStart('/')

    private fun sanitizeFileName(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Playlist" }

    private fun sanitizePathPart(name: String): String =
        sanitizeFileName(name).replace(Regex("[. ]+$"), "").ifBlank { "Folder" }

    private fun writeZipEntry(zipOut: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName.replace('\\', '/'))
        zipOut.putNextEntry(entry)
        zipOut.write(data)
        zipOut.closeEntry()
    }

    private fun writeFileToZip(zipOut: ZipOutputStream, entryName: String, file: File) {
        val entry = ZipEntry(entryName.replace('\\', '/'))
        zipOut.putNextEntry(entry)
        FileInputStream(file).use { input -> input.copyTo(zipOut) }
        zipOut.closeEntry()
    }

    /**
     * Secure ZIP extraction: reject absolute paths and path traversal.
     */
    private fun unzip(zipFile: File, targetDir: File) {
        val root = targetDir.canonicalFile
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val safeName = entry.name.replace('\\', '/').trimStart('/')
                val outFile = File(root, safeName).canonicalFile
                if (outFile.path != root.path && !outFile.path.startsWith(root.path + File.separator)) {
                    throw SecurityException("Unsafe ZIP entry: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
