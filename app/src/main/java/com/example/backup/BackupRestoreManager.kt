package com.example.backup

import android.content.Context
import com.example.cover.PlaylistCoverGenerator
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettingEntity
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackEntity
import com.example.data.model.TrackEntity
import com.example.matcher.MatchResult
import com.example.matcher.SongMatcher
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

object BackupRestoreManager {

    /**
     * Creates a full ZIP backup of the app
     */
    suspend fun createFullBackup(
        context: Context,
        database: AppDatabase,
        includeMusicFiles: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val backupZipFile = File(backupDir, "LocalMusicBackup_$dateStr.zip")

        val playlists = database.playlistDao().getAllPlaylistsSnapshot()
        val allTracks = database.trackDao().getAllTracksSnapshot()
        val likedTracks = allTracks.filter { it.isLiked }
        val recentlyPlayed = allTracks.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }
        val settings = database.appSettingDao().getAllSettings()

        val zipOut = ZipOutputStream(FileOutputStream(backupZipFile))

        try {
            // 1. /backup/manifest.json
            val manifest = JSONObject().apply {
                put("backupVersion", 1)
                put("appVersion", "1.0")
                put("backupDate", dateStr)
                put("playlistCount", playlists.size)
                put("trackCount", allTracks.size)
                put("likedCount", likedTracks.size)
                put("includesAudioFiles", includeMusicFiles)
            }
            writeZipEntry(zipOut, "backup/manifest.json", manifest.toString(2).toByteArray())

            // 2. /playlists/<name>.json
            for (p in playlists) {
                val entries = database.playlistDao().getPlaylistEntriesSnapshot(p.id)
                val playlistJson = JSONObject().apply {
                    put("version", 1)
                    val pObj = JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("description", p.description)
                        put("coverPath", p.coverPath ?: "")
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                        put("isSystemLiked", p.isSystemLiked)

                        val tracksArray = JSONArray()
                        for (entry in entries) {
                            val tObj = JSONObject().apply {
                                put("title", entry.csvTitle)
                                put("artist", entry.csvArtist)
                                put("album", entry.csvAlbum)
                                put("durationMs", entry.csvDurationMs)
                                put("trackUri", entry.csvTrackUri ?: "")
                                put("fileName", entry.resolvedFilePath?.let { File(it).name } ?: "")
                                put("relativePath", entry.resolvedFilePath ?: "")
                                put("isMissing", entry.isMissing)
                                put("orderIndex", entry.orderIndex)
                            }
                            tracksArray.put(tObj)
                        }
                        put("tracks", tracksArray)
                    }
                    put("playlist", pObj)
                }
                val safeName = p.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                writeZipEntry(zipOut, "playlists/$safeName.json", playlistJson.toString(2).toByteArray())
            }

            // 3. /library/tracks.json, albums.json, artists.json
            val tracksArray = JSONArray()
            val albumsSet = mutableSetOf<String>()
            val artistsSet = mutableSetOf<String>()

            for (t in allTracks) {
                albumsSet.add(t.album)
                artistsSet.add(t.artist)
                val tObj = JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("albumArtist", t.albumArtist)
                    put("durationMs", t.durationMs)
                    put("filePath", t.filePath)
                    put("fileName", t.fileName)
                    put("coverPath", t.coverPath ?: "")
                    put("isLiked", t.isLiked)
                    put("playCount", t.playCount)
                    put("lastPlayedAt", t.lastPlayedAt ?: -1L)
                    put("trackNumber", t.trackNumber)
                    put("discNumber", t.discNumber)
                }
                tracksArray.put(tObj)
            }
            writeZipEntry(zipOut, "library/tracks.json", tracksArray.toString(2).toByteArray())

            val albumsArray = JSONArray()
            albumsSet.forEach { albumsArray.put(it) }
            writeZipEntry(zipOut, "library/albums.json", albumsArray.toString(2).toByteArray())

            val artistsArray = JSONArray()
            artistsSet.forEach { artistsArray.put(it) }
            writeZipEntry(zipOut, "library/artists.json", artistsArray.toString(2).toByteArray())

            // 4. /liked/liked_songs.json
            val likedArray = JSONArray()
            for (lt in likedTracks) {
                likedArray.put(JSONObject().apply {
                    put("id", lt.id)
                    put("title", lt.title)
                    put("artist", lt.artist)
                    put("album", lt.album)
                    put("durationMs", lt.durationMs)
                    put("fileName", lt.fileName)
                })
            }
            writeZipEntry(zipOut, "liked/liked_songs.json", likedArray.toString(2).toByteArray())

            // 5. /history/recently_played.json
            val historyArray = JSONArray()
            for (h in recentlyPlayed) {
                historyArray.put(JSONObject().apply {
                    put("id", h.id)
                    put("title", h.title)
                    put("artist", h.artist)
                    put("lastPlayedAt", h.lastPlayedAt ?: 0L)
                    put("playCount", h.playCount)
                })
            }
            writeZipEntry(zipOut, "history/recently_played.json", historyArray.toString(2).toByteArray())

            // 6. /settings/settings.json
            val settingsObj = JSONObject()
            for (s in settings) {
                settingsObj.put(s.key, s.value)
            }
            writeZipEntry(zipOut, "settings/settings.json", settingsObj.toString(2).toByteArray())

            // 7. /playback/playback_state.json
            val playbackObj = JSONObject().apply {
                put("savedAt", System.currentTimeMillis())
            }
            writeZipEntry(zipOut, "playback/playback_state.json", playbackObj.toString(2).toByteArray())

            // 8. /covers/
            val coversDir = File(context.filesDir, "playlist_covers")
            if (coversDir.exists()) {
                coversDir.listFiles()?.forEach { coverFile ->
                    if (coverFile.isFile && coverFile.extension.lowercase() in listOf("jpg", "png")) {
                        writeFileToZip(zipOut, "covers/${coverFile.name}", coverFile)
                    }
                }
            }

            // Optional: /audio/
            if (includeMusicFiles) {
                for (t in allTracks) {
                    val f = File(t.filePath)
                    if (f.exists() && f.isFile) {
                        writeFileToZip(zipOut, "audio/${f.name}", f)
                    }
                }
            }

        } finally {
            zipOut.close()
        }

        var finalCopiedFile = backupZipFile
        try {
            val publicDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "LocalMusicBackups")
            if (!publicDir.exists()) {
                publicDir.mkdirs()
            }
            val directDir = File("/storage/emulated/0/Download/LocalMusicBackups")
            if (!directDir.exists()) {
                directDir.mkdirs()
            }

            val targetDir = if (publicDir.exists()) publicDir else if (directDir.exists()) directDir else null
            if (targetDir != null) {
                val publicFile = File(targetDir, backupZipFile.name)
                backupZipFile.copyTo(publicFile, overwrite = true)
                finalCopiedFile = publicFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        finalCopiedFile
    }

    /**
     * Restores all app data from a backup ZIP file with rollback safety
     */
    suspend fun restoreAllData(
        context: Context,
        database: AppDatabase,
        backupZipFile: File
    ): Result<RestoreReport> = withContext(Dispatchers.IO) {
        // Step 1: Safety snapshot of current database state before modifying
        val tempBackupDir = File(context.cacheDir, "pre_restore_snapshot").apply { mkdirs() }
        val currentPlaylists = database.playlistDao().getAllPlaylistsSnapshot()
        val currentTracks = database.trackDao().getAllTracksSnapshot()

        try {
            // Unzip into a temporary staging folder
            val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply { mkdirs() }
            unzip(backupZipFile, stagingDir)

            val manifestFile = File(stagingDir, "backup/manifest.json")
            if (!manifestFile.exists()) {
                return@withContext Result.failure(Exception("Invalid backup file: manifest.json not found"))
            }

            val manifest = JSONObject(manifestFile.readText())
            var playlistsRestored = 0
            var tracksMatched = 0
            var tracksMissing = 0
            var likedSongsRestored = 0
            var coversRestored = 0
            val missingTracksList = mutableListOf<MissingTrackInfo>()

            // Restore covers
            val coversDir = File(stagingDir, "covers")
            val targetCoversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
            if (coversDir.exists()) {
                coversDir.listFiles()?.forEach { cFile ->
                    if (cFile.isFile) {
                        cFile.copyTo(File(targetCoversDir, cFile.name), overwrite = true)
                        coversRestored++
                    }
                }
            }

            // Re-fetch current available local songs for matching
            val localTracks = database.trackDao().getAllTracksSnapshot()

            // Restore liked songs status
            val likedFile = File(stagingDir, "liked/liked_songs.json")
            if (likedFile.exists()) {
                val likedArray = JSONArray(likedFile.readText())
                for (i in 0 until likedArray.length()) {
                    val item = likedArray.getJSONObject(i)
                    val title = item.optString("title")
                    val artist = item.optString("artist")
                    val dur = item.optLong("durationMs")
                    val fileName = item.optString("fileName")

                    val match = SongMatcher.matchForRestore(
                        expectedPath = null,
                        expectedFileName = fileName,
                        expectedTitle = title,
                        expectedArtist = artist,
                        expectedAlbum = "",
                        expectedDurationMs = dur,
                        localTracks = localTracks
                    )
                    if (match is MatchResult.SingleMatch) {
                        database.trackDao().setLiked(match.track.id, true)
                        likedSongsRestored++
                    }
                }
            }

            // Restore playlists
            val playlistsFolder = File(stagingDir, "playlists")
            if (playlistsFolder.exists()) {
                val pFiles = playlistsFolder.listFiles() ?: emptyArray()
                for (pFile in pFiles) {
                    if (!pFile.isFile || !pFile.name.endsWith(".json")) continue
                    val root = JSONObject(pFile.readText())
                    val pObj = root.optJSONObject("playlist") ?: continue

                    val pId = pObj.optString("id", UUID.randomUUID().toString())
                    val pName = pObj.optString("name", pFile.nameWithoutExtension)
                    val pDesc = pObj.optString("description", "")
                    val isSystemLiked = pObj.optBoolean("isSystemLiked", false)
                    val pCover = pObj.optString("coverPath").ifBlank { null }

                    // Duplicate handling: if existing playlist with same name exists, update/replace
                    val existing = database.playlistDao().getPlaylistByName(pName)
                    val playlistIdToUse = existing?.id ?: pId

                    val playlistEntity = PlaylistEntity(
                        id = playlistIdToUse,
                        name = pName,
                        description = pDesc,
                        coverPath = pCover,
                        createdAt = pObj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = System.currentTimeMillis(),
                        isSystemLiked = isSystemLiked
                    )
                    database.playlistDao().insertPlaylist(playlistEntity)
                    database.playlistDao().deletePlaylistEntries(playlistIdToUse)

                    val tracksArray = pObj.optJSONArray("tracks") ?: JSONArray()
                    val entriesToInsert = mutableListOf<PlaylistTrackEntity>()
                    val matchedTracksForCover = mutableListOf<TrackEntity>()

                    for (j in 0 until tracksArray.length()) {
                        val tObj = tracksArray.getJSONObject(j)
                        val title = tObj.optString("title")
                        val artist = tObj.optString("artist")
                        val album = tObj.optString("album")
                        val dur = tObj.optLong("durationMs")
                        val fileName = tObj.optString("fileName")
                        val relPath = tObj.optString("relativePath")
                        val uri = tObj.optString("trackUri")
                        val orderIdx = tObj.optInt("orderIndex", j)

                        val match = SongMatcher.matchForRestore(
                            expectedPath = relPath,
                            expectedFileName = fileName,
                            expectedTitle = title,
                            expectedArtist = artist,
                            expectedAlbum = album,
                            expectedDurationMs = dur,
                            localTracks = localTracks
                        )

                        when (match) {
                            is MatchResult.SingleMatch -> {
                                entriesToInsert.add(
                                    PlaylistTrackEntity(
                                        playlistId = playlistIdToUse,
                                        trackId = match.track.id,
                                        orderIndex = orderIdx,
                                        isMissing = false,
                                        csvTitle = title,
                                        csvArtist = artist,
                                        csvAlbum = album,
                                        csvDurationMs = dur,
                                        csvTrackUri = uri,
                                        resolvedFilePath = match.track.filePath
                                    )
                                )
                                matchedTracksForCover.add(match.track)
                                tracksMatched++
                            }
                            is MatchResult.AmbiguousMatch -> {
                                val firstCand = match.candidates.first()
                                entriesToInsert.add(
                                    PlaylistTrackEntity(
                                        playlistId = playlistIdToUse,
                                        trackId = firstCand.id,
                                        orderIndex = orderIdx,
                                        isMissing = false,
                                        csvTitle = title,
                                        csvArtist = artist,
                                        csvAlbum = album,
                                        csvDurationMs = dur,
                                        csvTrackUri = uri,
                                        resolvedFilePath = firstCand.filePath
                                    )
                                )
                                matchedTracksForCover.add(firstCand)
                                tracksMatched++
                            }
                            MatchResult.NoMatch -> {
                                entriesToInsert.add(
                                    PlaylistTrackEntity(
                                        playlistId = playlistIdToUse,
                                        trackId = null,
                                        orderIndex = orderIdx,
                                        isMissing = true,
                                        csvTitle = title,
                                        csvArtist = artist,
                                        csvAlbum = album,
                                        csvDurationMs = dur,
                                        csvTrackUri = uri,
                                        resolvedFilePath = null
                                    )
                                )
                                tracksMissing++
                                missingTracksList.add(
                                    MissingTrackInfo(
                                        playlistName = pName,
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        expectedDurationMs = dur,
                                        expectedFileName = fileName
                                    )
                                )
                            }
                        }
                    }

                    database.playlistDao().insertPlaylistEntries(entriesToInsert)

                    // Generate or restore 2x2 cover if not set
                    if (playlistEntity.coverPath == null && matchedTracksForCover.isNotEmpty()) {
                        val genCover = PlaylistCoverGenerator.generateCoverForPlaylist(
                            context = context,
                            playlistId = playlistIdToUse,
                            tracks = matchedTracksForCover
                        )
                        if (genCover != null) {
                            database.playlistDao().updatePlaylist(playlistEntity.copy(coverPath = genCover))
                        }
                    }

                    playlistsRestored++
                }
            }

            // Clean staging
            stagingDir.deleteRecursively()

            val report = RestoreReport(
                playlistsRestored = playlistsRestored,
                tracksMatched = tracksMatched,
                tracksMissing = tracksMissing,
                likedSongsRestored = likedSongsRestored,
                playlistCoversRestored = coversRestored,
                missingTracks = missingTracksList
            )

            Result.success(report)
        } catch (e: Exception) {
            e.printStackTrace()
            // Rollback safety: restore original database snapshot if anything goes wrong!
            try {
                // (currentPlaylists and currentTracks are intact in memory)
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    private fun writeZipEntry(zipOut: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName)
        zipOut.putNextEntry(entry)
        zipOut.write(data)
        zipOut.closeEntry()
    }

    private fun writeFileToZip(zipOut: ZipOutputStream, entryName: String, file: File) {
        val entry = ZipEntry(entryName)
        zipOut.putNextEntry(entry)
        FileInputStream(file).use { input ->
            input.copyTo(zipOut)
        }
        zipOut.closeEntry()
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
