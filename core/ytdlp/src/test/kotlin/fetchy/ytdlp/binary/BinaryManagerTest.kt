package fetchy.ytdlp.binary

import fetchy.ytdlp.components.ComponentId
import fetchy.ytdlp.util.AppPaths
import fetchy.ytdlp.util.Platform
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BinaryManagerTest {

    private lateinit var root: Path
    private lateinit var appPaths: AppPaths

    @BeforeEach
    fun setUp() {
        root = Files.createTempDirectory("binmgr")
        appPaths = AppPaths(root, root.resolve("components"), root.resolve("cache"))
    }

    private class FakeLauncher(
        private val behavior: (List<String>) -> ProcessOutput,
    ) : ProcessLauncher {
        var calls: Int = 0
            private set

        override suspend fun run(command: List<String>, timeoutMillis: Long): ProcessOutput {
            calls++
            return behavior(command)
        }
    }

    private fun manager(
        config: BinaryConfig = BinaryConfig(),
        launcher: ProcessLauncher = FakeLauncher { ProcessOutput(0, "2026.08.19", "") },
        pathEnv: String? = null,
    ): BinaryManager =
        BinaryManager(
            appPaths = appPaths,
            platform = Platform.LINUX,
            launcher = launcher,
            config = config,
            pathEnv = pathEnv,
        )

    private fun writeBinary(dir: Path, name: String, content: String = "binary"): Path {
        Files.createDirectories(dir)
        val file = dir.resolve(name)
        Files.writeString(file, content)
        return file
    }

    @Test
    fun resolvesBinaryFromPathAndParsesVersion() = runTest {
        val binDir = root.resolve("bin")
        val file = writeBinary(binDir, "yt-dlp")
        val launcher = FakeLauncher { ProcessOutput(0, "2026.08.19", "") }
        val info = manager(launcher = launcher, pathEnv = binDir.toString()).resolve(ComponentId.YT_DLP)

        assertEquals(BinaryStatus.OK, info.status)
        assertEquals("2026.08.19", info.version)
        assertEquals(BinarySource.PATH, info.source)
        assertEquals(file, info.path)
        assertEquals(1, launcher.calls)
    }

    @Test
    fun cachesVersionByPathMtimeAndSize() = runTest {
        val binDir = root.resolve("bin")
        writeBinary(binDir, "yt-dlp")
        val launcher = FakeLauncher { ProcessOutput(0, "2026.08.19", "") }
        val manager = manager(launcher = launcher, pathEnv = binDir.toString())

        manager.resolve(ComponentId.YT_DLP)
        manager.resolve(ComponentId.YT_DLP)

        assertEquals(1, launcher.calls)
    }

    @Test
    fun rerunsVersionCheckWhenFileChanges() = runTest {
        val binDir = root.resolve("bin")
        val file = writeBinary(binDir, "yt-dlp", "a")
        val launcher = FakeLauncher { ProcessOutput(0, "2026.08.19", "") }
        val manager = manager(launcher = launcher, pathEnv = binDir.toString())

        manager.resolve(ComponentId.YT_DLP)
        Files.writeString(file, "abcdef")
        manager.resolve(ComponentId.YT_DLP)

        assertEquals(2, launcher.calls)
    }

    @Test
    fun marksBrokenWhenExitCodeNonZero() = runTest {
        val binDir = root.resolve("bin")
        writeBinary(binDir, "yt-dlp")
        val launcher = FakeLauncher { ProcessOutput(1, "", "boom") }
        val info = manager(launcher = launcher, pathEnv = binDir.toString()).resolve(ComponentId.YT_DLP)

        assertEquals(BinaryStatus.BROKEN, info.status)
        assertNull(info.version)
    }

    @Test
    fun marksNotExecutableWhenProcessFailsToStart() = runTest {
        val binDir = root.resolve("bin")
        writeBinary(binDir, "yt-dlp")
        val launcher = FakeLauncher { throw ProcessStartException("cannot start") }
        val info = manager(launcher = launcher, pathEnv = binDir.toString()).resolve(ComponentId.YT_DLP)

        assertEquals(BinaryStatus.NOT_EXECUTABLE, info.status)
    }

    @Test
    fun marksBrokenWhenProcessTimesOut() = runTest {
        val binDir = root.resolve("bin")
        writeBinary(binDir, "yt-dlp")
        val launcher = FakeLauncher { throw ProcessTimeoutException("timeout") }
        val info = manager(launcher = launcher, pathEnv = binDir.toString()).resolve(ComponentId.YT_DLP)

        assertEquals(BinaryStatus.BROKEN, info.status)
    }

    @Test
    fun marksUnknownVersionWhenOutputUnrecognized() = runTest {
        val binDir = root.resolve("bin")
        writeBinary(binDir, "yt-dlp")
        val launcher = FakeLauncher { ProcessOutput(0, "dunno", "") }
        val info = manager(launcher = launcher, pathEnv = binDir.toString()).resolve(ComponentId.YT_DLP)

        assertEquals(BinaryStatus.UNKNOWN_VERSION, info.status)
        assertNull(info.version)
    }

    @Test
    fun systemModeNotFoundWhenAbsentFromPath() = runTest {
        val info = manager(pathEnv = root.resolve("empty").toString()).resolve(ComponentId.YT_DLP)

        assertEquals(BinaryStatus.NOT_FOUND, info.status)
        assertEquals(BinarySource.NONE, info.source)
    }

    @Test
    fun customModeUsesUserPath() = runTest {
        val file = writeBinary(root.resolve("custom"), "my-ytdlp")
        val config = BinaryConfig(mode = BinaryMode.CUSTOM, userPaths = mapOf(ComponentId.YT_DLP to file))
        val info = manager(config = config).resolve(ComponentId.YT_DLP)

        assertEquals(BinaryStatus.OK, info.status)
        assertEquals(BinarySource.USER_DEFINED, info.source)
        assertEquals(file, info.path)
    }

    @Test
    fun customModeNotFoundWhenUserPathMissing() = runTest {
        val missing = root.resolve("custom").resolve("nope")
        val config = BinaryConfig(mode = BinaryMode.CUSTOM, userPaths = mapOf(ComponentId.YT_DLP to missing))
        val info = manager(config = config).resolve(ComponentId.YT_DLP)

        assertEquals(BinaryStatus.NOT_FOUND, info.status)
        assertEquals(BinarySource.USER_DEFINED, info.source)
    }

    @Test
    fun bundledModeUsesComponentsDir() = runTest {
        writeBinary(appPaths.componentsDir, "ffmpeg")
        val launcher = FakeLauncher { ProcessOutput(0, "ffmpeg version n5.1.5 Copyright", "") }
        val config = BinaryConfig(mode = BinaryMode.BUNDLED)
        val info = manager(config = config, launcher = launcher).resolve(ComponentId.FFMPEG)

        assertEquals(BinaryStatus.OK, info.status)
        assertEquals(BinarySource.COMPONENTS_DIR, info.source)
        assertEquals("n5.1.5", info.version)
        assertEquals(appPaths.componentsDir.resolve("ffmpeg"), info.path)
    }
}