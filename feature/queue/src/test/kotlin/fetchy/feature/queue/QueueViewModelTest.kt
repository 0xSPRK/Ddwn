package fetchy.feature.queue

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fetchy.data.DownloadRepository
import fetchy.data.FetchyDatabaseFactory
import fetchy.model.DownloadTask
import fetchy.model.ProgressInfo
import fetchy.model.TaskStatus
import fetchy.ytdlp.DownloadScheduler
import fetchy.ytdlp.SchedulerEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {

    @Test
    fun `title falls back to url when null`() = runTest {
        val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())
        repository.create(task("a", title = null))
        val harness = createHarness(repository)

        harness.viewModel.tasks.test {
            assertEquals("https://example.test/a", awaitNonEmpty().single().title)
            cancelAndIgnoreRemainingEvents()
        }
        harness.viewModel.close()
    }

    @Test
    fun `thumbnail url is mapped`() = runTest {
        val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())
        repository.create(task("a", thumbnailUrl = "https://img/a.jpg"))
        val harness = createHarness(repository)

        harness.viewModel.tasks.test {
            assertEquals("https://img/a.jpg", awaitNonEmpty().single().thumbnailUrl)
            cancelAndIgnoreRemainingEvents()
        }
        harness.viewModel.close()
    }

    @Test
    fun `progress comes from persisted field`() = runTest {
        val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())
        repository.create(task("a", progress = 0.42, status = TaskStatus.DOWNLOADING))
        val harness = createHarness(repository)

        harness.viewModel.tasks.test {
            assertEquals(0.42, awaitNonEmpty().single().progress, 1e-9)
            cancelAndIgnoreRemainingEvents()
        }
        harness.viewModel.close()
    }

    @Test
    fun `speed eta and playlist come from progressed event`() = runTest {
        val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())
        repository.create(task("a", status = TaskStatus.DOWNLOADING))
        val harness = createHarness(repository)

        harness.viewModel.tasks.test {
            awaitNonEmpty()
            harness.events.emit(
                SchedulerEvent.Progressed("a", 0.5, info(speed = 1_048_576.0, eta = 42L, playlistIndex = 3)),
            )
            val state = awaitUntil { it.singleOrNull()?.speedBytesPerSecond != null }.single()
            assertEquals(1_048_576.0, state.speedBytesPerSecond)
            assertEquals(42L, state.etaSeconds)
            assertEquals(3, state.playlistIndex)
            cancelAndIgnoreRemainingEvents()
        }
        harness.viewModel.close()
    }

    @Test
    fun `finished event clears live overlay`() = runTest {
        val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())
        repository.create(task("a", status = TaskStatus.DOWNLOADING))
        val harness = createHarness(repository)

        harness.viewModel.tasks.test {
            awaitNonEmpty()
            harness.events.emit(SchedulerEvent.Progressed("a", 0.5, info(speed = 2_000_000.0, eta = 10L)))
            assertEquals(2_000_000.0, awaitUntil { it.single().speedBytesPerSecond != null }.single().speedBytesPerSecond)
            harness.events.emit(SchedulerEvent.Finished("a", TaskStatus.COMPLETED, null))
            val cleared = awaitUntil { it.singleOrNull()?.speedBytesPerSecond == null }.single()
            assertNull(cleared.speedBytesPerSecond)
            assertNull(cleared.etaSeconds)
            cancelAndIgnoreRemainingEvents()
        }
        harness.viewModel.close()
    }

    @Test
    fun `empty repository emits empty list`() = runTest {
        val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())
        val harness = createHarness(repository)

        harness.viewModel.tasks.test {
            assertEquals(emptyList<QueueTaskUiState>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        harness.viewModel.close()
    }

    @Test
    fun `tasks are ordered by creation time`() = runTest {
        val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())
        repository.create(task("b", createdAt = 2L))
        repository.create(task("a", createdAt = 1L))
        val harness = createHarness(repository)

        harness.viewModel.tasks.test {
            assertEquals(listOf("a", "b"), awaitNonEmpty().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        harness.viewModel.close()
    }

    @Test
    fun `cancel delegates to scheduler`() = runTest {
        val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())
        val harness = createHarness(repository)

        harness.viewModel.cancel("a")

        verify(exactly = 1) { harness.scheduler.cancel("a") }
        harness.viewModel.close()
    }

    private data class Harness(
        val viewModel: QueueViewModel,
        val scheduler: DownloadScheduler,
        val events: MutableSharedFlow<SchedulerEvent>,
    )

    private fun TestScope.createHarness(
        repository: DownloadRepository,
        events: MutableSharedFlow<SchedulerEvent> = MutableSharedFlow(extraBufferCapacity = 16),
    ): Harness {
        val scheduler = mockk<DownloadScheduler>()
        every { scheduler.events } returns events
        every { scheduler.cancel(any()) } just Runs
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        return Harness(QueueViewModel(repository, scheduler, scope), scheduler, events)
    }

    private suspend fun ReceiveTurbine<List<QueueTaskUiState>>.awaitNonEmpty(): List<QueueTaskUiState> =
        awaitUntil { it.isNotEmpty() }

    private suspend fun ReceiveTurbine<List<QueueTaskUiState>>.awaitUntil(
        predicate: (List<QueueTaskUiState>) -> Boolean,
    ): List<QueueTaskUiState> {
        var item = awaitItem()
        while (!predicate(item)) {
            item = awaitItem()
        }
        return item
    }

    private fun task(
        id: String,
        createdAt: Long = 1L,
        title: String? = "Title $id",
        thumbnailUrl: String? = null,
        progress: Double = 0.0,
        status: TaskStatus = TaskStatus.QUEUED,
    ) = DownloadTask(
        id = id,
        url = "https://example.test/$id",
        title = title,
        thumbnailUrl = thumbnailUrl,
        status = status,
        progress = progress,
        createdAt = createdAt,
    )

    private fun info(
        speed: Double? = null,
        eta: Long? = null,
        playlistIndex: Int? = null,
    ) = ProgressInfo(
        id = "a",
        playlistIndex = playlistIndex,
        status = "downloading",
        downloadedBytes = 50L,
        totalBytes = 100L,
        totalBytesEstimate = null,
        speed = speed,
        etaSeconds = eta,
    )
}
