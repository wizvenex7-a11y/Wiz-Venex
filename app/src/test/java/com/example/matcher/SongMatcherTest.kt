package com.example.matcher

import com.example.data.model.TrackEntity
import com.example.util.CsvTrackRow
import com.example.util.NormalizationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongMatcherTest {

    @Test
    fun testNormalizationUtils() {
        assertEquals("blinding lights", NormalizationUtils.cleanSongTitle("Blinding Lights - Remastered 2020"))
        assertEquals("blinding lights", NormalizationUtils.cleanSongTitle("Blinding Lights (Official Audio)"))
        assertEquals("instrumental - music 🎶", NormalizationUtils.sanitizePlaylistName("instrumental_-music🎶_.csv"))
    }

    @Test
    fun testSongMatcherExact() {
        val localTracks = listOf(
            TrackEntity(
                id = "track1",
                filePath = "/music/The Weeknd - Blinding Lights.mp3",
                fileName = "The Weeknd - Blinding Lights.mp3",
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                durationMs = 200000L
            )
        )

        val parsed = CsvTrackRow(
            trackName = "Blinding Lights (Official Audio)",
            artistNames = "The Weeknd",
            albumName = "After Hours",
            durationMs = 201000L
        )

        val result = SongMatcher.matchCsvTrack(parsed, localTracks)
        assertTrue(result is MatchResult.SingleMatch)
        assertEquals("track1", (result as MatchResult.SingleMatch).track.id)
    }

    @Test
    fun testPythonSongKeyAndNormalization() {
        // Python matching removes all dash variations and all whitespace
        val key1 = NormalizationUtils.songKey("Weckly Jay", "All In My Head - Amapiano Version")
        val key2 = NormalizationUtils.songKey("Weckly Jay", "All In My Head – Amapiano Version")
        val key3 = NormalizationUtils.songKey("Weckly Jay", "All In My Head — Amapiano Version")
        assertEquals(key1, key2)
        assertEquals(key1, key3)
        assertEquals("wecklyjay|allinmyheadamapianoversion", key1)
    }

    @Test
    fun testPythonMatchingWithUserSampleTracks() {
        val localTracks = listOf(
            TrackEntity(
                id = "local_1",
                filePath = "/storage/emulated/0/Music/Afrospin - The Fate Of Ophelia Afrobeats.flac",
                fileName = "Afrospin - The Fate Of Ophelia Afrobeats.flac",
                title = "The Fate Of Ophelia Afrobeats",
                artist = "Afrospin",
                album = "",
                durationMs = 231200L // 231.2s
            ),
            TrackEntity(
                id = "local_2",
                filePath = "/storage/emulated/0/Music/ZionRay - Three Seconds To Goodbye.flac",
                fileName = "ZionRay - Three Seconds To Goodbye.flac",
                title = "Three Seconds To Goodbye",
                artist = "ZionRay",
                album = "Three Seconds To Goodbye",
                durationMs = 416000L
            ),
            TrackEntity(
                id = "local_3",
                filePath = "/storage/emulated/0/Music/Weckly Jay - All In My Head (Amapiano Version).flac",
                fileName = "Weckly Jay - All In My Head (Amapiano Version).flac",
                title = "All In My Head (Amapiano Version)",
                artist = "Weckly Jay",
                album = "All In My Head (Amapiano Version)",
                durationMs = 184000L
            )
        )

        // 1. Check Afrospin track matching by Artist, Title, and Time ms
        val csvTrack1 = CsvTrackRow(
            trackUri = "spotify:local:Afrospin::The+Fate+Of+Ophelia+Afrobeats:231",
            trackName = "The Fate Of Ophelia Afrobeats",
            artistNames = "Afrospin",
            albumName = "",
            durationMs = 231000L
        )
        val match1 = SongMatcher.matchCsvTrack(csvTrack1, localTracks)
        assertTrue(match1 is MatchResult.SingleMatch)
        assertEquals("local_1", (match1 as MatchResult.SingleMatch).track.id)
        assertEquals("/storage/emulated/0/Music/Afrospin - The Fate Of Ophelia Afrobeats.flac", match1.track.filePath)

        // 2. Check ZionRay track matching
        val csvTrack2 = CsvTrackRow(
            trackUri = "spotify:local:ZionRay:Three+Seconds+To+Goodbye:Three+Seconds+To+Goodbye:416",
            trackName = "Three Seconds To Goodbye",
            artistNames = "ZionRay",
            albumName = "Three Seconds To Goodbye",
            durationMs = 416000L
        )
        val match2 = SongMatcher.matchCsvTrack(csvTrack2, localTracks)
        assertTrue(match2 is MatchResult.SingleMatch)
        assertEquals("local_2", (match2 as MatchResult.SingleMatch).track.id)

        // 3. Check Amapiano Version track with dash/parentheses variance
        val csvTrack3 = CsvTrackRow(
            trackUri = "spotify:local:Weckly+Jay:All+In+My+Head+%28Amapiano+Version%29:All+In+My+Head+-+Amapiano+Version:184",
            trackName = "All In My Head - Amapiano Version",
            artistNames = "Weckly Jay",
            albumName = "All In My Head (Amapiano Version)",
            durationMs = 184000L
        )
        val match3 = SongMatcher.matchCsvTrack(csvTrack3, localTracks)
        assertTrue(match3 is MatchResult.SingleMatch)
        assertEquals("local_3", (match3 as MatchResult.SingleMatch).track.id)

        // 4. Missing song should return NoMatch (hide unplayable, no fake sound!)
        val missingTrack = CsvTrackRow(
            trackUri = "spotify:local:Orion7::Addicted+to+You:30",
            trackName = "Addicted to You",
            artistNames = "Orion7",
            albumName = "",
            durationMs = 30000L
        )
        val matchMissing = SongMatcher.matchCsvTrack(missingTrack, localTracks)
        assertTrue(matchMissing is MatchResult.NoMatch)
    }

    @Test
    fun testDurationMismatchReturnsNoMatch() {
        val localTracks = listOf(
            TrackEntity(
                id = "track3",
                filePath = "/music/Short_Preview.mp3",
                fileName = "Short_Preview.mp3",
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                durationMs = 30000L // 30s preview snippet, not full 200s song
            )
        )

        val parsed = CsvTrackRow(
            trackName = "Blinding Lights",
            artistNames = "The Weeknd",
            albumName = "After Hours",
            durationMs = 200000L
        )

        val result = SongMatcher.matchCsvTrack(parsed, localTracks)
        assertTrue(result is MatchResult.NoMatch)
    }

    @Test
    fun testDemoTracksAreExcludedFromMatching() {
        val localTracks = listOf(
            TrackEntity(
                id = "demo_track_1",
                filePath = "/data/user/0/com.example/files/demo_music/track1.wav",
                fileName = "track1.wav",
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                durationMs = 200000L
            )
        )

        val parsed = CsvTrackRow(
            trackName = "Blinding Lights",
            artistNames = "The Weeknd",
            albumName = "After Hours",
            durationMs = 200000L
        )

        val result = SongMatcher.matchCsvTrack(parsed, localTracks)
        // Should be NoMatch because demo sounds must NOT be used for CSV restore
        assertTrue(result is MatchResult.NoMatch)
    }
}
