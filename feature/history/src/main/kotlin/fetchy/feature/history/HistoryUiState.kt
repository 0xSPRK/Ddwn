package fetchy.feature.history

import fetchy.model.HistoryEntry

data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val statusFilter: HistoryStatusFilter = HistoryStatusFilter.ALL,
    val periodFilter: HistoryPeriodFilter = HistoryPeriodFilter.ALL,
    val canLoadMore: Boolean = false,
    val isEmpty: Boolean = false,
)
