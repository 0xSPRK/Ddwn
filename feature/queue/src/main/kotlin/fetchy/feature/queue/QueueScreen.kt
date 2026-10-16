package fetchy.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import fetchy.designsystem.components.FetchySectionHeader
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.queue.components.TaskCard
import fetchy.feature.queue.generated.resources.Res
import fetchy.feature.queue.generated.resources.queue_cancel_dialog_confirm
import fetchy.feature.queue.generated.resources.queue_cancel_dialog_dismiss
import fetchy.feature.queue.generated.resources.queue_cancel_dialog_message
import fetchy.feature.queue.generated.resources.queue_cancel_dialog_title
import fetchy.feature.queue.generated.resources.queue_cancel_selected
import fetchy.feature.queue.generated.resources.queue_counts
import fetchy.feature.queue.generated.resources.queue_empty_description
import fetchy.feature.queue.generated.resources.queue_empty_title
import fetchy.feature.queue.generated.resources.queue_title
import fetchy.model.TaskStatus
import org.jetbrains.compose.resources.stringResource

@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    modifier: Modifier = Modifier,
) {
    val states by viewModel.tasks.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    var cancelTarget by remember { mutableStateOf<String?>(null) }
    var confirmingCancelSelected by remember { mutableStateOf(false) }

    val activeCount: Int = states.count { it.status == TaskStatus.DOWNLOADING }
    val queuedCount: Int = states.count { it.status == TaskStatus.QUEUED }

    val trailing: (@Composable () -> Unit)? =
        if (selectedIds.isEmpty()) {
            null
        } else {
            {
                FetchyButton(
                    text = stringResource(Res.string.queue_cancel_selected, selectedIds.size),
                    onClick = { confirmingCancelSelected = true },
                )
            }
        }

    Column(modifier = modifier.fillMaxSize().padding(FetchySpacing.lg)) {
        FetchySectionHeader(
            title = stringResource(Res.string.queue_title),
            subtitle = stringResource(Res.string.queue_counts, activeCount, queuedCount),
            trailing = trailing,
        )
        if (states.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                FetchyEmptyState(
                    title = stringResource(Res.string.queue_empty_title),
                    description = stringResource(Res.string.queue_empty_description),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = FetchySpacing.md),
                verticalArrangement = Arrangement.spacedBy(FetchySpacing.sm),
            ) {
                items(items = states, key = { it.id }) { state ->
                    TaskCard(
                        state = state,
                        selected = state.id in selectedIds,
                        onToggleSelection = { viewModel.toggleSelection(state.id) },
                        onCancel = { cancelTarget = state.id },
                        onRetry = { viewModel.retry(state.id) },
                        onRestart = { viewModel.restart(state.id) },
                    )
                }
            }
        }
    }

    val target: String? = cancelTarget
    if (target != null) {
        FetchyAlertDialog(
            title = stringResource(Res.string.queue_cancel_dialog_title),
            text = stringResource(Res.string.queue_cancel_dialog_message),
            confirmLabel = stringResource(Res.string.queue_cancel_dialog_confirm),
            dismissLabel = stringResource(Res.string.queue_cancel_dialog_dismiss),
            onConfirm = {
                viewModel.cancel(target)
                cancelTarget = null
            },
            onDismiss = { cancelTarget = null },
        )
    }
    if (confirmingCancelSelected) {
        FetchyAlertDialog(
            title = stringResource(Res.string.queue_cancel_dialog_title),
            text = stringResource(Res.string.queue_cancel_dialog_message),
            confirmLabel = stringResource(Res.string.queue_cancel_dialog_confirm),
            dismissLabel = stringResource(Res.string.queue_cancel_dialog_dismiss),
            onConfirm = {
                viewModel.cancelSelected()
                confirmingCancelSelected = false
            },
            onDismiss = { confirmingCancelSelected = false },
        )
    }
}
