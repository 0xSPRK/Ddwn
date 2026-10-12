package fetchy.feature.add.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fetchy.designsystem.components.FetchyButton
import fetchy.designsystem.components.FetchyOutlinedCard
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.add.DownloadFormat
import fetchy.feature.add.UrlPreview
import fetchy.feature.add.generated.resources.Res
import fetchy.feature.add.generated.resources.add_download
import fetchy.feature.add.generated.resources.add_duration
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

@Composable
fun UrlPreviewCard(
    preview: UrlPreview,
    selectedFormat: DownloadFormat,
    onFormatSelected: (DownloadFormat) -> Unit,
    downloading: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FetchyOutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FetchySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FetchySpacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(FetchySpacing.md)) {
                val thumbnail: String? = preview.thumbnailUrl
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(THUMBNAIL_SIZE)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(FetchySpacing.xs)) {
                    preview.title?.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    formatDuration(preview.durationSeconds)?.let { duration ->
                        Text(
                            text = stringResource(Res.string.add_duration, duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            FormatPicker(
                selected = selectedFormat,
                onFormatSelected = onFormatSelected,
            )

            FetchyButton(
                text = stringResource(Res.string.add_download),
                onClick = onDownload,
                loading = downloading,
                enabled = !downloading,
            )
        }
    }
}

private val THUMBNAIL_SIZE: DpSize = DpSize(160.dp, 90.dp)

private fun formatDuration(seconds: Double?): String? {
    if (seconds == null) return null
    val total: Long = seconds.toLong()
    if (total < 0) return null
    val hours: Long = total / 3600
    val minutes: Long = (total % 3600) / 60
    val secs: Long = total % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}
