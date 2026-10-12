package fetchy.feature.add.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fetchy.designsystem.components.FetchyOutlinedCard
import fetchy.designsystem.components.FetchyTextButton
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.add.AddError
import fetchy.feature.add.generated.resources.Res
import fetchy.feature.add.generated.resources.add_error_binary_missing
import fetchy.feature.add.generated.resources.add_error_drm
import fetchy.feature.add.generated.resources.add_error_generic
import fetchy.feature.add.generated.resources.add_error_invalid_url
import fetchy.feature.add.generated.resources.add_error_unknown
import fetchy.feature.add.generated.resources.add_hide_log
import fetchy.feature.add.generated.resources.add_show_log
import fetchy.model.ErrorClass
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddErrorPanel(
    error: AddError,
    logVisible: Boolean,
    onToggleLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FetchyOutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FetchySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FetchySpacing.sm),
        ) {
            Text(
                text = message(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            if (error is AddError.Classified && error.message.isNotBlank()) {
                FetchyTextButton(
                    text = stringResource(if (logVisible) Res.string.add_hide_log else Res.string.add_show_log),
                    onClick = onToggleLog,
                )
                if (logVisible) {
                    Text(
                        text = error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun message(error: AddError): String = when (error) {
    AddError.InvalidUrl -> stringResource(Res.string.add_error_invalid_url)
    is AddError.Classified -> when (error.errorClass) {
        ErrorClass.DRM_PROTECTED -> stringResource(Res.string.add_error_drm)
        ErrorClass.BINARY_MISSING -> stringResource(Res.string.add_error_binary_missing)
        ErrorClass.UNKNOWN -> stringResource(Res.string.add_error_unknown)
        else -> stringResource(Res.string.add_error_generic, error.message)
    }
}
