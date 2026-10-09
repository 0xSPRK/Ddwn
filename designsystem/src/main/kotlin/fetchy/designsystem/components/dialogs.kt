package fetchy.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun FetchyAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissLabel: String? = null,
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = title) },
        text = text?.let { { Text(text = it) } },
        icon = icon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        confirmButton = {
            FetchyTextButton(text = confirmLabel, onClick = onConfirm)
        },
        dismissButton = dismissLabel?.let {
            { FetchyTextButton(text = it, onClick = onDismiss) }
        },
    )
}
