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
import org.jetbrains.compose.resources.stringResource

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
        when (status) {
            TaskStatus.DOWNLOADING, TaskStatus.QUEUED -> {
                FetchyTextButton(
                    text = stringResource(Res.string.queue_action_restart),
                    onClick = onRestart,
                )
                FetchyTextButton(
                    text = stringResource(Res.string.queue_action_cancel),
                    onClick = onCancel,
                )
            }

            TaskStatus.FAILED, TaskStatus.CANCELLED ->
                FetchyTextButton(
                    text = stringResource(Res.string.queue_action_retry),
                    onClick = onRetry,
                )

            TaskStatus.COMPLETED -> Unit
        }
    }
}

fun hasTaskActions(status: TaskStatus): Boolean = when (status) {
    TaskStatus.DOWNLOADING, TaskStatus.QUEUED, TaskStatus.FAILED, TaskStatus.CANCELLED -> true
    TaskStatus.COMPLETED -> false
}
