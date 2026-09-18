package com.example.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MusicScanner {

    val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "aiff", "wma", "m4b", "mid"
    )

    /**
     * Checks if a file path is located on internal shared storage rather than an SD card or removable storage.
     */
    fun isInternalSharedStoragePath(path: String?): Boolean {
        if (path.isNullOrBlank()) return true
        val lower = path.lowercase()
        // Exclude removable SD cards (typically /storage/XXXX-XXXX or /mnt/media_rw or sdcard1)
        if (Regex("/storage/[a-f0-9]{4}-[a-f0-9]{4}", RegexOption.IGNORE_CASE).containsMatchIn(path)) {
            return false
        }
        if (lower.contains("/mnt/media_rw/") || lower.contains("/storage/sdcard1") || lower.contains("/storage/extsd")) {
            return false
        }
        return true
    }

    /**
     * Scans a directory recursively from a File path
     */
    suspend fun scanDirectory(context: Context, directory: File): List<TrackEntity> =
        withContext(Dispatchers.IO) {
            val tracks = mutableListOf<TrackEntity>()
            if (!directory.exists() || !directory.isDirectory) return@withContext tracks

            fun traverse(dir: File) {
                val files = dir.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory) {
                        // Skip hidden folders (.thumbnails) and Android app data folders for high performance
                        val name = file.name
                        if (!name.startsWith(".") && !name.equals("Android", ignoreCase = true)) {
                            traverse(file)
                        }
                    } else if (file.isFile) {
                        val ext = file.extension.lowercase()
                        if (SUPPORTED_EXTENSIONS.contains(ext)) {
                            extractTrackFromFile(context, file)?.let { tracks.add(it) }
                        }
                    }
                }
            }

            traverse(directory)
            tracks
        }

    /**
     * Scans multiple directories
     */
    suspend fun scanMultipleDirectories(context: Context, directories: List<File>): List<TrackEntity> =
        withContext(Dispatchers.IO) {
            val tracks = mutableListOf<TrackEntity>()
            val seenPaths = mutableSetOf<String>()
            for (dir in directories) {
                if (dir.exists() && dir.isDirectory) {
                    val dirTracks = scanDirectory(context, dir)
                    for (t in dirTracks) {
                        if (seenPaths.add(t.filePath)) {
                            tracks.add(t)
                        }
                    }
                }
            }
            tracks
        }

    /**
     * Scans SAF DocumentFile tree uri
     */
    suspend fun scanDocumentTree(context: Context, treeUri: Uri): List<TrackEntity> =
        withContext(Dispatchers.IO) {
            val tracks = mutableListOf<TrackEntity>()
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext tracks

            fun traverse(doc: DocumentFile) {
                if (doc.isDirectory) {
                    val name = doc.name ?: ""
                    if (!name.startsWith(".")) {
                        doc.listFiles().forEach { traverse(it) }
                    }
                } else if (doc.isFile) {
                    val name = doc.name ?: return
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (SUPPORTED_EXTENSIONS.contains(ext)) {
                        extractTrackFromDocument(context, doc)?.let { tracks.add(it) }
                    }
                }
            }

            traverse(rootDoc)
            tracks
        }

    /**
     * Scans MediaStore Audio library on device
     */
    suspend fun scanMediaStore(context: Context): List<TrackEntity> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TRACK
        )
        // Allow is_music or audio mime type
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0) OR (${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')"

        val cursor: Cursor? = try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val idCol = it.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durCol = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)
            val nameCol = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val trackCol = it.getColumnIndex(MediaStore.Audio.Media.TRACK)

            while (it.moveToNext()) {
                val path = if (dataCol != -1) it.getString(dataCol) ?: "" else ""
                if (!isInternalSharedStoragePath(path)) continue

                val mediaId = if (idCol != -1) it.getLong(idCol) else 0L
                val rawTitle = if (titleCol != -1) it.getString(titleCol) else null
                val rawArtist = if (artistCol != -1) it.getString(artistCol) else null
                val rawAlbum = if (albumCol != -1) it.getString(albumCol) else null
                val duration = if (durCol != -1) it.getLong(durCol) else 0L
                val name = if (nameCol != -1) it.getString(nameCol) ?: (if (path.isNotEmpty()) File(path).name else "song.mp3") else "song.mp3"
                val trackNum = if (trackCol != -1) it.getInt(trackCol) else 0

                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString()
                val finalPath = if (path.isNotEmpty() && File(path).exists()) path else contentUri

                val cleanName = if (name.contains('.')) name.substringBeforeLast('.') else name
                var title = rawTitle?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: cleanName
                var artist = rawArtist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist"
                val album = rawAlbum?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Album"

                if (artist == "Unknown Artist" && cleanName.contains(" - ")) {
                    val parts = cleanName.split(" - ", limit = 2)
                    if (parts.size == 2) {
                        artist = parts[0].trim()
                        if (title == cleanName) title = parts[1].trim()
                    }
                }

                var coverPath: String? = null
                if (path.isNotEmpty() && File(path).exists()) {
                    coverPath = extractAndCacheArtwork(context, path, "ms_$mediaId")
                }

                tracks.add(
                    TrackEntity(
                        id = "ms_$mediaId",
                        title = title,
                        artist = artist,
                        album = album,
                        albumArtist = artist,
                        durationMs = duration,
                        filePath = finalPath,
                        fileName = name,
                        coverPath = coverPath,
                        trackNumber = trackNum
                    )
                )
            }
        }
        tracks
    }

    private fun extractTrackFromFile(context: Context, file: File): TrackEntity? {
        val fileName = file.name
        val cleanName = file.nameWithoutExtension
        var title = cleanName
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var albumArtist = "Unknown Artist"
        var duration = 0L
        var trackNum = 0
        var discNum = 0
        val trackId = UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString()
        var coverPath: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaAlbumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val trackNumStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val discNumStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)

            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank()) artist = metaArtist
            else if (!metaAlbumArtist.isNullOrBlank()) artist = metaAlbumArtist
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum
            albumArtist = metaAlbumArtist ?: artist
            duration = durationStr?.toLongOrNull() ?: 0L
            trackNum = trackNumStr?.toIntOrNull() ?: 0
            discNum = discNumStr?.toIntOrNull() ?: 0

            coverPath = extractAndCacheArtworkFromRetriever(context, retriever, trackId)
        } catch (e: Exception) {
            // Fallback gracefully without dropping the file
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // Filename fallback: if title/artist not in tags, parse from "Artist - Title"
        if (cleanName.contains(" - ")) {
            val parts = cleanName.split(" - ", limit = 2)
            if (parts.size == 2) {
                if (artist == "Unknown Artist") artist = parts[0].trim()
                if (title == cleanName) title = parts[1].trim()
            }
        }

        return TrackEntity(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            durationMs = duration,
            filePath = file.absolutePath,
            fileName = file.name,
            coverPath = coverPath,
            trackNumber = trackNum,
            discNumber = discNum
        )
    }

    private fun extractTrackFromDocument(context: Context, doc: DocumentFile): TrackEntity? {
        val fileName = doc.name ?: "song.mp3"
        val cleanName = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        var title = cleanName
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var duration = 0L
        val trackId = UUID.nameUUIDFromBytes(doc.uri.toString().toByteArray()).toString()
        var coverPath: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            val pfd = context.contentResolver.openFileDescriptor(doc.uri, "r")
            if (pfd != null) {
                pfd.use {
                    retriever.setDataSource(it.fileDescriptor)
                    val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                    if (!metaTitle.isNullOrBlank()) title = metaTitle
                    if (!metaArtist.isNullOrBlank()) artist = metaArtist
                    if (!metaAlbum.isNullOrBlank()) album = metaAlbum
                    duration = durationStr?.toLongOrNull() ?: 0L

                    coverPath = extractAndCacheArtworkFromRetriever(context, retriever, trackId)
                }
            }
        } catch (e: Exception) {
            // Fallback gracefully
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        if (cleanName.contains(" - ")) {
            val parts = cleanName.split(" - ", limit = 2)
            if (parts.size == 2) {
                if (artist == "Unknown Artist") artist = parts[0].trim()
                if (title == cleanName) title = parts[1].trim()
            }
        }

        return TrackEntity(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            filePath = doc.uri.toString(),
            fileName = fileName,
            coverPath = coverPath
        )
    }

    private fun extractAndCacheArtwork(context: Context, filePath: String, id: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            extractAndCacheArtworkFromRetriever(context, retriever, id)
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun extractAndCacheArtworkFromRetriever(
        context: Context,
        retriever: MediaMetadataRetriever,
        id: String
    ): String? {
        val artBytes = retriever.embeddedPicture ?: return null
        val coversDir = File(context.filesDir, "cached_covers").apply { mkdirs() }
        val targetFile = File(coversDir, "art_$id.jpg")
        if (targetFile.exists() && targetFile.length() > 0) return targetFile.absolutePath

        return try {
            FileOutputStream(targetFile).use { out ->
                out.write(artBytes)
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

