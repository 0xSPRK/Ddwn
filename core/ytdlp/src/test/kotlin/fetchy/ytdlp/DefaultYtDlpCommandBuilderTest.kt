package fetchy.ytdlp

import fetchy.model.DownloadTask
import fetchy.ytdlp.binary.BinaryConfig
import fetchy.ytdlp.binary.BinaryManager
import fetchy.ytdlp.binary.BinaryMode
import fetchy.ytdlp.binary.ProcessLauncher
import fetchy.ytdlp.binary.ProcessOutput
import fetchy.ytdlp.components.ComponentId
import fetchy.ytdlp.util.AppPaths
import fetchy.ytdlp.util.Platform
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultYtDlpCommandBuilderTest {

    private val root: Path = Files.createTempDirectory("cmdbuilder")
    private val appPaths: AppPaths = AppPaths(root, root.resolve("components"), root.resolve("cache"))

    private val launcher: ProcessLauncher =
        object : ProcessLauncher {
            override suspend fun run(command: List<String>, timeoutMillis: Long): ProcessOutput =
                ProcessOutput(0, "2026.08.19", "")
        }

    private fun manager(userPath: Path?): BinaryManager =
        BinaryManager(
            appPaths = appPaths,
            platform = Platform.LINUX,
            launcher = launcher,
            config =
                BinaryConfig(
                    mode = BinaryMode.CUSTOM,
                    userPaths = userPath?.let { mapOf(ComponentId.YT_DLP to it) } ?: emptyMap(),
                ),
        )

    private fun binaryFile(): Path {
        val file = root.resolve("yt-dlp")
        Files.writeString(file, "binary")
        return file
    }

    @Test
    fun `build uses binary path and format args from task`() = runTest {
        val builder = DefaultYtDlpCommandBuilder(manager(binaryFile()))

        val command = builder.build(task("""["-f","137+140"]"""))

        assertEquals(binaryFile().toString(), command.first())
        assertEquals("https://example.test/v", command.last())
        assertTrue(command.contains("-f"))
        assertTrue(command.contains("137+140"))
        assertEquals(1, command.count { it == "--ignore-config" })
    }

    @Test
    fun `blank format args produce no user args`() = runTest {
        val builder = DefaultYtDlpCommandBuilder(manager(binaryFile()))

        val command = builder.build(task(""))

        assertTrue(command.none { it == "-f" })
        assertEquals("https://example.test/v", command.last())
    }

    @Test
    fun `malformed format args are ignored`() = runTest {
        val builder = DefaultYtDlpCommandBuilder(manager(binaryFile()))

        val command = builder.build(task("{not json"))

        assertTrue(command.none { it == "-f" })
        assertEquals("https://example.test/v", command.last())
    }

    @Test
    fun `missing binary yields empty command`() = runTest {
        val builder = DefaultYtDlpCommandBuilder(manager(null))

        assertTrue(builder.build(task("[]")).isEmpty())
    }

    private fun task(formatArgsJson: String): DownloadTask =
        DownloadTask(
            id = "t1",
            url = "https://example.test/v",
            createdAt = 1L,
            formatArgsJson = formatArgsJson,
        )
}
