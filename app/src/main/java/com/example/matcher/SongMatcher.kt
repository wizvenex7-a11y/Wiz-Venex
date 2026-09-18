package com.example.matcher

import com.example.data.model.TrackEntity
import com.example.util.CsvTrackRow
import com.example.util.NormalizationUtils
import com.example.util.TxtTrackRow
import kotlin.math.abs

sealed class MatchResult {
    data class SingleMatch(val track: TrackEntity, val confidence: MatchConfidence) : MatchResult()
    data class AmbiguousMatch(val candidates: List<TrackEntity>) : MatchResult()
    object NoMatch : MatchResult()
}

enum class MatchConfidence {
    EXACT_PATH,
    FILENAME,
    EXACT_TITLE_ARTIST_DURATION,
    TITLE_ARTIST_ALBUM_DURATION,
    TITLE_ARTIST_TOLERANT_DURATION,
    TITLE_ARTIST_ONLY
}

/**
 * High-performance indexed track matcher for fast batch CSV & TXT playlist imports.
 */
class FastTrackMatcher(localTracks: List<TrackEntity>) {
    private val poolTracks = localTracks.filterNot {
        it.id.startsWith("demo_") || it.filePath.contains("demo_music") || it.filePath.contains("sample_data")
    }

    private val exactKeyMap = HashMap<String, MutableList<TrackEntity>>()
    private val normTitleMap = HashMap<String, MutableList<TrackEntity>>()
    private val cleanTitleMap = HashMap<String, MutableList<TrackEntity>>()
    private val filenameMap = HashMap<String, MutableList<TrackEntity>>()
    private val pathMap = HashMap<String, TrackEntity>()

    init {
        for (track in poolTracks) {
            val key = NormalizationUtils.songKey(track.artist, track.title)
            exactKeyMap.getOrPut(key) { mutableListOf() }.add(track)

            val normTitle = NormalizationUtils.pythonNormalize(track.title)
            if (normTitle.isNotEmpty()) {
                normTitleMap.getOrPut(normTitle) { mutableListOf() }.add(track)
            }

            val cleanTitle = NormalizationUtils.pythonNormalize(NormalizationUtils.cleanSongTitle(track.title))
            if (cleanTitle.isNotEmpty()) {
                cleanTitleMap.getOrPut(cleanTitle) { mutableListOf() }.add(track)
            }

            val baseName = NormalizationUtils.pythonNormalize(track.fileName.substringBeforeLast('.'))
            if (baseName.isNotEmpty()) {
                filenameMap.getOrPut(baseName) { mutableListOf() }.add(track)
            }

            pathMap[track.filePath] = track
        }
    }

    fun matchTxtTrack(txtTrack: TxtTrackRow, toleranceMs: Long = 3000L): MatchResult {
        if (poolTracks.isEmpty()) return MatchResult.NoMatch

        val normTargetArtist = NormalizationUtils.pythonNormalize(txtTrack.artist)
        val normTargetTitle = NormalizationUtils.pythonNormalize(txtTrack.title)
        val cleanTargetTitleNorm = NormalizationUtils.pythonNormalize(NormalizationUtils.cleanSongTitle(txtTrack.title))
        val targetKey = NormalizationUtils.songKey(txtTrack.artist, txtTrack.title)

        fun matchesDuration(dur: Long): Boolean {
            if (txtTrack.durationMs <= 0L) return true
            return abs(dur - txtTrack.durationMs) <= toleranceMs
        }

        // 1. Direct Hash Match by songKey
        val exactMatches = exactKeyMap[targetKey]?.filter { matchesDuration(it.durationMs) }
        if (!exactMatches.isNullOrEmpty()) {
            val closest = exactMatches.minByOrNull { abs(it.durationMs - txtTrack.durationMs) } ?: exactMatches.first()
            return MatchResult.SingleMatch(closest, MatchConfidence.EXACT_TITLE_ARTIST_DURATION)
        }

        // 2. Lookup by clean title or norm title
        val titleCandidates = mutableListOf<TrackEntity>()
        normTitleMap[normTargetTitle]?.let { titleCandidates.addAll(it) }
        cleanTitleMap[cleanTargetTitleNorm]?.let { titleCandidates.addAll(it) }

        val validMatches = titleCandidates.distinctBy { it.id }.filter { local ->
            val normLocalArtist = NormalizationUtils.pythonNormalize(local.artist)
            val artistMatches = normLocalArtist == normTargetArtist ||
                    normLocalArtist.contains(normTargetArtist) ||
                    normTargetArtist.contains(normLocalArtist) ||
                    normTargetArtist.isEmpty()
            artistMatches && matchesDuration(local.durationMs)
        }

        if (validMatches.isNotEmpty()) {
            val closest = validMatches.minByOrNull { abs(it.durationMs - txtTrack.durationMs) } ?: validMatches.first()
            return MatchResult.SingleMatch(closest, MatchConfidence.EXACT_TITLE_ARTIST_DURATION)
        }

        // 3. Relaxed duration match for exact key (up to 6s tolerance)
        val relaxedKeyMatches = exactKeyMap[targetKey]?.filter {
            txtTrack.durationMs <= 0L || abs(it.durationMs - txtTrack.durationMs) <= 6000L
        }
        if (!relaxedKeyMatches.isNullOrEmpty()) {
            val closest = relaxedKeyMatches.minByOrNull { abs(it.durationMs - txtTrack.durationMs) } ?: relaxedKeyMatches.first()
            return MatchResult.SingleMatch(closest, MatchConfidence.TITLE_ARTIST_TOLERANT_DURATION)
        }

        return MatchResult.NoMatch
    }

    fun matchCsvTrack(csvTrack: CsvTrackRow): MatchResult {
        if (poolTracks.isEmpty()) return MatchResult.NoMatch

        val csvArtist = NormalizationUtils.sanitizeText(csvTrack.artistNames)
        val csvTitle = NormalizationUtils.sanitizeText(csvTrack.trackName)
        val csvAlbum = NormalizationUtils.sanitizeText(csvTrack.albumName)
        val csvDurationMs = csvTrack.durationMs

        val targetKey = NormalizationUtils.songKey(csvArtist, csvTitle)
        val normCsvArtist = NormalizationUtils.pythonNormalize(csvArtist)
        val normCsvTitle = NormalizationUtils.pythonNormalize(csvTitle)
        val cleanCsvTitleNorm = NormalizationUtils.pythonNormalize(NormalizationUtils.cleanSongTitle(csvTitle))

        fun matchesDuration(localDurationMs: Long, toleranceMs: Long): Boolean {
            if (csvDurationMs <= 0L) return true
            if (abs(localDurationMs - csvDurationMs) <= toleranceMs) return true
            if ((localDurationMs / 1000) == (csvDurationMs / 1000)) return true
            return false
        }

        // 1. Exact key lookup
        val exactMatches = exactKeyMap[targetKey]?.filter { matchesDuration(it.durationMs, 3000L) }
        if (!exactMatches.isNullOrEmpty()) {
            if (exactMatches.size == 1) {
                return MatchResult.SingleMatch(exactMatches.first(), MatchConfidence.EXACT_TITLE_ARTIST_DURATION)
            }
            val albumMatch = exactMatches.firstOrNull {
                NormalizationUtils.pythonNormalize(it.album) == NormalizationUtils.pythonNormalize(csvAlbum)
            }
            if (albumMatch != null) {
                return MatchResult.SingleMatch(albumMatch, MatchConfidence.TITLE_ARTIST_ALBUM_DURATION)
            }
            val closest = exactMatches.minByOrNull { abs(it.durationMs - csvDurationMs) } ?: exactMatches.first()
            return MatchResult.SingleMatch(closest, MatchConfidence.EXACT_TITLE_ARTIST_DURATION)
        }

        // 2. Filename Map
        val expectedBase1 = normCsvArtist + normCsvTitle
        val expectedBase2 = normCsvTitle + normCsvArtist
        val fnMatches = (filenameMap[expectedBase1] ?: filenameMap[expectedBase2] ?: filenameMap[normCsvTitle])
            ?.filter { matchesDuration(it.durationMs, 3000L) }
        if (!fnMatches.isNullOrEmpty()) {
            val closest = fnMatches.minByOrNull { abs(it.durationMs - csvDurationMs) } ?: fnMatches.first()
            return MatchResult.SingleMatch(closest, MatchConfidence.FILENAME)
        }

        // 3. Title Map / Clean Title Map
        val candidates = mutableListOf<TrackEntity>()
        normTitleMap[normCsvTitle]?.let { candidates.addAll(it) }
        cleanTitleMap[cleanCsvTitleNorm]?.let { candidates.addAll(it) }

        val cleanTitleMatches = candidates.distinctBy { it.id }.filter { local ->
            val normLocalArtist = NormalizationUtils.pythonNormalize(local.artist)
            val artistMatches = normLocalArtist == normCsvArtist ||
                    normLocalArtist.contains(normCsvArtist) ||
                    normCsvArtist.contains(normLocalArtist) ||
                    normCsvArtist.isEmpty()
            artistMatches && matchesDuration(local.durationMs, 3000L)
        }

        if (cleanTitleMatches.isNotEmpty()) {
            val closest = cleanTitleMatches.minByOrNull { abs(it.durationMs - csvDurationMs) } ?: cleanTitleMatches.first()
            return MatchResult.SingleMatch(closest, MatchConfidence.EXACT_TITLE_ARTIST_DURATION)
        }

        // 4. Relaxed Duration Match (±6s)
        val relaxedCandidates = (exactKeyMap[targetKey] ?: candidates).filter {
            matchesDuration(it.durationMs, 6000L)
        }
        if (relaxedCandidates.isNotEmpty()) {
            val closest = relaxedCandidates.minByOrNull { abs(it.durationMs - csvDurationMs) } ?: relaxedCandidates.first()
            return MatchResult.SingleMatch(closest, MatchConfidence.TITLE_ARTIST_TOLERANT_DURATION)
        }

        // 5. Zero duration fallback
        if (csvDurationMs <= 0L) {
            val zeroMatches = exactKeyMap[targetKey]
            if (!zeroMatches.isNullOrEmpty()) {
                return MatchResult.SingleMatch(zeroMatches.first(), MatchConfidence.TITLE_ARTIST_ONLY)
            }
        }

        return MatchResult.NoMatch
    }

    fun matchForRestore(
        expectedPath: String?,
        expectedFileName: String?,
        expectedTitle: String,
        expectedArtist: String,
        expectedAlbum: String,
        expectedDurationMs: Long
    ): MatchResult {
        if (poolTracks.isEmpty()) return MatchResult.NoMatch

        if (!expectedPath.isNullOrBlank()) {
            pathMap[expectedPath]?.let {
                return MatchResult.SingleMatch(it, MatchConfidence.EXACT_PATH)
            }
        }

        if (!expectedFileName.isNullOrBlank()) {
            val base = NormalizationUtils.pythonNormalize(expectedFileName.substringBeforeLast('.'))
            filenameMap[base]?.firstOrNull()?.let {
                return MatchResult.SingleMatch(it, MatchConfidence.FILENAME)
            }
        }

        val csvDummy = CsvTrackRow(
            trackName = expectedTitle,
            artistNames = expectedArtist,
            albumName = expectedAlbum,
            durationMs = expectedDurationMs
        )
        return matchCsvTrack(csvDummy)
    }
}

object SongMatcher {
    fun matchTxtTrack(
        txtTrack: TxtTrackRow,
        localTracks: List<TrackEntity>,
        toleranceMs: Long = 3000L
    ): MatchResult {
        return FastTrackMatcher(localTracks).matchTxtTrack(txtTrack, toleranceMs)
    }

    fun matchCsvTrack(
        csvTrack: CsvTrackRow,
        localTracks: List<TrackEntity>
    ): MatchResult {
        return FastTrackMatcher(localTracks).matchCsvTrack(csvTrack)
    }

    fun matchForRestore(
        expectedPath: String?,
        expectedFileName: String?,
        expectedTitle: String,
        expectedArtist: String,
        expectedAlbum: String,
        expectedDurationMs: Long,
        localTracks: List<TrackEntity>
    ): MatchResult {
        return FastTrackMatcher(localTracks).matchForRestore(
            expectedPath, expectedFileName, expectedTitle, expectedArtist, expectedAlbum, expectedDurationMs
        )
    }
}
