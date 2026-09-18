package com.example.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class TxtTrackRow(
    val artist: String,
    val title: String,
    val durationMs: Long
)

object TxtPlaylistUtils {

    /**
     * Parses a TXT stream formatted with lines of <artist>:<title>:<milliseconds>
     * Example: "The Weeknd:Blinding Lights:200040"
     */
    fun parseTxtPlaylist(inputStream: InputStream): List<TxtTrackRow> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val rows = mutableListOf<TxtTrackRow>()

        reader.useLines { lines ->
            for (rawLine in lines) {
                val parsed = parseLine(rawLine)
                if (parsed != null) {
                    rows.add(parsed)
                }
            }
        }

        return rows
    }

    /**
     * Parses a single line of playlist text.
     * Supports:
     * - "Artist:Title:DurationMs"
     * - "Artist:Title"
     * - "Artist - Title:DurationMs"
     * - "Artist,Title,DurationMs"
     * - "Artist\tTitle\tDurationMs"
     */
    fun parseLine(line: String): TxtTrackRow? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("//")) return null

        // 1. Try colon separated
        if (trimmed.contains(':')) {
            val parts = trimmed.split(':')
            if (parts.size >= 3) {
                val durationMs = parts.last().trim().toLongOrNull() ?: 0L
                val artist = parts[0].trim()
                val title = parts.subList(1, parts.size - 1).joinToString(":").trim()
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return TxtTrackRow(artist = artist, title = title, durationMs = durationMs)
                }
            } else if (parts.size == 2) {
                val durationMs = parts[1].trim().toLongOrNull()
                if (durationMs != null) {
                    val combined = parts[0].trim()
                    if (combined.contains(" - ")) {
                        val sub = combined.split(" - ", limit = 2)
                        return TxtTrackRow(artist = sub[0].trim(), title = sub[1].trim(), durationMs = durationMs)
                    }
                } else {
                    return TxtTrackRow(artist = parts[0].trim(), title = parts[1].trim(), durationMs = 0L)
                }
            }
        }

        // 2. Try comma separated
        if (trimmed.contains(',')) {
            val parts = trimmed.split(',')
            if (parts.size >= 3) {
                val durationMs = parts.last().trim().toLongOrNull() ?: 0L
                val artist = parts[0].trim().removeSurrounding("\"")
                val title = parts.subList(1, parts.size - 1).joinToString(",").trim().removeSurrounding("\"")
                if (artist.isNotBlank() && title.isNotBlank()) {
                    return TxtTrackRow(artist = artist, title = title, durationMs = durationMs)
                }
            }
        }

        // 3. Try hyphen separated "Artist - Title"
        if (trimmed.contains(" - ")) {
            val parts = trimmed.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return TxtTrackRow(artist = parts[0].trim(), title = parts[1].trim(), durationMs = 0L)
            }
        }

        return null
    }

    /**
     * Serializes a list of songs into the standard TXT format (<artist>:<title>:<milliseconds>)
     */
    fun formatTracksAsTxt(tracks: List<com.example.data.model.TrackEntity>): String {
        return tracks.joinToString("\n") { track ->
            "${track.artist}:${track.title}:${track.durationMs}"
        }
    }
}
