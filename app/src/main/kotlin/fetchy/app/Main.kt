package fetchy.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fetchy.app.generated.resources.Res
import fetchy.app.generated.resources.app_name
import fetchy.core.platform.AppDirs
import fetchy.core.platform.SingleInstance
import fetchy.designsystem.theme.FetchyTheme
import java.awt.Window as AwtWindow
import org.jetbrains.compose.resources.stringResource

private var awtWindow: AwtWindow? = null

fun main() {
    val paths = AppDirs.appPaths().ensureExists()
    val singleInstance = SingleInstance(paths.singleInstancePortFile, onFocusRequested = ::raiseWindow)
    if (!singleInstance.acquire()) return
    val graph = AppGraph.start(paths)
    try {
        application {
            Window(
                onCloseRequest = {
                    shutdown(graph, singleInstance)
                    exitApplication()
                },
                title = stringResource(Res.string.app_name),
                state = rememberWindowState(width = 1100.dp, height = 720.dp),
            ) {
                DisposableEffect(Unit) {
                    awtWindow = window
                    (window as? java.awt.Frame)?.minimumSize = java.awt.Dimension(1100, 720)
                    onDispose { awtWindow = null }
                }
                FetchyTheme {
                    App()
                }
            }
        }
    } finally {
        shutdown(graph, singleInstance)
    }
}

private fun shutdown(graph: AppGraph, singleInstance: SingleInstance) {
    runCatching { graph.close() }
    runCatching { singleInstance.close() }
}

private fun raiseWindow() {
    val target = awtWindow ?: return
    runCatching {
        if (!target.isVisible) {
            target.isVisible = true
        }
        target.toFront()
        target.requestFocus()
    }
}