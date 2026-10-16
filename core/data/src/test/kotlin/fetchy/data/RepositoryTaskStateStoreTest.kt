package fetchy.data

import fetchy.model.DownloadTask
import fetchy.model.ErrorClass
import fetchy.model.TaskStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RepositoryTaskStateStoreTest {

    private val database = FetchyDatabaseFactory.inMemory()
    private val repository = DownloadRepository(database)
    private val history = SqlDelightHistoryRepository(database)
    private val store = RepositoryTaskStateStore(repository)

    @Test
    fun markDownloadingSetsStatusAndZeroProgress() = runTest {
        repository.create(task("a"))

        store.markDownloading("a")

        val updated = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.DOWNLOADING, updated.status)
        assertEquals(0.0, updated.progress, 1e-9)
    }

    @Test
    fun updateProgressKeepsStatus() = runTest {
        repository.create(task("a", status = TaskStatus.DOWNLOADING))

        store.updateProgress("a", 0.375)

        val updated = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.DOWNLOADING, updated.status)
        assertEquals(0.375, updated.progress, 1e-9)
    }

    @Test
    fun markCompletedSetsFullProgressAndFinishedAt() = runTest {
        repository.create(task("a", status = TaskStatus.DOWNLOADING))

        store.markCompleted("a", progress = 1.0, finishedAt = 555L)

        val updated = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.COMPLETED, updated.status)
        assertEquals(1.0, updated.progress, 1e-9)
        assertEquals(555L, updated.finishedAt)
    }

    @Test
    fun markFailedStoresErrorClassAndProgress() = runTest {
        repository.create(task("a", status = TaskStatus.DOWNLOADING))

        store.markFailed("a", progress = 0.5, errorClass = ErrorClass.NETWORK, finishedAt = 777L)

        val updated = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.FAILED, updated.status)
        assertEquals(0.5, updated.progress, 1e-9)
        assertEquals(ErrorClass.NETWORK, updated.errorClass)
        assertEquals(777L, updated.finishedAt)
    }

    @Test
    fun markFailedAllowsNullErrorClass() = runTest {
        repository.create(task("a", status = TaskStatus.DOWNLOADING))

        store.markFailed("a", progress = 0.0, errorClass = null, finishedAt = 12L)

        val updated = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.FAILED, updated.status)
        assertNull(updated.errorClass)
    }

    @Test
    fun markCancelledSetsStatusAndFinishedAt() = runTest {
        repository.create(task("a", status = TaskStatus.DOWNLOADING))

        store.markCancelled("a", progress = 0.25, finishedAt = 999L)

        val updated = requireNotNull(repository.get("a"))
        assertEquals(TaskStatus.CANCELLED, updated.status)
        assertEquals(0.25, updated.progress, 1e-9)
        assertEquals(999L, updated.finishedAt)
    }

    @Test
    fun markCompletedArchivesTaskIntoHistory() = runTest {
        repository.create(task("a", status = TaskStatus.DOWNLOADING))

        store.markCompleted("a", progress = 1.0, finishedAt = 555L)

        val archived = requireNotNull(history.get("a"))
        assertEquals(TaskStatus.COMPLETED, archived.status)
        assertEquals(555L, archived.finishedAt)
    }

    private fun task(id: String, status: TaskStatus = TaskStatus.QUEUED): DownloadTask =
        DownloadTask(
            id = id,
            url = "https://example.test/$id",
            createdAt = 1L,
            status = status,
        )
}
