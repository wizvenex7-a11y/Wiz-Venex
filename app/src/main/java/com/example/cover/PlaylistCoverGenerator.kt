package com.example.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.data.model.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PlaylistCoverGenerator {

    /**
     * Generates and caches a distinct-album mosaic cover for a playlist
     */
    suspend fun generateCoverForPlaylist(
        context: Context,
        playlistId: String,
        tracks: List<TrackEntity>,
        forceRegenerate: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        val coversDir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val targetFile = File(coversDir, "cover_$playlistId.jpg")

        if (targetFile.exists() && targetFile.length() > 0 && !forceRegenerate) {
            return@withContext targetFile.absolutePath
        }

        // Gather distinct album covers
        val distinctCoverPaths = mutableListOf<String>()
        val seenAlbums = mutableSetOf<String>()

        for (track in tracks) {
            val albumKey = track.album.ifBlank { track.artist }
            if (track.coverPath != null && File(track.coverPath).exists()) {
                if (albumKey.isNotBlank() && !seenAlbums.contains(albumKey)) {
                    seenAlbums.add(albumKey)
                    distinctCoverPaths.add(track.coverPath)
                    if (distinctCoverPaths.size == 4) break
                }
            }
        }

        if (distinctCoverPaths.isEmpty()) {
            // Generate stylish dark music placeholder
            val placeholder = createDarkMusicPlaceholder()
            saveBitmap(placeholder, targetFile)
            return@withContext targetFile.absolutePath
        }

        val outputSize = 512
        val combinedBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)
        canvas.drawColor(Color.parseColor("#121212"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        when (distinctCoverPaths.size) {
            1 -> {
                // Single cover
                val b = loadBitmap(distinctCoverPaths[0])
                if (b != null) {
                    canvas.drawBitmap(b, null, Rect(0, 0, outputSize, outputSize), paint)
                }
            }
            2 -> {
                // 2-panel cover (split vertically)
                val b1 = loadBitmap(distinctCoverPaths[0])
                val b2 = loadBitmap(distinctCoverPaths[1])
                val halfW = outputSize / 2
                if (b1 != null) canvas.drawBitmap(b1, null, Rect(0, 0, halfW, outputSize), paint)
                if (b2 != null) canvas.drawBitmap(b2, null, Rect(halfW, 0, outputSize, outputSize), paint)
            }
            3 -> {
                // 3-panel cover: 1 full width top, 2 split bottom
                val b1 = loadBitmap(distinctCoverPaths[0])
                val b2 = loadBitmap(distinctCoverPaths[1])
                val b3 = loadBitmap(distinctCoverPaths[2])
                val halfH = outputSize / 2
                val halfW = outputSize / 2
                if (b1 != null) canvas.drawBitmap(b1, null, Rect(0, 0, outputSize, halfH), paint)
                if (b2 != null) canvas.drawBitmap(b2, null, Rect(0, halfH, halfW, outputSize), paint)
                if (b3 != null) canvas.drawBitmap(b3, null, Rect(halfW, halfH, outputSize, outputSize), paint)
            }
            else -> {
                // 4 distinct covers: 2x2 grid
                val b1 = loadBitmap(distinctCoverPaths[0])
                val b2 = loadBitmap(distinctCoverPaths[1])
                val b3 = loadBitmap(distinctCoverPaths[2])
                val b4 = loadBitmap(distinctCoverPaths[3])
                val half = outputSize / 2
                if (b1 != null) canvas.drawBitmap(b1, null, Rect(0, 0, half, half), paint)
                if (b2 != null) canvas.drawBitmap(b2, null, Rect(half, 0, outputSize, half), paint)
                if (b3 != null) canvas.drawBitmap(b3, null, Rect(0, half, half, outputSize), paint)
                if (b4 != null) canvas.drawBitmap(b4, null, Rect(half, half, outputSize, outputSize), paint)
            }
        }

        saveBitmap(combinedBitmap, targetFile)
        return@withContext targetFile.absolutePath
    }

    private fun loadBitmap(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmap(bitmap: Bitmap, targetFile: File) {
        try {
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createDarkMusicPlaceholder(): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor("#181818"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1ED760")
            textSize = 72f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🎵", size / 2f, size / 2f + 24f, paint)
        return bmp
    }
}
