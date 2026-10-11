package fetchy.ytdlp.installer

import fetchy.ytdlp.util.Platform
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AtomicInstallerTest {

    private lateinit var root: Path
    private lateinit var installer: AtomicInstaller

    @BeforeEach
    fun setUp() {
        root = Files.createTempDirectory("installer")
        installer = AtomicInstaller(Platform.LINUX)
    }

    private fun write(name: String, content: String): Path {
        val file = root.resolve(name)
        Files.writeString(file, content)
        return file
    }

    @Test
    fun installsStagedBinaryAndKeepsBackup() = runTest {
        val staged = write("staged", "new")
        val target = write("yt-dlp", "old")

        val result = installer.install(staged, target) { true }

        assertEquals(AtomicInstallResult.Installed, result)
        assertEquals("new", Files.readString(target))
        val backup = target.resolveSibling("yt-dlp.bak")
        assertTrue(Files.exists(backup))
        assertEquals("old", Files.readString(backup))
    }

    @Test
    fun rollbackRestoresPreviousVersion() = runTest {
        val staged = write("staged", "new")
        val target = write("yt-dlp", "old")

        val result = installer.install(staged, target) { false }

        assertInstanceOf(AtomicInstallResult.RolledBack::class.java, result)
        assertEquals("old", Files.readString(target))
        assertTrue(!Files.exists(target.resolveSibling("yt-dlp.bak")))
    }

    @Test
    fun firstInstallRollbackLeavesNoTarget() = runTest {
        val staged = write("staged", "new")
        val target = root.resolve("yt-dlp")

        val result = installer.install(staged, target) { false }

        assertInstanceOf(AtomicInstallResult.RolledBack::class.java, result)
        assertTrue(!Files.exists(target))
    }

    @Test
    fun installsWithoutPreviousVersion() = runTest {
        val staged = write("staged", "new")
        val target = root.resolve("yt-dlp")

        val result = installer.install(staged, target) { true }

        assertEquals(AtomicInstallResult.Installed, result)
        assertEquals("new", Files.readString(target))
    }
}