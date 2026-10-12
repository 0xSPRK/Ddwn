package fetchy.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fetchy.designsystem.components.FetchyButton
import fetchy.designsystem.components.FetchyCircularProgress
import fetchy.designsystem.components.FetchyEmptyState
import fetchy.designsystem.components.FetchySectionHeader
import fetchy.designsystem.components.FetchyTextField
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.add.components.AddErrorPanel
import fetchy.feature.add.components.UrlPreviewCard
import fetchy.feature.add.generated.resources.Res
import fetchy.feature.add.generated.resources.add_error_invalid_url
import fetchy.feature.add.generated.resources.add_idle_hint
import fetchy.feature.add.generated.resources.add_parsing
import fetchy.feature.add.generated.resources.add_preview
import fetchy.feature.add.generated.resources.add_title
import fetchy.feature.add.generated.resources.add_url_label
import fetchy.feature.add.generated.resources.add_url_placeholder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddScreen(
    viewModel: AddViewModel,
    modifier: Modifier = Modifier,
    onTaskCreated: (String) -> Unit = {},
) {
    val state: AddUiState by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var logVisible: Boolean by remember(state.error) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FetchySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(FetchySpacing.lg),
    ) {
        FetchySectionHeader(title = stringResource(Res.string.add_title))

        FetchyTextField(
            value = state.url,
            onValueChange = viewModel::onUrlChange,
            label = stringResource(Res.string.add_url_label),
            placeholder = stringResource(Res.string.add_url_placeholder),
            isError = state.error is AddError.InvalidUrl,
            supportingText = if (state.error is AddError.InvalidUrl) {
                stringResource(Res.string.add_error_invalid_url)
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        FetchyButton(
            text = stringResource(Res.string.add_preview),
            onClick = { scope.launch { viewModel.preview() } },
            enabled = state.canPreview,
            loading = state.phase == AddPhase.PARSING,
        )

        when (state.phase) {
            AddPhase.IDLE -> FetchyEmptyState(
                title = stringResource(Res.string.add_idle_hint),
                modifier = Modifier.fillMaxWidth(),
            )

            AddPhase.PARSING -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FetchySpacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.add_parsing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FetchyCircularProgress()
            }

            AddPhase.ERROR -> state.error?.let { error ->
                AddErrorPanel(
                    error = error,
                    logVisible = logVisible,
                    onToggleLog = { logVisible = !logVisible },
                )
            }

            AddPhase.READY -> state.preview?.let { preview ->
                UrlPreviewCard(
                    preview = preview,
                    selectedFormat = state.selectedFormat,
                    onFormatSelected = viewModel::selectFormat,
                    downloading = state.isCreating,
                    onDownload = {
                        scope.launch {
                            val id: String? = viewModel.download()
                            if (id != null) onTaskCreated(id)
                        }
                    },
                )
            }
        }
    }
}
