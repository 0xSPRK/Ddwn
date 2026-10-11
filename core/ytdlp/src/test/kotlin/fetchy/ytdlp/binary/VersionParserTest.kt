package fetchy.ytdlp.binary

import fetchy.ytdlp.components.ComponentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VersionParserTest {

    @Test
    fun parsesYtDlpDate() {
        assertEquals("2026.08.19", VersionParser.parse(ComponentId.YT_DLP, "2026.08.19"))
    }

    @Test
    fun parsesYtDlpDateWithNightlySuffix() {
        assertEquals("2026.08.19.183023", VersionParser.parse(ComponentId.YT_DLP, "2026.08.19.183023"))
    }

    @Test
    fun parsesYtDlpDateFromNoisyOutput() {
        val noisy = "yt-dlp is a command-line program\n2026.08.19 (c) 2006-2026"
        assertEquals("2026.08.19", VersionParser.parse(ComponentId.YT_DLP, noisy))
    }

    @Test
    fun ytDlpUnrecognizedReturnNull() {
        assertNull(VersionParser.parse(ComponentId.YT_DLP, "garbage output"))
    }

    @Test
    fun parsesFfmpegVersionFromFirstLine() {
        val output = "ffmpeg version n5.1.5 Copyright (c) 2000-2025 the FFmpeg developers"
        assertEquals("n5.1.5", VersionParser.parse(ComponentId.FFMPEG, output))
    }

    @Test
    fun parsesFfprobeVersion() {
        val output = "ffprobe version 6.0-essentials Copyright (c) 2007-2025"
        assertEquals("6.0-essentials", VersionParser.parse(ComponentId.FFPROBE, output))
    }

    @Test
    fun parsesDenoVersion() {
        val output = "deno 1.45.14 (release, x86_64-pc-windows-msvc)"
        assertEquals("1.45.14", VersionParser.parse(ComponentId.DENO, output))
    }

    @Test
    fun blankOutputReturnsNull() {
        assertNull(VersionParser.parse(ComponentId.YT_DLP, "   \n"))
    }

    @Test
    fun detectStatusOkForRecognizedVersion() {
        assertEquals(BinaryStatus.OK, VersionParser.detectStatus(ComponentId.YT_DLP, "2026.08.19"))
    }

    @Test
    fun detectStatusUnknownForUnrecognizedVersion() {
        assertEquals(BinaryStatus.UNKNOWN_VERSION, VersionParser.detectStatus(ComponentId.DENO, "???"))
    }
}