package fetchy.feature.history

import fetchy.core.platform.FolderOpener
import fetchy.data.HistoryRepository
import fetchy.model.ErrorClass
import fetchy.model.HistoryEntry
import fetchy.model.TaskStatus
import fetchy.ytdlp.ShellFamily
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryDetailViewModelTest {

    @Test
    fun `loads the entry and exposes a masked command`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(entry(id = "a", formatArgsJson = """["--cookies","cookies.txt","-f","best"]""")),
        )
        val viewModel = createViewModel(repository, id = "a")

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals("a", state.entry?.id)
        val command: String = requireNotNull(state.command)
        assertTrue(command.contains("yt-dlp"))
        assertTrue(command.contains("-f"))
        assertFalse(command.contains("cookies.txt"))
        viewModel.close()
    }

    @Test
    fun `does not read the log until it is requested`() = runTest {
        val logReader = FakeLogReader()
        val repository = FakeHistoryRepository(listOf(entry(id = "a", logPath = "/logs/a.log")))
        val viewModel = createViewModel(repository, id = "a", logReader = logReader)

        assertTrue(logReader.calls.isEmpty())
        assertNull(viewModel.state.value.logText)
        assertFalse(viewModel.state.value.logExpanded)

        viewModel.showLog()

        assertEquals(listOf("/logs/a.log" to HistoryDetailViewModel.DEFAULT_LOG_LIMIT_BYTES), logReader.calls)
        assertEquals("log", viewModel.state.value.logText)
        assertTrue(viewModel.state.value.logExpanded)
        viewModel.close()
    }

    @Test
    fun `expands the log immediately for drm protected entries`() = runTest {
        val logReader = FakeLogReader()
        val repository = FakeHistoryRepository(
            listOf(entry(id = "a", errorClass = ErrorClass.DRM_PROTECTED, logPath = "/logs/a.log")),
        )
        val viewModel = createViewModel(repository, id = "a", logReader = logReader)

        assertTrue(viewModel.state.value.logExpanded)
        assertEquals(1, logReader.calls.size)
        assertEquals("log", viewModel.state.value.logText)
        viewModel.close()
    }

    @Test
    fun `show more grows the read limit`() = runTest {
        val logReader = FakeLogReader(slice = { _, limit -> LogSlice(text = "chunk", truncated = limit < 1_000_000_000L) })
        val repository = FakeHistoryRepository(listOf(entry(id = "a", logPath = "/logs/a.log")))
        val viewModel = createViewModel(repository, id = "a", logReader = logReader)

        viewModel.showLog()
        assertTrue(viewModel.state.value.logTruncated)

        viewModel.showLogMore()

        assertEquals(
            listOf(
                "/logs/a.log" to HistoryDetailViewModel.DEFAULT_LOG_LIMIT_BYTES,
                "/logs/a.log" to HistoryDetailViewModel.DEFAULT_LOG_LIMIT_BYTES + HistoryDetailViewModel.LOG_CHUNK_BYTES,
            ),
            logReader.calls,
        )
        viewModel.close()
    }

    @Test
    fun `open folder reveals the parent directory`() = runTest {
        val opener = RecordingFolderOpener()
        val repository = FakeHistoryRepository(listOf(entry(id = "a", filePath = "/tmp/fetchy/video.mp4")))
        val viewModel = createViewModel(repository, id = "a", opener = opener)

        viewModel.openFolder()

        assertEquals(listOf(Path.of("/tmp/fetchy")), opener.opened)
        viewModel.close()
    }

    @Test
    fun `open folder is ignored without a file path`() = runTest {
        val opener = RecordingFolderOpener()
        val repository = FakeHistoryRepository(listOf(entry(id = "a")))
        val viewModel = createViewModel(repository, id = "a", opener = opener)

        viewModel.openFolder()

        assertTrue(opener.opened.isEmpty())
        viewModel.close()
    }

    @Test
    fun `reports a missing entry`() = runTest {
        val viewModel = createViewModel(FakeHistoryRepository(emptyList()), id = "missing")

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertNull(state.entry)
        assertNull(state.command)
        viewModel.close()
    }

    @Test
    fun `show log does nothing without a log path`() = runTest {
        val logReader = FakeLogReader()
        val repository = FakeHistoryRepository(listOf(entry(id = "a")))
        val viewModel = createViewModel(repository, id = "a", logReader = logReader)

        viewModel.showLog()

        assertTrue(logReader.calls.isEmpty())
        assertNull(viewModel.state.value.logText)
        viewModel.close()
    }

    @Test
    fun `close prevents further work`() = runTest {
        val logReader = FakeLogReader()
        val repository = FakeHistoryRepository(listOf(entry(id = "a", logPath = "/logs/a.log")))
        val viewModel = createViewModel(repository, id = "a", logReader = logReader)

        viewModel.close()
        viewModel.showLog()

        assertTrue(logReader.calls.isEmpty())
    }

    private fun TestScope.createViewModel(
        repository: HistoryRepository,
        id: String,
        opener: FolderOpener = RecordingFolderOpener(),
        logReader: LogFileReader = FakeLogReader(),
        logLimitBytes: Long = HistoryDetailViewModel.DEFAULT_LOG_LIMIT_BYTES,
    ): HistoryDetailViewModel = HistoryDetailViewModel(
        repository = repository,
        opener = opener,
        logReader = logReader,
        id = id,
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        shell = ShellFamily.BASH,
        logLimitBytes = logLimitBytes,
    )

    private fun entry(
        id: String,
        formatArgsJson: String = "[]",
        errorClass: ErrorClass? = null,
        logPath: String? = null,
        filePath: String? = null,
    ): HistoryEntry = HistoryEntry(
        id = id,
        url = "https://example.test/$id",
        title = "Title $id",
        formatArgsJson = formatArgsJson,
        status = TaskStatus.COMPLETED,
        createdAt = 1L,
        errorClass = errorClass,
        logPath = logPath,
        filePath = filePath,
    )

    private class FakeHistoryRepository(
        private val entries: List<HistoryEntry>,
    ) : HistoryRepository {

        override fun observeAll(): Flow<List<HistoryEntry>> = MutableStateFlow(entries)

        override fun search(
            query: String,
            status: TaskStatus?,
            fromMillis: Long?,
            toMillis: Long?,
            limit: Long,
            offset: Long,
        ): Flow<List<HistoryEntry>> = MutableStateFlow(entries)

        override suspend fun get(id: String): HistoryEntry? = entries.firstOrNull { it.id == id }

        override suspend fun insert(entry: HistoryEntry) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun deleteAll() = Unit

        override suspend fun count(): Long = entries.size.toLong()
    }

    private class RecordingFolderOpener : FolderOpener {
        val opened: MutableList<Path> = mutableListOf()

        override fun open(directory: Path): Boolean {
            opened.add(directory)
            return true
        }
    }

    private class FakeLogReader(
        private val slice: (String, Long) -> LogSlice = { _, _ -> LogSlice(text = "log", truncated = false) },
    ) : LogFileReader {
        val calls: MutableList<Pair<String, Long>> = mutableListOf()

        override fun read(path: String, limitBytes: Long): LogSlice {
            calls += path to limitBytes
            return slice(path, limitBytes)
        }
    }
}
