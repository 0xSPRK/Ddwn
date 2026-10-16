package fetchy.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import fetchy.app.generated.resources.Res
import fetchy.app.generated.resources.nav_add
import fetchy.app.generated.resources.nav_gallery
import fetchy.app.generated.resources.nav_history
import fetchy.app.generated.resources.nav_presets
import fetchy.app.generated.resources.nav_queue
import fetchy.app.generated.resources.nav_settings
import fetchy.app.generated.resources.nav_tools
import fetchy.app.generated.resources.recovery_dialog_confirm
import fetchy.app.generated.resources.recovery_dialog_dismiss
import fetchy.app.generated.resources.recovery_dialog_message
import fetchy.app.generated.resources.recovery_dialog_title
import fetchy.app.generated.resources.screen_coming_soon
import fetchy.app.generated.resources.screen_detail
import fetchy.designsystem.components.FetchyAlertDialog
import fetchy.designsystem.motion.FetchySpacing
import fetchy.feature.add.AddScreen
import fetchy.feature.add.AddViewModel
import fetchy.feature.queue.QueueRecovery
import fetchy.feature.queue.QueueScreen
import fetchy.feature.queue.QueueViewModel
import fetchy.feature.tools.gallery.GalleryScreen
import fetchy.model.DownloadTask
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.Koin

@Composable
fun App(koin: Koin) {
    val navController = remember { FetchyNavController() }
    val recovery: QueueRecovery = remember { koin.get() }
    var recoverable by remember { mutableStateOf<List<DownloadTask>?>(null) }
    LaunchedEffect(Unit) {
        val pending: List<DownloadTask> = recovery.recover()
        if (pending.isNotEmpty()) recoverable = pending
    }
    Row(modifier = Modifier.fillMaxSize()) {
        NavRail(navController)
        Box(modifier = Modifier.fillMaxSize()) {
            FetchyNavHost(navController) { destination ->
                when (destination) {
                    Destination.Add -> {
                        val addViewModel: AddViewModel = remember { koin.get() }
                        AddScreen(
                            viewModel = addViewModel,
                            onTaskCreated = { navController.topLevel(Destination.Queue) },
                        )
                    }

                    Destination.Gallery -> GalleryScreen()
                    Destination.Queue -> {
                        val queueViewModel: QueueViewModel = remember { koin.get() }
                        QueueScreen(viewModel = queueViewModel)
                    }

                    else -> PlaceholderScreen(destination)
                }
            }
        }
    }

    val pendingRecovery: List<DownloadTask>? = recoverable
    if (pendingRecovery != null) {
        FetchyAlertDialog(
            title = stringResource(Res.string.recovery_dialog_title),
            text = stringResource(Res.string.recovery_dialog_message, pendingRecovery.size),
            confirmLabel = stringResource(Res.string.recovery_dialog_confirm),
            dismissLabel = stringResource(Res.string.recovery_dialog_dismiss),
            onConfirm = {
                recovery.resume(pendingRecovery)
                recoverable = null
            },
            onDismiss = { recoverable = null },
        )
    }
}

private data class NavItem(
    val destination: Destination,
    val icon: ImageVector,
    val label: StringResource,
)

@Composable
private fun NavRail(controller: FetchyNavController) {
    val items = listOf(
        NavItem(Destination.Add, Icons.Default.Add, Res.string.nav_add),
        NavItem(Destination.Queue, Icons.AutoMirrored.Filled.List, Res.string.nav_queue),
        NavItem(Destination.History, Icons.Default.Refresh, Res.string.nav_history),
        NavItem(Destination.Presets, Icons.Default.PlayArrow, Res.string.nav_presets),
        NavItem(Destination.Settings, Icons.Default.Settings, Res.string.nav_settings),
        NavItem(Destination.Tools, Icons.Default.Info, Res.string.nav_tools),
        NavItem(Destination.Gallery, Icons.Default.Star, Res.string.nav_gallery),
    )
    NavigationRail {
        items.forEach { item ->
            val label = stringResource(item.label)
            NavigationRailItem(
                selected = controller.current == item.destination,
                onClick = { controller.topLevel(item.destination) },
                icon = { Icon(imageVector = item.icon, contentDescription = label) },
                label = { Text(text = label) },
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(destination: Destination) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FetchySpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = destinationLabel(destination),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(Res.string.screen_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun destinationLabel(destination: Destination): String = when (destination) {
    Destination.Add -> stringResource(Res.string.nav_add)
    Destination.Queue -> stringResource(Res.string.nav_queue)
    Destination.History -> stringResource(Res.string.nav_history)
    Destination.Presets -> stringResource(Res.string.nav_presets)
    Destination.Settings -> stringResource(Res.string.nav_settings)
    Destination.Tools -> stringResource(Res.string.nav_tools)
    Destination.Gallery -> stringResource(Res.string.nav_gallery)
    is Destination.Detail -> stringResource(Res.string.screen_detail, destination.id)
}
