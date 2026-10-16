package fetchy.feature.queue

import fetchy.data.DownloadRepository
import fetchy.data.FetchyDatabaseFactory
import fetchy.model.DownloadTask
import fetchy.model.TaskStatus
import fetchy.ytdlp.DownloadScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueRecoveryTest {

    @Test
    fun `recover normalizes interrupted running tasks to queued`() = runTest {
        val repository = repository()
        repository.create(task("a", status = TaskStatus.DOWNLOADING, progress = 0.6))
        val recovery = QueueRecovery(repository, mockk(relaxed = true))

        val resumable = recovery.recover()

        assertEquals(listOf("a"), resumable.map { it.id })
        val stored = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.QUEUED, stored.status)
        assertEquals(0.0, stored.progress, 1e-9)
    }

    @Test
    fun `recover returns queued tasks that were never started`() = runTest {
        val repository = repository()
        repository.create(task("a", status = TaskStatus.QUEUED))
        val recovery = QueueRecovery(repository, mockk(relaxed = true))

        assertEquals(listOf("a"), recovery.recover().map { it.id })
    }

    @Test
    fun `resume submits every resumable task and counts accepted`() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxed = true)
        every { scheduler.submit(any()) } returns true
        val recovery = QueueRecovery(repository(), scheduler)
        val tasks = listOf(task("a"), task("b"))

        val accepted = recovery.resume(tasks)

        assertEquals(2, accepted)
        verify(exactly = 1) { scheduler.submit(match { it.id == "a" }) }
        verify(exactly = 1) { scheduler.submit(match { it.id == "b" }) }
    }

    @Test
    fun `resume counts only accepted submissions`() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxed = true)
        every { scheduler.submit(match { it.id == "a" }) } returns true
        every { scheduler.submit(match { it.id == "b" }) } returns false
        val recovery = QueueRecovery(repository(), scheduler)

        val accepted = recovery.resume(listOf(task("a"), task("b")))

        assertEquals(1, accepted)
    }

    @Test
    fun `resume with no tasks submits nothing`() = runTest {
        val scheduler = mockk<DownloadScheduler>(relaxed = true)
        val recovery = QueueRecovery(repository(), scheduler)

        assertEquals(0, recovery.resume(emptyList()))

        verify(exactly = 0) { scheduler.submit(any()) }
    }

    private fun TestScope.repository(): DownloadRepository =
        DownloadRepository(FetchyDatabaseFactory.inMemory(), UnconfinedTestDispatcher(testScheduler))

    private fun task(
        id: String,
        status: TaskStatus = TaskStatus.QUEUED,
        progress: Double = 0.0,
    ) = DownloadTask(
        id = id,
        url = "https://example.test/$id",
        title = "Title $id",
        createdAt = 1L,
        status = status,
        progress = progress,
    )
}
