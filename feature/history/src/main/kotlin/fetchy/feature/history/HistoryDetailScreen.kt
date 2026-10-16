package fetchy.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fetchy.designsystem.components.FetchyButton
import fetchy.designsystem.components.FetchyCircularProgress
import fetchy.designsystem.components.FetchyEmptyState
import fetchy.designsystem.components.FetchyOutlinedCard
import fetchy.designsystem.components.FetchySectionHeader
import fetchy.designsystem.components.FetchyTextButton
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.history.components.LogView
import fetchy.feature.history.components.statusColor
import fetchy.feature.history.components.statusLabel
import fetchy.feature.history.generated.resources.Res
import fetchy.feature.history.generated.resources.history_detail_back
import fetchy.feature.history.generated.resources.history_detail_command
import fetchy.feature.history.generated.resources.history_detail_command_hint
import fetchy.feature.history.generated.resources.history_detail_created
import fetchy.feature.history.generated.resources.history_detail_duration
import fetchy.feature.history.generated.resources.history_detail_error
import fetchy.feature.history.generated.resources.history_detail_file
import fetchy.feature.history.generated.resources.history_detail_file_none
import fetchy.feature.history.generated.resources.history_detail_finished
import fetchy.feature.history.generated.resources.history_detail_log
import fetchy.feature.history.generated.resources.history_detail_log_error
import fetchy.feature.history.generated.resources.history_detail_log_missing
import fetchy.feature.history.generated.resources.history_detail_not_found
import fetchy.feature.history.generated.resources.history_detail_open_folder
import fetchy.feature.history.generated.resources.history_detail_show_log
import fetchy.feature.history.generated.resources.history_detail_status
import fetchy.feature.history.generated.resources.history_detail_url
import fetchy.feature.history.generated.resources.history_error_class
import fetchy.model.ErrorClass
import org.jetbrains.compose.resources.stringResource

@Composable
fun HistoryDetailScreen(
    viewModel: HistoryDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state: HistoryDetailUiState by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FetchySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(FetchySpacing.md),
    ) {
        FetchyTextButton(text = stringResource(Res.string.history_detail_back), onClick = onBack)

        when {
            state.loading -> FetchyCircularProgress()
            state.entry == null -> FetchyEmptyState(
                title = stringResource(Res.string.history_detail_not_found),
                modifier = Modifier.fillMaxWidth(),
            )

            else -> {
                val entry = requireNotNull(state.entry)
                FetchySectionHeader(title = entry.title ?: entry.url)

                FetchyOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FetchySpacing.md),
                        verticalArrangement = Arrangement.spacedBy(FetchySpacing.md),
                    ) {
                        DetailRow(label = stringResource(Res.string.history_detail_url), value = entry.url)
                        DetailRow(
                            label = stringResource(Res.string.history_detail_status),
                            value = stringResource(statusLabel(entry.status)),
                            valueColor = statusColor(entry.status),
                        )
                        DetailRow(
                            label = stringResource(Res.string.history_detail_created),
                            value = formatHistoryTimestamp(entry.createdAt),
                        )
                        entry.finishedAt?.let { finishedAt ->
                            DetailRow(
                                label = stringResource(Res.string.history_detail_finished),
                                value = formatHistoryTimestamp(finishedAt),
                            )
                        }
                        entry.durationMs?.let { durationMs ->
                            DetailRow(
                                label = stringResource(Res.string.history_detail_duration),
                                value = formatDuration(durationMs),
                            )
                        }
                        val filePath: String? = entry.filePath
                        DetailRow(
                            label = stringResource(Res.string.history_detail_file),
                            value = filePath ?: stringResource(Res.string.history_detail_file_none),
                        )
                        if (filePath != null) {
                            FetchyButton(
                                text = stringResource(Res.string.history_detail_open_folder),
                                onClick = viewModel::openFolder,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                entry.errorClass?.let { errorClass ->
                    FetchyOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(FetchySpacing.md),
                            verticalArrangement = Arrangement.spacedBy(FetchySpacing.sm),
                        ) {
                            Text(
                                text = stringResource(Res.string.history_detail_error),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(errorMessage(errorClass)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(FetchySpacing.sm)) {
                                Text(
                                    text = stringResource(Res.string.history_error_class),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(errorClassLabel(errorClass)),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                            if (errorClass == ErrorClass.DRM_PROTECTED && entry.logPath != null) {
                                FetchyTextButton(
                                    text = stringResource(Res.string.history_detail_show_log),
                                    onClick = viewModel::showLog,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                state.command?.let { command ->
                    FetchySectionHeader(
                        title = stringResource(Res.string.history_detail_command),
                        subtitle = stringResource(Res.string.history_detail_command_hint),
                    )
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                FetchySectionHeader(title = stringResource(Res.string.history_detail_log))
                when {
                    entry.logPath == null -> Text(
                        text = stringResource(Res.string.history_detail_log_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    !state.logExpanded -> FetchyTextButton(
                        text = stringResource(Res.string.history_detail_show_log),
                        onClick = viewModel::showLog,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.logError -> Text(
                        text = stringResource(Res.string.history_detail_log_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> LogView(
                        text = state.logText,
                        truncated = state.logTruncated,
                        loading = state.logLoading,
                        onLoadMore = viewModel::showLogMore,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FetchySpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
