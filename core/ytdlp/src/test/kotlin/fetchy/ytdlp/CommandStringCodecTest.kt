package fetchy.ytdlp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandStringCodecTest {

    private val trickyArgs: List<String> =
        listOf(
            "-f",
            "bestvideo+bestaudio/best",
            "--output",
            "C:\\Users\\me\\My Videos\\%(title)s.%(ext)s",
            "--add-headers",
            "Authorization: Bearer secret",
            "",
            "hello world",
            "it's a 'test'",
            "say \"hi\"",
            "юникод-путь/файл.mp4",
            "--print",
            "%(title)s",
            "C:\\path\\with\\trailing\\",
        )

    @Test
    fun roundTripsBash() {
        val command = CommandStringCodec.format(trickyArgs, ShellFamily.BASH)
        assertEquals(trickyArgs, CommandStringCodec.parse(command, ShellFamily.BASH))
    }

    @Test
    fun roundTripsCmd() {
        val command = CommandStringCodec.format(trickyArgs, ShellFamily.CMD)
        assertEquals(trickyArgs, CommandStringCodec.parse(command, ShellFamily.CMD))
    }

    @Test
    fun roundTripsPowerShell() {
        val command = CommandStringCodec.format(trickyArgs, ShellFamily.POWERSHELL)
        assertEquals(trickyArgs, CommandStringCodec.parse(command, ShellFamily.POWERSHELL))
    }

    @Test
    fun doesNotExpandVariables() {
        val parsed = CommandStringCodec.parse("yt-dlp \$VAR %USERPROFILE% *.mp4", ShellFamily.BASH)
        assertEquals(listOf("yt-dlp", "\$VAR", "%USERPROFILE%", "*.mp4"), parsed)
    }

    @Test
    fun importsCommandDroppingExecutable() {
        assertEquals(
            listOf("-f", "best", "https://x/y"),
            CommandStringCodec.importArgs("yt-dlp -f best https://x/y", ShellFamily.BASH),
        )
        assertEquals(
            listOf("-f", "best", "https://x/y"),
            CommandStringCodec.importArgs("\"C:\\Tools\\yt-dlp.exe\" -f best https://x/y", ShellFamily.CMD),
        )
        assertEquals(
            listOf("-f", "best"),
            CommandStringCodec.importArgs("-f best", ShellFamily.POWERSHELL),
        )
    }

    @Test
    fun masksSecrets() {
        val argv =
            listOf(
                "--password",
                "s3cr3tValue",
                "--video-password=hunter2Value",
                "--twofactor",
                "654321",
                "--ap-username",
                "apUser",
                "--ap-password",
                "apPass",
                "--cookies",
                "C:\\cookie\\secret.txt",
                "--proxy",
                "http://puser:ppass@proxy.local:8080",
                "--add-headers",
                "Authorization: Bearer abc123token",
                "https://user:pass@example.com/watch?v=1",
            )

        val masked = CommandStringCodec.mask(argv)

        assertEquals(argv.size, masked.size)
        for (secret in listOf("s3cr3tValue", "hunter2Value", "654321", "apUser", "apPass", "secret.txt", "ppass")) {
            assertTrue(masked.none { it.contains(secret) }, "leaked: $secret")
        }
        assertEquals(CommandStringCodec.MASK, masked[1])
        assertEquals("--video-password=${CommandStringCodec.MASK}", masked[2])
        assertEquals("http://${CommandStringCodec.MASK}@proxy.local:8080", masked[12])
        assertEquals("authorization: ${CommandStringCodec.MASK}", masked[14])
        assertEquals("https://${CommandStringCodec.MASK}@example.com/watch?v=1", masked[15])
    }
}
