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

    private fun task(
        id: String,
        createdAt: Long,
        title: String? = "Title $id",
        status: TaskStatus = TaskStatus.QUEUED,
        thumbnailUrl: String? = null,
    ) = DownloadTask(
        id = id,
        url = "https://example.test/$id",
        title = title,
        thumbnailUrl = thumbnailUrl,
        createdAt = createdAt,
        status = status,
    )
}
