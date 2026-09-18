package com.example.util

import java.text.Normalizer
import java.util.Locale

object NormalizationUtils {

    /**
     * Sanitizes playlist filename to clean display name according to the spec:
     * e.g., "instrumental_-music🎶_.csv" -> "instrumental - music 🎶"
     * "Night_Drive.csv" -> "Night Drive"
     * "My_Favorites_❤️.csv" -> "My Favorites ❤️"
     */
    fun sanitizePlaylistName(fileName: String): String {
        var name = fileName.trim()

        // 1. Remove .csv or .txt extension (case-insensitive)
        if (name.endsWith(".csv", ignoreCase = true)) {
            name = name.substring(0, name.length - 4)
        } else if (name.endsWith(".txt", ignoreCase = true)) {
            name = name.substring(0, name.length - 4)
        }

        // 2. Replace '_' with spaces
        name = name.replace('_', ' ')

        // 3. Normalize dash spacing (e.g. "-music" or " -music" or " - music" -> " - ")
        name = name.replace(Regex("\\s*-\\s*"), " - ")

        // 4. Ensure emojis/symbols are separated from adjacent letters with a space
        name = name.replace(Regex("([\\p{L}\\p{N}])([\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}])"), "$1 $2")
        name = name.replace(Regex("([\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}])([\\p{L}\\p{N}])"), "$1 $2")

        // 5. Collapse multiple consecutive spaces
        name = name.replace(Regex("\\s+"), " ")

        // 5. Remove unnecessary leading / trailing artifacts (like leading/trailing spaces, trailing dashes or dots)
        name = name.trim()
        name = name.trim { it <= ' ' || it == '-' || it == '.' || it == '_' }

        // Clean up spaces again in case trimming exposed any
        name = name.trim()

        return if (name.isBlank()) "Imported Playlist" else name
    }

    /**
     * Converts raw text (titles, artist names, album names, playlist names, folder names)
     * into Title Case for UI preview/display ONLY.
     * Original names stored in metadata, database, CSV, TXT, JSON, or backup files remain unchanged.
     *
     * Example:
     * instrumental_-_music_🎶_.csv -> Instrumental - Music 🎶
     */
    fun toTitleCaseDisplay(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var text = input.trim()

        // 1. Strip common extensions if present in preview
        val extensions = listOf(".csv", ".txt", ".mp3", ".flac", ".m4a", ".wav", ".aac", ".ogg")
        for (ext in extensions) {
            if (text.endsWith(ext, ignoreCase = true)) {
                text = text.substring(0, text.length - ext.length)
            }
        }

        // 2. Replace '_' with spaces
        text = text.replace('_', ' ')

        // 3. Normalize dash spacing
        text = text.replace(Regex("\\s*-\\s*"), " - ")

        // 4. Collapse multiple spaces
        text = text.replace(Regex("\\s+"), " ").trim()

        if (text.isBlank()) return ""

        // 5. Title case words while preserving emojis and special characters
        val words = text.split(" ")
        return words.joinToString(" ") { word ->
            if (word.isBlank()) ""
            else if (word.contains("-")) {
                word.split("-").joinToString("-") { sub ->
                    sub.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            } else {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
    }

    /**
     * Normalizes a song title or artist string for accurate matching:
     * - Unicode NFC normalization
     * - Lowercase
     * - Removes invisible / zero-width characters
     * - Normalizes apostrophes (' ’ ` ‘ -> ')
     * - Normalizes dashes (– — ― − -> -)
     * - Collapses whitespace
     * - Preserves emojis, foreign scripts (Arabic, Chinese, Japanese, Cyrillic, Korean, French accents)
     */
    fun normalizeText(input: String?): String {
        if (input.isNullOrBlank()) return ""

        // Unicode NFC normalization
        var normalized = Normalizer.normalize(input, Normalizer.Form.NFC)

        // Remove invisible / control / zero-width characters (\u200B - \u200F, \uFEFF, \p{Cntrl})
        normalized = normalized.replace(Regex("[\\p{Cntrl}\\u200B-\\u200F\\uFEFF]"), "")

        // Normalize apostrophes & quotes
        normalized = normalized.replace(Regex("[’‘`´]"), "'")

        // Normalize dash variations
        normalized = normalized.replace(Regex("[–—―−]"), "-")

        // Lowercase using root locale
        normalized = normalized.lowercase(Locale.ROOT)

        // Trim and collapse whitespace
        normalized = normalized.replace(Regex("\\s+"), " ").trim()

        return normalized
    }

    /**
     * Secondary stripped normalization that removes surrounding punctuation or feature tags
     * like "(feat. ...)", "(remix)", etc., to help level 2 title matching.
     */
    fun cleanSongTitle(title: String): String {
        var cleaned = normalizeText(title)
        // Strip known Spotify suffixes
        cleaned = cleaned.replace(Regex("(?i)\\s*-\\s*(remastered|live|mono|stereo|deluxe|anniversary|radio edit|original mix).*"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*\\((official audio|official video|official music video|music video|audio|video|lyric video|lyrics|live|remastered).*?\\)"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*\\[(official audio|official video|official music video|music video|audio|video|lyric video|lyrics|live|remastered).*?\\]"), "")
        // Remove feat tags
        cleaned = cleaned.replace(Regex("(?i)\\s*\\((feat|ft)\\.?.*?\\)"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*\\[(feat|ft)\\.?.*?\\]"), "")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()
        return cleaned
    }

    /**
     * Replicates Python clean(text):
     * Only remove illegal filename characters and backslashes
     */
    fun cleanFilename(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.replace(Regex("[\\\\/*?:\"<>|]"), "")
    }

    /**
     * Replicates Python sanitize_text(text):
     * Filters out hidden control and embedding characters (category 'C')
     * Removes backslashes; preserves original punctuation and spaces.
     */
    fun sanitizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val sb = StringBuilder()
        for (ch in text) {
            val type = Character.getType(ch)
            if (type != Character.CONTROL.toInt() &&
                type != Character.FORMAT.toInt() &&
                type != Character.PRIVATE_USE.toInt() &&
                type != Character.SURROGATE.toInt() &&
                type != Character.UNASSIGNED.toInt()
            ) {
                if (ch != '\\') {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    /**
     * Replicates Python normalize(text):
     * Strip spaces and all dash variations for robust matching:
     * text.lower().replace('–', '').replace('—', '').replace('-', '')
     * and strip all whitespace
     */
    fun pythonNormalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var t = sanitizeText(text)
        t = t.replace(Regex("[’‘`´']"), "")
        t = t.replace(Regex("[()\\[\\],_.:;\"!~*^&]"), "")
        t = t.replace("–", "").replace("—", "").replace("―", "").replace("−", "").replace("-", "")
        t = t.lowercase(Locale.ROOT)
        return t.replace(Regex("\\s+"), "")
    }

    /**
     * Replicates Python song_key(artist, title):
     * normalize(artist) + "|" + normalize(title)
     */
    fun songKey(artist: String?, title: String?): String {
        return pythonNormalize(artist) + "|" + pythonNormalize(title)
    }

    /**
     * Formats milliseconds into mm:ss or hh:mm:ss
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remainingMins = minutes % 60
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remainingMins, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }
}

val String?.toTitleCaseDisplay: String
    get() = NormalizationUtils.toTitleCaseDisplay(this)

