package fetchy.feature.tools.gallery

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fetchy.designsystem.components.FetchyAlertDialog
import fetchy.designsystem.components.FetchyAssistChip
import fetchy.designsystem.components.FetchyButton
import fetchy.designsystem.components.FetchyCard
import fetchy.designsystem.components.FetchyCircularProgress
import fetchy.designsystem.components.FetchyElevatedCard
import fetchy.designsystem.components.FetchyEmptyState
import fetchy.designsystem.components.FetchyFilterChip
import fetchy.designsystem.components.FetchyLinearProgress
import fetchy.designsystem.components.FetchyOutlinedButton
import fetchy.designsystem.components.FetchyOutlinedCard
import fetchy.designsystem.components.FetchySectionHeader
import fetchy.designsystem.components.FetchyTextButton
import fetchy.designsystem.components.FetchyTextField
import fetchy.designsystem.motion.FetchySpacing
import fetchy.designsystem.shapes.rememberFetchyMorphShape

/**
 * Showcase of every base design-system component plus the `graphics-shapes` morphing spike
 * (T1.3). Pure `@Composable`: it owns no navigation and takes no callbacks — the app shell
 * connects it to `Destination.Gallery` in T8.1.
 */
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var filterSelected by remember { mutableStateOf(true) }
    var dialogVisible by remember { mutableStateOf(false) }

    val morphProgress by rememberInfiniteTransition(label = "gallery-morph").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gallery-morph-progress",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FetchySpacing.lg),
        verticalArrangement = Arrangement.spacedBy(FetchySpacing.xl),
    ) {
        FetchySectionHeader(
            title = "Fetchy design system",
            subtitle = "Component gallery",
        )

        GallerySection("Shapes") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FetchySpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(rememberFetchyMorphShape(morphProgress))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = "graphics-shapes Morph: star -> circle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        GallerySection("Buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(FetchySpacing.sm)) {
                FetchyButton(text = "Primary", onClick = {})
                FetchyButton(text = "Loading", onClick = {}, loading = true)
                FetchyOutlinedButton(text = "Outlined", onClick = {})
                FetchyTextButton(text = "Text", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FetchySpacing.sm)) {
                FetchyButton(text = "Disabled", onClick = {}, enabled = false)
            }
        }

        GallerySection("Cards") {
            FetchyCard { GalleryCardBody(title = "Card") }
            FetchyElevatedCard { GalleryCardBody(title = "Elevated card") }
            FetchyOutlinedCard { GalleryCardBody(title = "Outlined card") }
        }

        GallerySection("Text fields") {
            FetchyTextField(
                value = text,
                onValueChange = { text = it },
                label = "Search",
                placeholder = "Type a video URL",
            )
            FetchyTextField(
                value = "not-a-url",
                onValueChange = {},
                label = "URL",
                isError = true,
                supportingText = "Enter a valid http(s) URL",
            )
        }

        GallerySection("Chips") {
            Row(horizontalArrangement = Arrangement.spacedBy(FetchySpacing.sm)) {
                FetchyFilterChip(
                    label = "Video",
                    selected = filterSelected,
                    onClick = { filterSelected = !filterSelected },
                )
                FetchyFilterChip(label = "Audio", selected = false, onClick = {})
                FetchyAssistChip(label = "Paste URL", onClick = {})
            }
        }

        GallerySection("Progress") {
            FetchyLinearProgress(progress = 0.4f)
            FetchyLinearProgress(progress = null)
            Row(
                horizontalArrangement = Arrangement.spacedBy(FetchySpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FetchyCircularProgress(progress = 0.65f)
                FetchyCircularProgress(progress = null)
            }
        }

        GallerySection("Dialog") {
            FetchyTextButton(text = "Show dialog", onClick = { dialogVisible = true })
        }

        GallerySection("Empty state") {
            FetchyEmptyState(
                title = "Nothing here yet",
                description = "Downloads you start will appear here.",
                action = { FetchyButton(text = "Add download", onClick = {}) },
            )
        }
    }

    if (dialogVisible) {
        FetchyAlertDialog(
            title = "Remove download",
            text = "This will delete the item from the queue.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = { dialogVisible = false },
            onDismiss = { dialogVisible = false },
        )
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FetchySpacing.sm)) {
        FetchySectionHeader(title = title)
        content()
    }
}

@Composable
private fun GalleryCardBody(title: String) {
    Column(modifier = Modifier.padding(FetchySpacing.lg)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Supporting content demonstrates the shape, color and spacing tokens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
