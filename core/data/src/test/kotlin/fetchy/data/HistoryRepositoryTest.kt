package fetchy.data

import fetchy.model.HistoryEntry
import fetchy.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HistoryRepositoryTest {

    private val repository: HistoryRepository = SqlDelightHistoryRepository(FetchyDatabaseFactory.inMemory())

    @Test
    fun insertThenGetRoundTripsAllFields() = runTest {
        val entry = entry(
            id = "h1",
            title = "Clip",
            status = TaskStatus.COMPLETED,
            createdAt = 5L,
            filePath = "/out/clip.mp4",
            durationMs = 1234L,
        )

        repository.insert(entry)

        assertEquals(entry, repository.get("h1"))
    }

    @Test
    fun observeAllOrdersByCreatedAtDescending() = runTest {
        repository.insert(entry("old", createdAt = 1L))
        repository.insert(entry("new", createdAt = 2L))

        val ids = repository.observeAll().first().map { it.id }

        assertEquals(listOf("new", "old"), ids)
    }

    @Test
    fun searchMatchesTitleAndUrl() = runTest {
        repository.insert(entry("a", title = "Cats", url = "https://example.test/a", createdAt = 1L))
        repository.insert(entry("b", title = "Dogs", url = "https://example.test/b", createdAt = 2L))

        val byTitle = repository.search("Cats", limit = 10, offset = 0).first().map { it.id }
        val byUrl = repository.search("/b", limit = 10, offset = 0).first().map { it.id }

        assertEquals(listOf("a"), byTitle)
        assertEquals(listOf("b"), byUrl)
    }

    @Test
    fun searchRespectsLimitAndOffset() = runTest {
        repository.insert(entry("x1", title = "same", createdAt = 1L))
        repository.insert(entry("x2", title = "same", createdAt = 2L))
        repository.insert(entry("x3", title = "same", createdAt = 3L))

        val page = repository.search("same", limit = 1, offset = 1).first().map { it.id }

        assertEquals(listOf("x2"), page)
    }

    @Test
    fun searchFiltersByStatus() = runTest {
        repository.insert(entry("ok", status = TaskStatus.COMPLETED, createdAt = 2L))
        repository.insert(entry("bad", status = TaskStatus.FAILED, createdAt = 1L))

        val failed = repository.search("", status = TaskStatus.FAILED, limit = 10).first().map { it.id }

        assertEquals(listOf("bad"), failed)
    }

    @Test
    fun searchFiltersByCreatedAtRange() = runTest {
        repository.insert(entry("old", createdAt = 100L))
        repository.insert(entry("recent", createdAt = 900L))

        val windowed = repository.search("", fromMillis = 500L, limit = 10).first().map { it.id }
        val bounded = repository.search("", toMillis = 500L, limit = 10).first().map { it.id }

        assertEquals(listOf("recent"), windowed)
        assertEquals(listOf("old"), bounded)
    }

    @Test
    fun deleteAndCountAndClear() = runTest {
        repository.insert(entry("a", createdAt = 1L))
        repository.insert(entry("b", createdAt = 2L))

        repository.delete("a")
        assertNull(repository.get("a"))
        assertEquals(1L, repository.count())

        repository.deleteAll()
        assertEquals(0L, repository.count())
    }

    @Test
    fun searchTreatsLikeWildcardsLiterally() = runTest {
        repository.insert(entry("percent", url = "https://example.test/100%25", createdAt = 1L))
        repository.insert(entry("thousand", url = "https://example.test/10000", createdAt = 2L))

        val matches = repository.search("100%", limit = 10).first().map { it.id }

        assertEquals(listOf("percent"), matches)
    }

    private fun entry(
        id: String,
        title: String? = "Title $id",
        url: String = "https://example.test/$id",
        status: TaskStatus = TaskStatus.COMPLETED,
        createdAt: Long,
        filePath: String? = null,
        durationMs: Long? = null,
    ) = HistoryEntry(
        id = id,
        url = url,
        title = title,
        status = status,
        createdAt = createdAt,
        finishedAt = createdAt,
        filePath = filePath,
        durationMs = durationMs,
    )
}
