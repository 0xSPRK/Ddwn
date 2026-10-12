package fetchy.app

import fetchy.core.platform.AppPaths
import fetchy.data.DownloadRepository
import fetchy.data.HistoryRepository
import fetchy.data.PresetRepository
import fetchy.data.SettingsRepository
import fetchy.model.DownloadTask
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AppGraphTest {

    @Test
    fun graphResolvesCoreRepositories(@TempDir root: Path) {
        AppGraph.start(AppPaths(root)).use { graph ->
            assertNotNull(graph.koin.get<DownloadRepository>())
            assertNotNull(graph.koin.get<HistoryRepository>())
            assertNotNull(graph.koin.get<PresetRepository>())
            assertNotNull(graph.koin.get<SettingsRepository>())
            assertNotNull(graph.koin.get<AppPaths>())
        }
    }

    @Test
    fun startCreatesDatabaseFile(@TempDir root: Path) {
        val paths = AppPaths(root).ensureExists()
        AppGraph.start(paths).use { }
        assertTrue(Files.exists(paths.databaseFile))
    }

    @Test
    fun repositoriesRoundTripThroughGraph(@TempDir root: Path) = runBlocking {
        val paths = AppPaths(root).ensureExists()
        AppGraph.start(paths).use { graph ->
            val repo = graph.koin.get<DownloadRepository>()
            repo.create(DownloadTask(id = "t1", url = "https://example.test/t1", createdAt = 1L))

            val loaded = requireNotNull(repo.get("t1"))
            assertEquals("https://example.test/t1", loaded.url)
            assertEquals(1L, repo.activeCount())
        }
    }
}
