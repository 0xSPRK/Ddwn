package fetchy.ytdlp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReusableCommandTest {

    private val url = "https://example.test/video"

    @Test
    fun `masks secret option values`() {
        val json = """["--cookies","cookies.txt","-f","best"]"""

        val argv = CommandStringCodec.parse(
            maskedReusableCommand(json, url, ShellFamily.BASH),
            ShellFamily.BASH,
        )

        assertEquals(listOf("yt-dlp", "--cookies", CommandStringCodec.MASK, "-f", "best", url), argv)
    }

    @Test
    fun `masks credentials embedded in the url`() {
        val command = maskedReusableCommand("[]", "https://user:password@example.test/video", ShellFamily.BASH)

        assertFalse(command.contains("password"))
        assertTrue(command.contains(CommandStringCodec.MASK))
    }

    @Test
    fun `falls back to just the tool and url when format args are not valid json`() {
        val argv = CommandStringCodec.parse(
            maskedReusableCommand("not-json", url, ShellFamily.BASH),
            ShellFamily.BASH,
        )

        assertEquals(listOf("yt-dlp", url), argv)
    }

    @Test
    fun `treats blank format args as no options`() {
        val argv = CommandStringCodec.parse(
            maskedReusableCommand("  ", url, ShellFamily.BASH),
            ShellFamily.BASH,
        )

        assertEquals(listOf("yt-dlp", url), argv)
    }
}
