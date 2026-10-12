package fetchy.feature.add.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fetchy.designsystem.components.FetchyFilterChip
import fetchy.designsystem.components.FetchySectionHeader
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.add.DownloadFormat
import fetchy.feature.add.generated.resources.Res
import fetchy.feature.add.generated.resources.add_format_audio_m4a
import fetchy.feature.add.generated.resources.add_format_audio_mp3
import fetchy.feature.add.generated.resources.add_format_best
import fetchy.feature.add.generated.resources.add_format_best_1080
import fetchy.feature.add.generated.resources.add_format_best_480
import fetchy.feature.add.generated.resources.add_format_best_720
import fetchy.feature.add.generated.resources.add_format_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun FormatPicker(
    selected: DownloadFormat,
    onFormatSelected: (DownloadFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FetchySpacing.xs),
    ) {
        FetchySectionHeader(title = stringResource(Res.string.add_format_label))
        DownloadFormat.entries.forEach { format ->
            FetchyFilterChip(
                label = formatLabel(format),
                selected = format == selected,
                onClick = { onFormatSelected(format) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun formatLabel(format: DownloadFormat): String = stringResource(
    when (format) {
        DownloadFormat.BEST -> Res.string.add_format_best
        DownloadFormat.BEST_1080P -> Res.string.add_format_best_1080
        DownloadFormat.BEST_720P -> Res.string.add_format_best_720
        DownloadFormat.BEST_480P -> Res.string.add_format_best_480
        DownloadFormat.AUDIO_MP3 -> Res.string.add_format_audio_mp3
        DownloadFormat.AUDIO_M4A -> Res.string.add_format_audio_m4a
    },
)
