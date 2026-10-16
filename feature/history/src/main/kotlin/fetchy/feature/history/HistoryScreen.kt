package fetchy.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fetchy.designsystem.components.FetchyAlertDialog
import fetchy.designsystem.components.FetchyButton
import fetchy.designsystem.components.FetchyEmptyState
import fetchy.designsystem.components.FetchyFilterChip
import fetchy.designsystem.components.FetchySectionHeader
import fetchy.designsystem.components.FetchyTextField
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.history.components.HistoryRow
import fetchy.feature.history.generated.resources.Res
import fetchy.feature.history.generated.resources.history_delete_confirm
import fetchy.feature.history.generated.resources.history_delete_dismiss
import fetchy.feature.history.generated.resources.history_delete_message
import fetchy.feature.history.generated.resources.history_delete_title
import fetchy.feature.history.generated.resources.history_empty_description
import fetchy.feature.history.generated.resources.history_empty_title
import fetchy.feature.history.generated.resources.history_load_more
import fetchy.feature.history.generated.resources.history_period_all
import fetchy.feature.history.generated.resources.history_period_day
import fetchy.feature.history.generated.resources.history_period_month
import fetchy.feature.history.generated.resources.history_period_week
import fetchy.feature.history.generated.resources.history_search_label
import fetchy.feature.history.generated.resources.history_search_placeholder
import fetchy.feature.history.generated.resources.history_status_all
import fetchy.feature.history.generated.resources.history_status_cancelled
import fetchy.feature.history.generated.resources.history_status_completed
import fetchy.feature.history.generated.resources.history_status_failed
import fetchy.feature.history.generated.resources.history_title
import fetchy.model.HistoryEntry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val state: HistoryUiState by viewModel.state.collectAsState()
    var query: String by remember { mutableStateOf(state.query) }
    var pendingDelete: HistoryEntry? by remember { mutableStateOf(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(FetchySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(FetchySpacing.md),
    ) {
        FetchySectionHeader(title = stringResource(Res.string.history_title))

        FetchyTextField(
            value = query,
            onValueChange = { value ->
                query = value
                viewModel.onQueryChange(value)
            },
            label = stringResource(Res.string.history_search_label),
            placeholder = stringResource(Res.string.history_search_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(FetchySpacing.sm)) {
            HistoryStatusFilter.entries.forEach { filter ->
                FetchyFilterChip(
                    label = stringResource(filter.label()),
                    selected = state.statusFilter == filter,
                    onClick = { viewModel.onStatusFilterChange(filter) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(FetchySpacing.sm)) {
            HistoryPeriodFilter.entries.forEach { filter ->
                FetchyFilterChip(
                    label = stringResource(filter.label()),
                    selected = state.periodFilter == filter,
                    onClick = { viewModel.onPeriodFilterChange(filter) },
                )
            }
        }

        if (state.isEmpty) {
            FetchyEmptyState(
                title = stringResource(Res.string.history_empty_title),
                description = stringResource(Res.string.history_empty_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(FetchySpacing.sm),
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onDelete = { pendingDelete = entry },
                    )
                }
                if (state.canLoadMore) {
                    item {
                        FetchyButton(
                            text = stringResource(Res.string.history_load_more),
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    val deleting: HistoryEntry? = pendingDelete
    if (deleting != null) {
        FetchyAlertDialog(
            title = stringResource(Res.string.history_delete_title),
            text = stringResource(Res.string.history_delete_message, deleting.title ?: deleting.url),
            confirmLabel = stringResource(Res.string.history_delete_confirm),
            dismissLabel = stringResource(Res.string.history_delete_dismiss),
            onConfirm = {
                viewModel.delete(deleting.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun HistoryStatusFilter.label(): StringResource = when (this) {
    HistoryStatusFilter.ALL -> Res.string.history_status_all
    HistoryStatusFilter.COMPLETED -> Res.string.history_status_completed
    HistoryStatusFilter.FAILED -> Res.string.history_status_failed
    HistoryStatusFilter.CANCELLED -> Res.string.history_status_cancelled
}

private fun HistoryPeriodFilter.label(): StringResource = when (this) {
    HistoryPeriodFilter.ALL -> Res.string.history_period_all
    HistoryPeriodFilter.LAST_DAY -> Res.string.history_period_day
    HistoryPeriodFilter.LAST_WEEK -> Res.string.history_period_week
    HistoryPeriodFilter.LAST_MONTH -> Res.string.history_period_month
}
