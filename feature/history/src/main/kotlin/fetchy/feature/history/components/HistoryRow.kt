package fetchy.feature.history.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import fetchy.designsystem.components.FetchyOutlinedCard
import fetchy.designsystem.components.FetchyTextButton
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.history.formatHistoryTimestamp
import fetchy.feature.history.generated.resources.Res
import fetchy.feature.history.generated.resources.history_delete
import fetchy.feature.history.generated.resources.history_status_cancelled
import fetchy.feature.history.generated.resources.history_status_completed
import fetchy.feature.history.generated.resources.history_status_downloading
import fetchy.feature.history.generated.resources.history_status_failed
import fetchy.feature.history.generated.resources.history_status_queued
import fetchy.model.HistoryEntry
import fetchy.model.TaskStatus
import org.jetbrains.compose.resources.stringResource

@Composable
fun HistoryRow(
    entry: HistoryEntry,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FetchyOutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FetchySpacing.md),
            horizontalArrangement = Arrangement.spacedBy(FetchySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FetchySpacing.xs),
            ) {
                Text(
                    text = entry.title ?: entry.url,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(statusLabel(entry.status)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColor(entry.status),
                )
                Text(
                    text = formatHistoryTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FetchyTextButton(
                text = stringResource(Res.string.history_delete),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun statusColor(status: TaskStatus) = when (status) {
    TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    TaskStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: TaskStatus) = when (status) {
    TaskStatus.COMPLETED -> Res.string.history_status_completed
    TaskStatus.FAILED -> Res.string.history_status_failed
    TaskStatus.CANCELLED -> Res.string.history_status_cancelled
    TaskStatus.QUEUED -> Res.string.history_status_queued
    TaskStatus.DOWNLOADING -> Res.string.history_status_downloading
}
