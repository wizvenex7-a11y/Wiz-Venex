package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.abs

object ColorExtractor {
    private val colorCache = mutableMapOf<String, Color>()

    /**
     * Extracts the dominant vibrant color for a song based on its album artwork,
     * or computes a deterministic rich saturated color based on title & artist.
     */
    fun getDominantColor(coverPath: String?, title: String = "", artist: String = ""): Color {
        val cacheKey = if (!coverPath.isNullOrBlank()) coverPath else "$title|$artist"
        colorCache[cacheKey]?.let { return it }

        if (!coverPath.isNullOrBlank()) {
            val file = File(coverPath)
            if (file.exists()) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val sampleSize = (bounds.outWidth / 32).coerceAtLeast(1)
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                    if (bitmap != null) {
                        val color = extractProminentColorFromBitmap(bitmap)
                        bitmap.recycle()
                        colorCache[cacheKey] = color
                        return color
                    }
                } catch (_: Exception) {
                }
            }
        }

        val fallback = generateVibrantColorFromText(title + artist)
        colorCache[cacheKey] = fallback
        return fallback
    }

    private fun extractProminentColorFromBitmap(bitmap: Bitmap): Color {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var bestColor = 0
        var highestScore = -1f

        val hsv = FloatArray(3)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val saturation = hsv[1]
            val value = hsv[2]

            // Filter out extreme blacks, whites, and low saturation grays
            if (value > 0.18f && value < 0.92f && saturation > 0.28f) {
                // Score: prioritize high saturation and balanced brightness
                val score = saturation * 2.2f + (1f - abs(value - 0.55f))
                if (score > highestScore) {
                    highestScore = score
                    bestColor = pixel
                }
            }
        }

        return if (highestScore > 0f) {
            Color(bestColor)
        } else {
            Color(0xFF3E3E3E)
        }
    }

    fun generateVibrantColorFromText(text: String): Color {
        if (text.isBlank()) return Color(0xFF1DB954)
        val hash = abs(text.hashCode())
        val hue = (hash % 360).toFloat()
        // Choose vivid saturation and moderate brightness for rich player background
        val hsv = floatArrayOf(hue, 0.72f, 0.60f)
        val intColor = android.graphics.Color.HSVToColor(hsv)
        return Color(intColor)
    }
}
