package fetchy.feature.history

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import fetchy.data.HistoryRepository
import fetchy.model.HistoryEntry
import fetchy.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @Test
    fun `empty repository reports an empty state`() = runTest {
        val viewModel = createViewModel(FakeHistoryRepository())

        viewModel.state.test {
            val state = awaitUntil { it.isEmpty }
            assertTrue(state.entries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `entries are ordered by creation time descending`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(entry("old", createdAt = 1L), entry("new", createdAt = 2L)),
        )
        val viewModel = createViewModel(repository)

        viewModel.state.test {
            assertEquals(listOf("new", "old"), awaitUntil { it.entries.isNotEmpty() }.entries.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `search filters by title`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(entry("a", title = "Cats", createdAt = 1L), entry("b", title = "Dogs", createdAt = 2L)),
        )
        val viewModel = createViewModel(repository)

        viewModel.onQueryChange("cats")

        viewModel.state.test {
            assertEquals(listOf("a"), awaitUntil { it.entries.size == 1 }.entries.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `search filters by url`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(
                entry("a", url = "https://example.test/alpha", createdAt = 1L),
                entry("b", url = "https://example.test/beta", createdAt = 2L),
            ),
        )
        val viewModel = createViewModel(repository)

        viewModel.onQueryChange("beta")

        viewModel.state.test {
            assertEquals(listOf("b"), awaitUntil { it.entries.size == 1 }.entries.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `status filter narrows the results`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(
                entry("ok", status = TaskStatus.COMPLETED, createdAt = 2L),
                entry("bad", status = TaskStatus.FAILED, createdAt = 1L),
            ),
        )
        val viewModel = createViewModel(repository)

        viewModel.onStatusFilterChange(HistoryStatusFilter.FAILED)

        viewModel.state.test {
            val state = awaitUntil { it.entries.size == 1 }
            assertEquals(listOf("bad"), state.entries.map { it.id })
            assertEquals(HistoryStatusFilter.FAILED, state.statusFilter)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `period filter excludes entries older than the window`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(
                entry("old", createdAt = FIXED_NOW - 2L * DAY_MILLIS),
                entry("recent", createdAt = FIXED_NOW - 1_000L),
            ),
        )
        val viewModel = createViewModel(repository)

        viewModel.onPeriodFilterChange(HistoryPeriodFilter.LAST_DAY)

        viewModel.state.test {
            val state = awaitUntil { it.entries.size == 1 }
            assertEquals(listOf("recent"), state.entries.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `pagination reveals one page at a time`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(entry("e1", createdAt = 1L), entry("e2", createdAt = 2L), entry("e3", createdAt = 3L)),
        )
        val viewModel = createViewModel(repository, pageSize = 2L)

        viewModel.state.test {
            val first = awaitUntil { it.entries.isNotEmpty() }
            assertEquals(listOf("e3", "e2"), first.entries.map { it.id })
            assertTrue(first.canLoadMore)

            viewModel.loadMore()
            val second = awaitUntil { it.entries.size == 3 }
            assertEquals(listOf("e3", "e2", "e1"), second.entries.map { it.id })
            assertFalse(second.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `changing the query resets pagination`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(
                entry("a1", title = "match", createdAt = 1L),
                entry("a2", title = "match", createdAt = 2L),
                entry("b", title = "other", createdAt = 3L),
            ),
        )
        val viewModel = createViewModel(repository, pageSize = 2L)

        viewModel.state.test {
            assertEquals(listOf("b", "a2"), awaitUntil { it.entries.size == 2 }.entries.map { it.id })

            viewModel.loadMore()
            awaitUntil { it.entries.size == 3 }

            viewModel.onQueryChange("match")

            val replaced = awaitUntil { it.entries.map { entry -> entry.id } == listOf("a2", "a1") }
            assertTrue(replaced.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `load more is ignored once the last page is reached`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(entry("e1", createdAt = 1L), entry("e2", createdAt = 2L), entry("e3", createdAt = 3L)),
        )
        val viewModel = createViewModel(repository, pageSize = 2L)

        viewModel.state.test {
            awaitUntil { it.entries.size == 2 }
            viewModel.loadMore()
            awaitUntil { it.entries.size == 3 }

            viewModel.loadMore()

            assertEquals(3, viewModel.state.value.entries.size)
            assertFalse(viewModel.state.value.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `delete removes the entry from the list`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(entry("a", createdAt = 1L), entry("b", createdAt = 2L)),
        )
        val viewModel = createViewModel(repository)

        viewModel.state.test {
            awaitUntil { it.entries.size == 2 }

            viewModel.delete("a")

            val state = awaitUntil { it.entries.size == 1 }
            assertEquals(listOf("b"), state.entries.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `clear filters restores the defaults`() = runTest {
        val repository = FakeHistoryRepository(
            listOf(
                entry("a", title = "match", status = TaskStatus.FAILED, createdAt = FIXED_NOW),
                entry("b", title = "other", status = TaskStatus.COMPLETED, createdAt = FIXED_NOW),
            ),
        )
        val viewModel = createViewModel(repository)

        viewModel.onQueryChange("match")
        viewModel.onStatusFilterChange(HistoryStatusFilter.FAILED)
        viewModel.onPeriodFilterChange(HistoryPeriodFilter.LAST_WEEK)

        viewModel.state.test {
            awaitUntil { it.entries.size == 1 }

            viewModel.clearFilters()

            val state = awaitUntil { it.entries.size == 2 }
            assertEquals(HistoryStatusFilter.ALL, state.statusFilter)
            assertEquals(HistoryPeriodFilter.ALL, state.periodFilter)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    @Test
    fun `state exposes the current query`() = runTest {
        val repository = FakeHistoryRepository(listOf(entry("a", createdAt = 1L)))
        val viewModel = createViewModel(repository)

        viewModel.onQueryChange("cats")

        viewModel.state.test {
            assertEquals("cats", awaitUntil { it.query == "cats" }.query)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.close()
    }

    private fun TestScope.createViewModel(
        repository: HistoryRepository,
        pageSize: Long = HistoryViewModel.DEFAULT_PAGE_SIZE,
        clock: () -> Long = { FIXED_NOW },
    ): HistoryViewModel = HistoryViewModel(
        repository = repository,
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
        pageSize = pageSize,
        clock = clock,
    )

    private suspend fun ReceiveTurbine<HistoryUiState>.awaitUntil(
        predicate: (HistoryUiState) -> Boolean,
    ): HistoryUiState {
        var item = awaitItem()
        while (!predicate(item)) {
            item = awaitItem()
        }
        return item
    }

    private fun entry(
        id: String,
        title: String? = "Title $id",
        url: String = "https://example.test/$id",
        status: TaskStatus = TaskStatus.COMPLETED,
        createdAt: Long,
    ) = HistoryEntry(
        id = id,
        url = url,
        title = title,
        status = status,
        createdAt = createdAt,
        finishedAt = createdAt,
    )

    private companion object {
        const val FIXED_NOW: Long = 1_000_000_000_000L
        const val DAY_MILLIS: Long = 86_400_000L
    }
}

private class FakeHistoryRepository(
    initial: List<HistoryEntry> = emptyList(),
) : HistoryRepository {

    private val store: MutableStateFlow<List<HistoryEntry>> = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<HistoryEntry>> =
        store.map { entries -> entries.sortedByDescending { it.createdAt } }

    override fun search(
        query: String,
        status: TaskStatus?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Long,
        offset: Long,
    ): Flow<List<HistoryEntry>> = store.map { entries ->
        entries.asSequence()
            .filter { matchesQuery(it, query) }
            .filter { status == null || it.status == status }
            .filter { fromMillis == null || it.createdAt >= fromMillis }
            .filter { toMillis == null || it.createdAt <= toMillis }
            .sortedByDescending { it.createdAt }
            .drop(offset.toInt())
            .take(limit.toInt())
            .toList()
    }

    override suspend fun get(id: String): HistoryEntry? = store.value.firstOrNull { it.id == id }

    override suspend fun insert(entry: HistoryEntry) {
        store.update { entries -> entries + entry }
    }

    override suspend fun delete(id: String) {
        store.update { entries -> entries.filterNot { it.id == id } }
    }

    override suspend fun deleteAll() {
        store.value = emptyList()
    }

    override suspend fun count(): Long = store.value.size.toLong()

    private fun matchesQuery(entry: HistoryEntry, query: String): Boolean =
        query.isEmpty() ||
            entry.url.contains(query, ignoreCase = true) ||
            entry.title?.contains(query, ignoreCase = true) == true
}
