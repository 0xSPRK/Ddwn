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
import androidx.compose.ui.Modifier
import fetchy.designsystem.components.FetchyEmptyState
import fetchy.designsystem.components.FetchySectionHeader
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.queue.components.TaskCard
import fetchy.feature.queue.generated.resources.Res
import fetchy.feature.queue.generated.resources.queue_count
import fetchy.feature.queue.generated.resources.queue_empty_description
import fetchy.feature.queue.generated.resources.queue_empty_title
import fetchy.feature.queue.generated.resources.queue_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    modifier: Modifier = Modifier,
) {
    val states by viewModel.tasks.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(FetchySpacing.lg)) {
        FetchySectionHeader(
            title = stringResource(Res.string.queue_title),
            subtitle = stringResource(Res.string.queue_count, states.size),
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
                    TaskCard(state = state)
                }
            }
        }
    }
}
