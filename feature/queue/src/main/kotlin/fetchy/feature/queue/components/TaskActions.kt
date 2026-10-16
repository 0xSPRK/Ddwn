package fetchy.feature.queue.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fetchy.designsystem.components.FetchyTextButton
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.queue.generated.resources.Res
import fetchy.feature.queue.generated.resources.queue_action_cancel
import fetchy.feature.queue.generated.resources.queue_action_restart
import fetchy.feature.queue.generated.resources.queue_action_retry
import fetchy.model.TaskStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal enum class QueueAction { CANCEL, RETRY, RESTART }

private fun actionsFor(status: TaskStatus): List<QueueAction> = when (status) {
    TaskStatus.DOWNLOADING, TaskStatus.QUEUED -> listOf(QueueAction.RESTART, QueueAction.CANCEL)
    TaskStatus.FAILED, TaskStatus.CANCELLED -> listOf(QueueAction.RETRY)
    TaskStatus.COMPLETED -> emptyList()
}

@Composable
fun TaskActions(
    status: TaskStatus,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FetchySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actionsFor(status).forEach { action ->
            val label: StringResource
            val onClick: () -> Unit
            when (action) {
                QueueAction.CANCEL -> {
                    label = Res.string.queue_action_cancel
                    onClick = onCancel
                }

                QueueAction.RETRY -> {
                    label = Res.string.queue_action_retry
                    onClick = onRetry
                }

                QueueAction.RESTART -> {
                    label = Res.string.queue_action_restart
                    onClick = onRestart
                }
            }
            FetchyTextButton(
                text = stringResource(label),
                onClick = onClick,
            )
        }
    }
}

fun hasTaskActions(status: TaskStatus): Boolean = actionsFor(status).isNotEmpty()
