package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CsvUtilsTest {

    @Test
    fun testParseSpotifyCsvWithLocalUri() {
        val sampleCsv = """
            "Track URI","Track Name","Artist URI(s)","Artist Name(s)","Album URI","Album Name","Album Artist URI(s)","Album Artist Name(s)","Album Release Date","Album Image URL","Disc Number","Track Number","Track Duration (ms)","Track Preview URL","Explicit","Popularity","ISRC","Added By","Added At"
            "spotify:local:Afrospin::The+Fate+Of+Ophelia+Afrobeats:231","The Fate Of Ophelia Afrobeats","","Afrospin","","","","","","","0","0","231000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-04-13T16:47:23Z"
            "spotify:local:ZionRay:Three+Seconds+To+Goodbye:Three+Seconds+To+Goodbye:416","Three Seconds To Goodbye","","ZionRay","","Three Seconds To Goodbye","","","","","0","0","416000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-04-13T16:47:23Z"
            "spotify:local:Chonde+Studios::Single+Again:24","Single Again","","Chonde Studios","","","","","","","0","0","24000","","false","0","","spotify:user:31z3veo26ee6gbnfgc5l62tx723q","2026-04-13T16:47:23Z"
        """.trimIndent()

        val parsed = CsvUtils.parseSpotifyCsv(sampleCsv)
        assertEquals(3, parsed.size)

        val track1 = parsed[0]
        assertEquals("The Fate Of Ophelia Afrobeats", track1.trackName)
        assertEquals("Afrospin", track1.artistNames)
        assertEquals(231000L, track1.durationMs)

        val track2 = parsed[1]
        assertEquals("Three Seconds To Goodbye", track2.trackName)
        assertEquals("ZionRay", track2.artistNames)
        assertEquals(416000L, track2.durationMs)

        val track3 = parsed[2]
        assertEquals("Single Again", track3.trackName)
        assertEquals("Chonde Studios", track3.artistNames)
        assertEquals(24000L, track3.durationMs)
    }

    @Test
    fun testParseSpotifyLocalUriFallbackWhenFieldsEmpty() {
        val uri = "spotify:local:Weckly+Jay:All+In+My+Head+%28Amapiano+Version%29:All+In+My+Head+-+Amapiano+Version:184"
        val parsed = CsvUtils.parseSpotifyLocalUri(uri)
        assertNotNull(parsed)
        assertEquals("Weckly Jay", parsed!!.artist)
        assertEquals("All In My Head - Amapiano Version", parsed.title)
        assertEquals("All In My Head (Amapiano Version)", parsed.album)
        assertEquals(184000L, parsed.durationMs)
    }
}
