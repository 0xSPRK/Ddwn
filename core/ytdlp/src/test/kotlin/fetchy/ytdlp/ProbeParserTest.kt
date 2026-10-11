package fetchy.ytdlp

import fetchy.model.MediaInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProbeParserTest {

    private val info: MediaInfo = ProbeParser.parse(fixture())

    @Test
    fun parsesFormat() {
        assertEquals("mov,mp4,m4a,3gp,3g2,mj2", info.formatName)
        assertEquals(10.048, requireNotNull(info.durationSeconds), 1e-9)
        assertEquals(5242880L, info.sizeBytes)
        assertEquals(4171584L, info.bitRate)
        assertEquals("Sample Clip", info.title)
    }

    @Test
    fun parsesAllStreams() {
        assertEquals(2, info.streams.size)
    }

    @Test
    fun parsesVideoStream() {
        val video = requireNotNull(info.videoStream)
        assertEquals(0, video.index)
        assertEquals("h264", video.codecName)
        assertEquals(1920, video.width)
        assertEquals(1080, video.height)
        assertEquals(30000.0 / 1001.0, requireNotNull(video.frameRate), 1e-9)
        assertTrue(info.hasVideo)
    }

    @Test
    fun parsesAudioStream() {
        val audio = requireNotNull(info.audioStream)
        assertEquals(1, audio.index)
        assertEquals("aac", audio.codecName)
        assertEquals(2, audio.channels)
        assertEquals(44100, audio.sampleRate)
        assertEquals("eng", audio.language)
        assertTrue(info.hasAudio)
    }

    @Test
    fun parsesStreamLanguage() {
        assertEquals("und", info.videoStream?.language)
    }

    @Test
    fun handlesAudioOnlyProbe() {
        val json = """
            {"streams":[{"index":0,"codec_type":"audio","codec_name":"opus"}],"format":{"duration":"1.5"}}
        """.trimIndent()
        val parsed = ProbeParser.parse(json)
        assertFalse(parsed.hasVideo)
        assertTrue(parsed.hasAudio)
        assertEquals(1.5, requireNotNull(parsed.durationSeconds), 1e-9)
        assertEquals("opus", parsed.audioStream?.codecName)
    }

    private fun fixture(): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/probe-sample.json")) {
            "fixture probe-sample.json not found"
        }.bufferedReader().use { it.readText() }
}
