package fetchy.ytdlp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReusableCommandTest {

    private val url = "https://example.test/video"

    private val defaultPrefix: List<String> =
        listOf("yt-dlp", "--ignore-config", "--newline", "--progress-template", ArgvBuilder.PROGRESS_TEMPLATE)

    @Test
    fun `includes the always-on options so the command reproduces the download`() {
        val argv = CommandStringCodec.parse(
            maskedReusableCommand("[]", url, ShellFamily.BASH),
            ShellFamily.BASH,
        )

        assertEquals(defaultPrefix + url, argv)
    }

    @Test
    fun `masks secret option values`() {
        val json = """["--cookies","cookies.txt","-f","best"]"""

        val argv = CommandStringCodec.parse(
            maskedReusableCommand(json, url, ShellFamily.BASH),
            ShellFamily.BASH,
        )

        assertEquals(defaultPrefix + listOf("--cookies", CommandStringCodec.MASK, "-f", "best", url), argv)
    }

    @Test
    fun `masks credentials embedded in the url`() {
        val command = maskedReusableCommand("[]", "https://user:password@example.test/video", ShellFamily.BASH)

        assertFalse(command.contains("password"))
        assertTrue(command.contains(CommandStringCodec.MASK))
    }

    @Test
    fun `falls back to no user options when format args are not valid json`() {
        val argv = CommandStringCodec.parse(
            maskedReusableCommand("not-json", url, ShellFamily.BASH),
            ShellFamily.BASH,
        )

        assertEquals(defaultPrefix + url, argv)
    }

    @Test
    fun `treats blank format args as no options`() {
        val argv = CommandStringCodec.parse(
            maskedReusableCommand("  ", url, ShellFamily.BASH),
            ShellFamily.BASH,
        )

        assertEquals(defaultPrefix + url, argv)
    }
}
