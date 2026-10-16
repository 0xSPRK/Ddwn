package fetchy.feature.history.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fetchy.designsystem.components.FetchyButton
import fetchy.designsystem.components.FetchyCircularProgress
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.history.generated.resources.Res
import fetchy.feature.history.generated.resources.history_detail_log_load_more
import org.jetbrains.compose.resources.stringResource

private val LOG_VIEW_HEIGHT = 320.dp

@Composable
fun LogView(
    text: String?,
    truncated: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FetchySpacing.sm),
    ) {
        if (text == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FetchyCircularProgress()
            }
        } else {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = LOG_VIEW_HEIGHT)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(FetchySpacing.sm),
                )
            }
        }
        if (loading && text != null) {
            FetchyCircularProgress()
        }
        if (truncated) {
            FetchyButton(
                text = stringResource(Res.string.history_detail_log_load_more),
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
