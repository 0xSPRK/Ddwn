package fetchy.feature.history

import fetchy.data.HistoryRepository
import fetchy.model.HistoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: HistoryRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val pageSize: Long = DEFAULT_PAGE_SIZE,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {

    private val criteria: MutableStateFlow<HistoryCriteria> = MutableStateFlow(HistoryCriteria())

    private val page: Flow<HistoryPage> = criteria
        .flatMapLatest { request ->
            val from: Long? = request.period.windowMillis?.let { window -> clock() - window }
            repository.search(
                query = request.query,
                status = request.status.status,
                fromMillis = from,
                toMillis = null,
                limit = pageSize * request.pages,
                offset = 0L,
            ).map { entries -> HistoryPage(request, entries) }
        }

    val state: StateFlow<HistoryUiState> = page
        .map { it.toUiState(pageSize) }
        .stateIn(scope, SharingStarted.Eagerly, HistoryUiState())

    fun onQueryChange(value: String) {
        criteria.update { it.copy(query = value, pages = FIRST_PAGE) }
    }

    fun onStatusFilterChange(filter: HistoryStatusFilter) {
        criteria.update { it.copy(status = filter, pages = FIRST_PAGE) }
    }

    fun onPeriodFilterChange(filter: HistoryPeriodFilter) {
        criteria.update { it.copy(period = filter, pages = FIRST_PAGE) }
    }

    fun clearFilters() {
        criteria.update { HistoryCriteria() }
    }

    fun loadMore() {
        if (state.value.canLoadMore) {
            criteria.update { it.copy(pages = it.pages + 1L) }
        }
    }

    fun delete(id: String) {
        scope.launch { repository.delete(id) }
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Long = 20L
        private const val FIRST_PAGE: Long = 1L
    }
}

private data class HistoryCriteria(
    val query: String = "",
    val status: HistoryStatusFilter = HistoryStatusFilter.ALL,
    val period: HistoryPeriodFilter = HistoryPeriodFilter.ALL,
    val pages: Long = 1L,
)

private data class HistoryPage(
    val criteria: HistoryCriteria,
    val entries: List<HistoryEntry>,
)

private fun HistoryPage.toUiState(pageSize: Long): HistoryUiState = HistoryUiState(
    entries = entries,
    statusFilter = criteria.status,
    periodFilter = criteria.period,
    canLoadMore = entries.size.toLong() >= pageSize * criteria.pages,
    isEmpty = entries.isEmpty(),
)
