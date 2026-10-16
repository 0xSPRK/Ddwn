package fetchy.feature.queue.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fetchy.designsystem.components.FetchyAssistChip
import fetchy.designsystem.components.FetchyLinearProgress
import fetchy.designsystem.components.FetchyOutlinedCard
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.queue.QueueTaskUiState
import fetchy.feature.queue.formatEta
import fetchy.feature.queue.formatSpeed
import fetchy.feature.queue.generated.resources.Res
import fetchy.feature.queue.generated.resources.queue_eta
import fetchy.feature.queue.generated.resources.queue_playlist_item
import fetchy.feature.queue.generated.resources.queue_select_task
import fetchy.feature.queue.generated.resources.queue_speed
import fetchy.feature.queue.generated.resources.queue_status_cancelled
import fetchy.feature.queue.generated.resources.queue_status_completed
import fetchy.feature.queue.generated.resources.queue_status_downloading
import fetchy.feature.queue.generated.resources.queue_status_failed
import fetchy.feature.queue.generated.resources.queue_status_queued
import fetchy.model.TaskStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TaskCard(
    state: QueueTaskUiState,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FetchyOutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(FetchySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selectDescription: String = stringResource(Res.string.queue_select_task, state.title)
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.semantics { contentDescription = selectDescription },
            )
            Spacer(Modifier.width(FetchySpacing.sm))
            Thumbnail(url = state.thumbnailUrl)
            Spacer(Modifier.width(FetchySpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(FetchySpacing.xs))
                FetchyLinearProgress(progress = state.progress.toFloat())
                Spacer(Modifier.height(FetchySpacing.xs))
                Metrics(state = state)
            }
            Spacer(Modifier.width(FetchySpacing.md))
            FetchyAssistChip(label = stringResource(statusLabel(state.status)), onClick = {})
        }
        if (hasTaskActions(state.status)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = FetchySpacing.md, end = FetchySpacing.md, bottom = FetchySpacing.sm),
                horizontalArrangement = Arrangement.End,
            ) {
                TaskActions(
                    status = state.status,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onRestart = onRestart,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(url: String?) {
    val shape = RoundedCornerShape(8.dp)
    if (url == null) {
        Box(
            modifier = Modifier
                .size(width = 112.dp, height = 63.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .size(width = 112.dp, height = 63.dp)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun Metrics(state: QueueTaskUiState) {
    val speed: String? = state.speedBytesPerSecond?.let { formatSpeed(it) }
    val eta: String? = state.etaSeconds?.let { formatEta(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(FetchySpacing.md)) {
        if (speed != null) {
            Text(
                text = stringResource(Res.string.queue_speed, speed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (eta != null) {
            Text(
                text = stringResource(Res.string.queue_eta, eta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.playlistIndex != null) {
            Text(
                text = stringResource(Res.string.queue_playlist_item, state.playlistIndex),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusLabel(status: TaskStatus): StringResource = when (status) {
    TaskStatus.QUEUED -> Res.string.queue_status_queued
    TaskStatus.DOWNLOADING -> Res.string.queue_status_downloading
    TaskStatus.COMPLETED -> Res.string.queue_status_completed
    TaskStatus.FAILED -> Res.string.queue_status_failed
    TaskStatus.CANCELLED -> Res.string.queue_status_cancelled
}
