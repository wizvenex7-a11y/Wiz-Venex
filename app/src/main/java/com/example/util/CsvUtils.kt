package com.example.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

data class CsvTrackRow(
    val trackUri: String = "",
    val trackName: String = "",
    val artistNames: String = "",
    val albumName: String = "",
    val albumArtistNames: String = "",
    val albumReleaseDate: String = "",
    val albumImageUrl: String = "",
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val durationMs: Long = 0L,
    val explicit: Boolean = false,
    val addedAt: String = ""
)

object CsvUtils {

    /**
     * Parses an RFC 4180 CSV String into a list of CsvTrackRow
     */
    fun parseSpotifyCsv(csvString: String): List<CsvTrackRow> {
        return parseSpotifyCsv(java.io.ByteArrayInputStream(csvString.toByteArray(Charsets.UTF_8)))
    }

    /**
     * Parses an RFC 4180 CSV InputStream into a list of CsvTrackRow
     */
    fun parseSpotifyCsv(inputStream: InputStream): List<CsvTrackRow> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val rows = parseCsvRows(reader)
        if (rows.isEmpty()) return emptyList()

        val header = rows[0].map { it.trim().lowercase() }
        val headerMap = header.withIndex().associate { it.value to it.index }

        val trackNameIdx = findColumnIndex(headerMap, "track name", "title", "song name", "name")
        val artistNamesIdx = findColumnIndex(headerMap, "artist name(s)", "artist name", "artist", "artists")
        val albumNameIdx = findColumnIndex(headerMap, "album name", "album")
        val albumArtistIdx = findColumnIndex(headerMap, "album artist name(s)", "album artist")
        val durationIdx = findColumnIndex(headerMap, "track duration (ms)", "duration (ms)", "duration_ms", "duration")
        val trackUriIdx = findColumnIndex(headerMap, "track uri", "uri")
        val discNumIdx = findColumnIndex(headerMap, "disc number", "disc")
        val trackNumIdx = findColumnIndex(headerMap, "track number", "track")
        val explicitIdx = findColumnIndex(headerMap, "explicit")
        val addedAtIdx = findColumnIndex(headerMap, "added at")
        val imageUrlIdx = findColumnIndex(headerMap, "album image url", "image url", "image")

        val result = mutableListOf<CsvTrackRow>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty() || (row.size == 1 && row[0].isBlank())) continue

            var trackName = NormalizationUtils.sanitizeText(getCol(row, trackNameIdx))
            var artist = NormalizationUtils.sanitizeText(getCol(row, artistNamesIdx))
            var album = NormalizationUtils.sanitizeText(getCol(row, albumNameIdx))
            val albumArtist = NormalizationUtils.sanitizeText(getCol(row, albumArtistIdx))
            val rawDuration = getCol(row, durationIdx)
            var durationMs = parseDuration(rawDuration)
            val uri = getCol(row, trackUriIdx)
            val disc = getCol(row, discNumIdx).toIntOrNull() ?: 0
            val track = getCol(row, trackNumIdx).toIntOrNull() ?: 0
            val explicit = getCol(row, explicitIdx).equals("true", ignoreCase = true)
            val addedAt = getCol(row, addedAtIdx)
            val imageUrl = getCol(row, imageUrlIdx)

            // If URI is spotify:local:<artist>:<album>:<title>:<seconds>
            if (uri.startsWith("spotify:local:")) {
                val localParsed = parseSpotifyLocalUri(uri)
                if (localParsed != null) {
                    if (trackName.isBlank()) trackName = localParsed.title
                    if (artist.isBlank()) artist = localParsed.artist
                    if (album.isBlank()) album = localParsed.album
                    if (durationMs <= 0L && localParsed.durationMs > 0L) durationMs = localParsed.durationMs
                }
            }

            if (trackName.isBlank()) continue

            result.add(
                CsvTrackRow(
                    trackUri = uri,
                    trackName = trackName,
                    artistNames = artist,
                    albumName = album,
                    albumArtistNames = albumArtist,
                    albumImageUrl = imageUrl,
                    discNumber = disc,
                    trackNumber = track,
                    durationMs = durationMs,
                    explicit = explicit,
                    addedAt = addedAt
                )
            )
        }
        return result
    }

    data class SpotifyLocalUriData(
        val artist: String,
        val album: String,
        val title: String,
        val durationMs: Long
    )

    /**
     * Parses Spotify local URI format:
     * spotify:local:Artist:Album:Title:DurationInSeconds
     */
    fun parseSpotifyLocalUri(uri: String): SpotifyLocalUriData? {
        if (!uri.startsWith("spotify:local:")) return null
        val parts = uri.removePrefix("spotify:local:").split(":")
        if (parts.size < 4) return null
        return try {
            val rawArtist = parts[0]
            val rawAlbum = parts[1]
            val rawTitle = parts[2]
            val rawDurationSec = parts[3]

            val artist = java.net.URLDecoder.decode(rawArtist.replace("+", "%20"), "UTF-8")
            val album = java.net.URLDecoder.decode(rawAlbum.replace("+", "%20"), "UTF-8")
            val title = java.net.URLDecoder.decode(rawTitle.replace("+", "%20"), "UTF-8")
            val durationSec = rawDurationSec.toLongOrNull() ?: 0L
            val durationMs = durationSec * 1000L

            SpotifyLocalUriData(artist, album, title, durationMs)
        } catch (e: Exception) {
            null
        }
    }

    private fun findColumnIndex(headerMap: Map<String, Int>, vararg candidates: String): Int {
        for (c in candidates) {
            headerMap[c]?.let { return it }
        }
        // Partial match
        for (c in candidates) {
            headerMap.entries.firstOrNull { it.key.contains(c) }?.let { return it.value }
        }
        return -1
    }

    private fun getCol(row: List<String>, idx: Int): String {
        return if (idx in row.indices) row[idx].trim() else ""
    }

    private fun parseDuration(raw: String): Long {
        if (raw.isBlank()) return 0L
        raw.toLongOrNull()?.let { return it }
        raw.toDoubleOrNull()?.let { return (it * 1000).toLong() }
        // If mm:ss
        if (raw.contains(":")) {
            val parts = raw.split(":")
            if (parts.size == 2) {
                val m = parts[0].trim().toLongOrNull() ?: 0L
                val s = parts[1].trim().toDoubleOrNull() ?: 0.0
                return ((m * 60 + s) * 1000).toLong()
            }
        }
        return 0L
    }

    /**
     * Standard RFC 4180 CSV tokenizer supporting quotes, newlines in quotes, and double-quote escaping
     */
    fun parseCsvRows(reader: BufferedReader): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = java.lang.StringBuilder()
        var inQuotes = false

        var intChar = reader.read()
        while (intChar != -1) {
            val c = intChar.toChar()
            when (c) {
                '"' -> {
                    if (inQuotes) {
                        // Check if next char is also quote (escaped quote)
                        reader.mark(1)
                        val next = reader.read()
                        if (next != -1 && next.toChar() == '"') {
                            currentField.append('"')
                        } else {
                            inQuotes = false
                            reader.reset()
                        }
                    } else {
                        inQuotes = true
                    }
                }
                ',' -> {
                    if (inQuotes) {
                        currentField.append(c)
                    } else {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                    }
                }
                '\r' -> {
                    // Ignore \r or handle \r\n
                }
                '\n' -> {
                    if (inQuotes) {
                        currentField.append(c)
                    } else {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                }
                else -> {
                    currentField.append(c)
                }
            }
            intChar = reader.read()
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            rows.add(currentRow)
        }

        return rows
    }

    /**
     * Exports tracks into Spotify-format CSV string
     */
    fun exportToCsv(
        tracks: List<CsvTrackRow>
    ): String {
        val sb = StringBuilder()
        val headers = listOf(
            "Track Name",
            "Artist Name(s)",
            "Album Name",
            "Album Artist Name(s)",
            "Album Release Date",
            "Disc Number",
            "Track Number",
            "Track Duration (ms)",
            "Explicit",
            "Added At"
        )
        sb.append(headers.joinToString(",") { escapeCsv(it) }).append("\n")

        for (t in tracks) {
            val cols = listOf(
                t.trackName,
                t.artistNames,
                t.albumName,
                t.albumArtistNames,
                t.albumReleaseDate,
                t.discNumber.toString(),
                t.trackNumber.toString(),
                t.durationMs.toString(),
                t.explicit.toString(),
                t.addedAt
            )
            sb.append(cols.joinToString(",") { escapeCsv(it) }).append("\n")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
