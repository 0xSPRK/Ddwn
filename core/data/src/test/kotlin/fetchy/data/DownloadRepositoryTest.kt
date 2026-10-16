package fetchy.data

import fetchy.model.DownloadTask
import fetchy.model.ErrorClass
import fetchy.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadRepositoryTest {

    private val repository = DownloadRepository(FetchyDatabaseFactory.inMemory())

    @Test
    fun createThenGetReturnsTask() = runTest {
        val task = task("a", createdAt = 1L, title = "Alpha", thumbnailUrl = "https://img/a.jpg")

        repository.create(task)

        assertEquals(task, repository.get("a"))
    }

    @Test
    fun thumbnailUrlRoundTripsAndDefaultsToNull() = runTest {
        repository.create(task("a", createdAt = 1L, thumbnailUrl = "https://img/a.jpg"))
        repository.create(task("b", createdAt = 2L, thumbnailUrl = null))

        assertEquals("https://img/a.jpg", repository.get("a")?.thumbnailUrl)
        assertNull(repository.get("b")?.thumbnailUrl)
    }

    @Test
    fun getMissingReturnsNull() = runTest {
        assertNull(repository.get("missing"))
    }

    @Test
    fun observeAllEmitsInsertedTasksInCreationOrder() = runTest {
        repository.create(task("b", createdAt = 2L))
        repository.create(task("a", createdAt = 1L))

        val ids = repository.observeAll().first().map { it.id }

        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun updateStatusPersistsTransition() = runTest {
        repository.create(task("a", createdAt = 1L))

        repository.updateStatus(
            id = "a",
            status = TaskStatus.FAILED,
            progress = 0.42,
            errorClass = ErrorClass.NETWORK,
            finishedAt = 99L,
        )

        val updated = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.FAILED, updated.status)
        assertEquals(0.42, updated.progress, 1e-9)
        assertEquals(ErrorClass.NETWORK, updated.errorClass)
        assertEquals(99L, updated.finishedAt)
    }

    @Test
    fun updateProgressKeepsStatus() = runTest {
        repository.create(task("a", createdAt = 1L, status = TaskStatus.DOWNLOADING))

        repository.updateProgress("a", 0.75)

        val updated = requireNotNull(repository.get("a"))
        assertEquals(0.75, updated.progress, 1e-9)
        assertEquals(TaskStatus.DOWNLOADING, updated.status)
    }

    @Test
    fun observeActiveExcludesFinishedTasks() = runTest {
        repository.create(task("queued", createdAt = 1L, status = TaskStatus.QUEUED))
        repository.create(task("running", createdAt = 2L, status = TaskStatus.DOWNLOADING))
        repository.create(task("done", createdAt = 3L, status = TaskStatus.COMPLETED))
        repository.create(task("failed", createdAt = 4L, status = TaskStatus.FAILED))

        val ids = repository.observeActive().first().map { it.id }

        assertEquals(listOf("queued", "running"), ids)
    }

    @Test
    fun observeIncompleteKeepsFailedAndCancelledButDropsCompleted() = runTest {
        repository.create(task("queued", createdAt = 1L, status = TaskStatus.QUEUED))
        repository.create(task("running", createdAt = 2L, status = TaskStatus.DOWNLOADING))
        repository.create(task("failed", createdAt = 3L, status = TaskStatus.FAILED))
        repository.create(task("cancelled", createdAt = 4L, status = TaskStatus.CANCELLED))
        repository.create(task("done", createdAt = 5L, status = TaskStatus.COMPLETED))

        val ids = repository.observeIncomplete().first().map { it.id }

        assertEquals(listOf("queued", "running", "failed", "cancelled"), ids)
    }

    @Test
    fun activeCountCountsQueuedAndDownloading() = runTest {
        repository.create(task("queued", createdAt = 1L, status = TaskStatus.QUEUED))
        repository.create(task("running", createdAt = 2L, status = TaskStatus.DOWNLOADING))
        repository.create(task("done", createdAt = 3L, status = TaskStatus.COMPLETED))

        assertEquals(2L, repository.activeCount())
    }

    @Test
    fun deleteRemovesTask() = runTest {
        repository.create(task("a", createdAt = 1L))

        repository.delete("a")

        assertNull(repository.get("a"))
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun requeueResetsInterruptedRunningTaskWithZeroProgress() = runTest {
        repository.create(task("a", createdAt = 1L, status = TaskStatus.DOWNLOADING, progress = 0.5))

        val resumable = repository.requeue()

        assertEquals(listOf("a"), resumable.map { it.id })
        val recovered = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.QUEUED, recovered.status)
        assertEquals(0.0, recovered.progress, 1e-9)
    }

    @Test
    fun requeueKeepsQueuedTasks() = runTest {
        repository.create(task("a", createdAt = 1L, status = TaskStatus.QUEUED))

        val resumable = repository.requeue()

        assertEquals(listOf("a"), resumable.map { it.id })
        assertEquals(TaskStatus.QUEUED, repository.get("a")?.status)
    }

    @Test
    fun requeueKeepsQueuedTaskProgressButResetsRunningProgress() = runTest {
        repository.create(task("queued", createdAt = 1L, status = TaskStatus.QUEUED, progress = 0.25))
        repository.create(task("running", createdAt = 2L, status = TaskStatus.DOWNLOADING, progress = 0.5))

        repository.requeue()

        assertEquals(0.25, requireNotNull(repository.get("queued")).progress, 1e-9)
        assertEquals(0.0, requireNotNull(repository.get("running")).progress, 1e-9)
    }

    @Test
    fun requeueIsIdempotent() = runTest {
        repository.create(task("running", createdAt = 1L, status = TaskStatus.DOWNLOADING, progress = 0.7))

        val first = repository.requeue()
        val second = repository.requeue()

        assertEquals(listOf("running"), first.map { it.id })
        assertEquals(listOf("running"), second.map { it.id })
        assertEquals(TaskStatus.QUEUED, repository.get("running")?.status)
        assertEquals(0.0, requireNotNull(repository.get("running")).progress, 1e-9)
    }

    @Test
    fun requeueExcludesFinishedAndFailedTasks() = runTest {
        repository.create(task("done", createdAt = 1L, status = TaskStatus.COMPLETED))
        repository.create(task("failed", createdAt = 2L, status = TaskStatus.FAILED))
        repository.create(task("cancelled", createdAt = 3L, status = TaskStatus.CANCELLED))

        val resumable = repository.requeue()

        assertTrue(resumable.isEmpty())
        assertEquals(TaskStatus.COMPLETED, repository.get("done")?.status)
        assertEquals(TaskStatus.FAILED, repository.get("failed")?.status)
        assertEquals(TaskStatus.CANCELLED, repository.get("cancelled")?.status)
    }

    @Test
    fun requeueReturnsResumableTasksInCreationOrder() = runTest {
        repository.create(task("running", createdAt = 2L, status = TaskStatus.DOWNLOADING))
        repository.create(task("queued", createdAt = 1L, status = TaskStatus.QUEUED))

        val resumable = repository.requeue()

        assertEquals(listOf("queued", "running"), resumable.map { it.id })
    }

    @Test
    fun requeueOnEmptyDatabaseReturnsEmptyList() = runTest {
        assertTrue(repository.requeue().isEmpty())
    }

    private fun task(
        id: String,
        createdAt: Long,
        title: String? = "Title $id",
        status: TaskStatus = TaskStatus.QUEUED,
        thumbnailUrl: String? = null,
        progress: Double = 0.0,
    ) = DownloadTask(
        id = id,
        url = "https://example.test/$id",
        title = title,
        thumbnailUrl = thumbnailUrl,
        createdAt = createdAt,
        status = status,
        progress = progress,
    )
}
