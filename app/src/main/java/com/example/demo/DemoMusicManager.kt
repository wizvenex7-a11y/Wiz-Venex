package com.example.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.example.data.model.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object DemoMusicManager {

    data class DemoTrackSpec(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val fileName: String,
        val bgColorHex: String,
        val accentColorHex: String,
        val emoji: String
    )

    val SAMPLE_SPECS = listOf(
        DemoTrackSpec(
            title = "The Fate Of Ophelia Afrobeats",
            artist = "Afrospin",
            album = "Ophelia EP",
            durationMs = 231000L,
            fileName = "The Fate Of Ophelia Afrobeats.wav",
            bgColorHex = "#E50914",
            accentColorHex = "#FFA000",
            emoji = "🥁"
        ),
        DemoTrackSpec(
            title = "Three Seconds To Goodbye",
            artist = "ZionRay",
            album = "Three Seconds To Goodbye",
            durationMs = 416000L,
            fileName = "Three Seconds To Goodbye.wav",
            bgColorHex = "#4A148C",
            accentColorHex = "#AB47BC",
            emoji = "🌌"
        ),
        DemoTrackSpec(
            title = "Single Again",
            artist = "Chonde Studios",
            album = "Chonde Beats Vol 1",
            durationMs = 24000L,
            fileName = "Single Again.wav",
            bgColorHex = "#004D40",
            accentColorHex = "#1DE9B6",
            emoji = "🔥"
        ),
        DemoTrackSpec(
            title = "Best Moments of 2025 Song",
            artist = "The Lyrical Lanterns",
            album = "Best Moments of 2025 Song",
            durationMs = 230000L,
            fileName = "Best Moments of 2025 Song.wav",
            bgColorHex = "#E65100",
            accentColorHex = "#FFD54F",
            emoji = "✨"
        ),
        DemoTrackSpec(
            title = "All In My Head - Amapiano Version",
            artist = "Weckly Jay",
            album = "All In My Head (Amapiano Version)",
            durationMs = 184000L,
            fileName = "All In My Head - Amapiano Version.wav",
            bgColorHex = "#1A237E",
            accentColorHex = "#536DFE",
            emoji = "🎹"
        ),
        DemoTrackSpec(
            title = "Addicted to You",
            artist = "Orion7",
            album = "Orion Night Sessions",
            durationMs = 30000L,
            fileName = "Addicted to You.wav",
            bgColorHex = "#311B92",
            accentColorHex = "#7C4DFF",
            emoji = "💫"
        ),
        DemoTrackSpec(
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200100L,
            fileName = "Blinding Lights.wav",
            bgColorHex = "#880E4F",
            accentColorHex = "#FF4081",
            emoji = "🌃"
        ),
        DemoTrackSpec(
            title = "Save Your Tears",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 215000L,
            fileName = "Save Your Tears.wav",
            bgColorHex = "#880E4F",
            accentColorHex = "#FF80AB",
            emoji = "💧"
        )
    )

    /**
     * Initializes demo music files in the internal Music folder if not already generated
     */
    suspend fun generateDemoLibrary(context: Context): List<TrackEntity> = withContext(Dispatchers.IO) {
        val musicDir = File(context.filesDir, "DemoMusic").apply { mkdirs() }
        val coversDir = File(context.filesDir, "cached_covers").apply { mkdirs() }
        val tracks = mutableListOf<TrackEntity>()

        for (spec in SAMPLE_SPECS) {
            val audioFile = File(musicDir, spec.fileName)
            if (!audioFile.exists()) {
                createSyntheticWavFile(audioFile, 10.0) // 10s playable sample audio
            }

            // Create distinct album cover artwork
            val coverFile = File(coversDir, "demo_cover_${spec.album.hashCode()}.jpg")
            if (!coverFile.exists()) {
                val coverBmp = generateCoverArtBitmap(spec)
                FileOutputStream(coverFile).use { out ->
                    coverBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }

            val trackId = UUID.nameUUIDFromBytes(spec.fileName.toByteArray()).toString()
            tracks.add(
                TrackEntity(
                    id = trackId,
                    title = spec.title,
                    artist = spec.artist,
                    album = spec.album,
                    albumArtist = spec.artist,
                    durationMs = spec.durationMs,
                    filePath = audioFile.absolutePath,
                    fileName = spec.fileName,
                    coverPath = coverFile.absolutePath,
                    trackNumber = 1
                )
            )
        }

        // Also write sample CSV files
        generateSampleCsvFiles(context)

        tracks
    }

    private fun generateCoverArtBitmap(spec: DemoTrackSpec): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = Color.parseColor(spec.bgColorHex)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Draw decorative circle
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(spec.accentColorHex)
            alpha = 70
        }
        canvas.drawCircle(size * 0.75f, size * 0.25f, size * 0.35f, circlePaint)

        // Draw emoji / symbol
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 100f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(spec.emoji, size / 2f, size / 2f - 20f, emojiPaint)

        // Draw Album Title
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }

        val truncatedAlbum = if (spec.album.length > 20) spec.album.take(18) + "…" else spec.album
        canvas.drawText(truncatedAlbum, size / 2f, size * 0.75f, textPaint)
        canvas.drawText(spec.artist, size / 2f, size * 0.75f + 38f, subPaint)

        return bitmap
    }

    /**
     * Creates a valid, audible 44.1kHz mono WAV file
     */
    private fun createSyntheticWavFile(file: File, durationSeconds: Double) {
        val sampleRate = 44100
        val totalSamples = (durationSeconds * sampleRate).toInt()
        val pcmDataSize = totalSamples * 2 // 16-bit PCM = 2 bytes per sample
        val totalFileSize = 36 + pcmDataSize

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(totalFileSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size
            putShort(1.toShort()) // PCM format
            putShort(1.toShort()) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // ByteRate
            putShort(2.toShort()) // BlockAlign
            putShort(16.toShort()) // BitsPerSample
            put("data".toByteArray())
            putInt(pcmDataSize)
        }

        FileOutputStream(file).use { out ->
            out.write(header.array())
            val buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
            val freq = 440.0 // A4 note
            for (i in 0 until totalSamples) {
                val angle = 2.0 * Math.PI * i * freq / sampleRate
                val sampleVal = (Math.sin(angle) * 12000).toInt().toShort()
                buffer.putShort(sampleVal)
                if (!buffer.hasRemaining()) {
                    out.write(buffer.array())
                    buffer.clear()
                }
            }
            if (buffer.position() > 0) {
                out.write(buffer.array(), 0, buffer.position())
            }
        }
    }

    /**
     * Generates sample CSV files on device for instant import testing
     */
    fun generateSampleCsvFiles(context: Context): List<File> {
        val playlistsDir = File(context.filesDir, "SamplePlaylists").apply { mkdirs() }
        val sampleCsv1 = File(playlistsDir, "instrumental_-music🎶_.csv")
        val sampleCsv2 = File(playlistsDir, "Night_Drive.csv")
        val sampleCsv3 = File(playlistsDir, "My_Favorites_❤️.csv")

        val csv1Content = """
"Track URI","Track Name","Artist URI(s)","Artist Name(s)","Album URI","Album Name","Album Artist URI(s)","Album Artist Name(s)","Album Release Date","Album Image URL","Disc Number","Track Number","Track Duration (ms)","Track Preview URL","Explicit","Popularity","ISRC","Added By","Added At"
"spotify:local:Afrospin::The+Fate+Of+Ophelia+Afrobeats:231","The Fate Of Ophelia Afrobeats","","Afrospin","","","","","","","0","0","231000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-04-13T16:47:23Z"
"spotify:local:ZionRay:Three+Seconds+To+Goodbye:Three+Seconds+To+Goodbye:416","Three Seconds To Goodbye","","ZionRay","","Three Seconds To Goodbye","","","","","0","0","416000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-04-13T16:47:23Z"
"spotify:local:Chonde+Studios::Single+Again:24","Single Again","","Chonde Studios","","","","","","","0","0","24000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-04-13T16:47:23Z"
"spotify:local:The+Lyrical+Lanterns:Best+Moments+of+2025+Song:Best+Moments+of+2025+Song:230","Best Moments of 2025 Song","","The Lyrical Lanterns","","Best Moments of 2025 Song","","","","","0","0","230000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-04-13T16:47:23Z"
"spotify:local:Weckly+Jay:All+In+My+Head+%28Amapiano+Version%29:All+In+My+Head+-+Amapiano+Version:184","All In My Head - Amapiano Version","","Weckly Jay","","All In My Head (Amapiano Version)","","","","","0","0","184000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-04-13T16:47:23Z"
"spotify:local:Orion7::Addicted+to+You:30","Addicted to You","","Orion7","","","","","","","0","0","30000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-08-28T22:32:54Z"
"spotify:local:Unknown::Retrograde+Synth:195","Retrograde Synth","","Unknown Stars","","Cosmic Journeys","","","","","0","0","195000","","false","0","","spotify:user:test","2026-08-28T22:32:54Z"
""".trimIndent()

        val csv2Content = """
"Track URI","Track Name","Artist URI(s)","Artist Name(s)","Album URI","Album Name","Album Artist URI(s)","Album Artist Name(s)","Album Release Date","Album Image URL","Disc Number","Track Number","Track Duration (ms)","Track Preview URL","Explicit","Popularity","ISRC","Added By","Added At"
"spotify:local:The+Weeknd::Blinding+Lights:200","Blinding Lights","","The Weeknd","","After Hours","","","","","0","0","200000","","false","90","","spotify:user:test","2026-01-10T12:00:00Z"
"spotify:local:The+Weeknd::Save+Your+Tears:215","Save Your Tears","","The Weeknd","","After Hours","","","","","0","0","215000","","false","88","","spotify:user:test","2026-01-10T12:05:00Z"
"spotify:local:ZionRay::Three+Seconds+To+Goodbye:416","Three Seconds To Goodbye","","ZionRay","","Three Seconds To Goodbye","","","","","0","0","416000","","false","0","","spotify:user:test","2026-01-10T12:10:00Z"
""".trimIndent()

        val csv3Content = """
"Track URI","Track Name","Artist URI(s)","Artist Name(s)","Album URI","Album Name","Album Artist URI(s)","Album Artist Name(s)","Album Release Date","Album Image URL","Disc Number","Track Number","Track Duration (ms)","Track Preview URL","Explicit","Popularity","ISRC","Added By","Added At"
"spotify:local:Afrospin::The+Fate+Of+Ophelia+Afrobeats:231","The Fate Of Ophelia Afrobeats","","Afrospin","","Ophelia EP","","","","","0","0","231000","","false","0","","spotify:user:test","2026-02-14T10:00:00Z"
"spotify:local:Orion7::Addicted+to+You:30","Addicted to You","","Orion7","","Orion Night Sessions","","","","","0","0","30000","","false","0","","spotify:user:test","2026-02-14T10:05:00Z"
""".trimIndent()

        sampleCsv1.writeText(csv1Content)
        sampleCsv2.writeText(csv2Content)
        sampleCsv3.writeText(csv3Content)

        return listOf(sampleCsv1, sampleCsv2, sampleCsv3)
    }
}
