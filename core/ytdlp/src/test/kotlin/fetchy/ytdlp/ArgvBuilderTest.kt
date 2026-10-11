package fetchy.ytdlp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArgvBuilderTest {

    private val builder = ArgvBuilder()

    @Test
    fun alwaysAddsIgnoreConfig() {
        val command = builder.build(YtDlpInvocation(urls = listOf("https://example.com/v")))
        assertEquals("--ignore-config", command.argv.first())
    }

    @Test
    fun omitsIgnoreConfigWhenUserConfigRequested() {
        val command =
            builder.build(YtDlpInvocation(urls = listOf("https://example.com/v"), readUserConfig = true))
        assertFalse(command.argv.contains("--ignore-config"))
    }

    @Test
    fun appendsGuiOwnedAndProgressOptions() {
        val command = builder.build(YtDlpInvocation(urls = listOf("https://example.com/v")))
        assertTrue(command.argv.contains("--newline"))
        val index = command.argv.indexOf("--progress-template")
        assertTrue(index >= 0)
        assertEquals(ArgvBuilder.PROGRESS_TEMPLATE, command.argv[index + 1])
    }

    @Test
    fun keepsStableOrderAcrossCalls() {
        val invocation =
            YtDlpInvocation(
                urls = listOf("https://example.com/v"),
                options =
                    YtDlpOptions(
                        outputTemplate = "%(title)s.%(ext)s",
                        formatSelector = "best",
                        subtitleLanguages = listOf("en", "ru"),
                        proxy = "http://localhost:8080",
                    ),
            )
        assertEquals(builder.build(invocation).argv, builder.build(invocation).argv)
    }

    @Test
    fun dropsGuiOwnedOptionsFromUserArgsWithWarning() {
        val command =
            builder.build(
                YtDlpInvocation(
                    urls = listOf("https://example.com/v"),
                    userArgs = listOf("--newline", "--progress-template", "%(x)s", "-f", "best"),
                ),
            )
        assertEquals(1, command.argv.count { it == "--newline" })
        assertFalse(command.argv.contains("%(x)s"))
        assertTrue(command.argv.contains("best"))
        assertEquals(2, command.warnings.size)
    }

    @Test
    fun separatesUrlsStartingWithDash() {
        val command = builder.build(YtDlpInvocation(urls = listOf("-weird-url")))
        val separator = command.argv.indexOf("--")
        assertTrue(separator >= 0)
        assertEquals("-weird-url", command.argv[separator + 1])
    }

    @Test
    fun masksCredentialsInDisplayedCommand() {
        val command =
            builder.build(
                YtDlpInvocation(
                    urls = listOf("https://user:pass@example.com/v"),
                    options =
                        YtDlpOptions(
                            password = "topsecret",
                            videoPassword = "vidsecret",
                            proxy = "http://puser:ppass@proxy.local:8080",
                        ),
                ),
            )
        assertTrue(command.maskedArgv.none { it.contains("topsecret") || it.contains("vidsecret") })
        assertTrue(command.maskedArgv.none { it.contains("ppass") })
        assertTrue(command.argv.any { it.contains("topsecret") })
    }
}
