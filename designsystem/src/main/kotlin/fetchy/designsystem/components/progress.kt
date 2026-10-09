package fetchy.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FetchyLinearProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun FetchyCircularProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    size: Dp = 40.dp,
    strokeWidth: Dp = 4.dp,
) {
    val resolved = modifier.size(size)
    if (progress == null) {
        CircularProgressIndicator(
            modifier = resolved,
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = strokeWidth,
        )
    } else {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = resolved,
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = strokeWidth,
        )
    }
}
