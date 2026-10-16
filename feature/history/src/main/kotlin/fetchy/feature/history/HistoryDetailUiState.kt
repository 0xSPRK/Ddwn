package fetchy.feature.history

import fetchy.model.HistoryEntry

data class HistoryDetailUiState(
    val entry: HistoryEntry? = null,
    val loading: Boolean = true,
    val command: String? = null,
    val logText: String? = null,
    val logLoading: Boolean = false,
    val logTruncated: Boolean = false,
    val logExpanded: Boolean = false,
)
