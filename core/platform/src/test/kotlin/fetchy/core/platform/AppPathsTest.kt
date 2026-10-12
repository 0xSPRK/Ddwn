package fetchy.core.platform

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AppPathsTest {

    @Test
    fun subPathsResolveUnderRoot(@TempDir root: Path) {
        val paths = AppPaths(root)

        assertEquals(root.resolve("config"), paths.configDirectory)
        assertEquals(root.resolve("cache"), paths.cacheDirectory)
        assertEquals(root.resolve("logs"), paths.logsDirectory)
        assertEquals(root.resolve("data"), paths.dataDirectory)
        assertEquals(root.resolve("data/fetchy.db"), paths.databaseFile)
        assertEquals(root.resolve("fetchy.port"), paths.singleInstancePortFile)
    }

    @Test
    fun ensureExistsCreatesAllDirectories(@TempDir root: Path) {
        val paths = AppPaths(root).ensureExists()

        assertTrue(Files.isDirectory(paths.configDirectory))
        assertTrue(Files.isDirectory(paths.cacheDirectory))
        assertTrue(Files.isDirectory(paths.logsDirectory))
        assertTrue(Files.isDirectory(paths.dataDirectory))
    }
}