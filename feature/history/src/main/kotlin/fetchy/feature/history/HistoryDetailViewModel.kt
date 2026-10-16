package fetchy.feature.history

import fetchy.core.platform.FolderOpener
import fetchy.data.HistoryRepository
import fetchy.model.ErrorClass
import fetchy.model.HistoryEntry
import fetchy.ytdlp.ShellFamily
import fetchy.ytdlp.maskedReusableCommand
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryDetailViewModel(
    private val repository: HistoryRepository,
    private val opener: FolderOpener,
    private val logReader: LogFileReader,
    private val id: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
    private val shell: ShellFamily = detectShell(),
    private val logLimitBytes: Long = DEFAULT_LOG_LIMIT_BYTES,
) : AutoCloseable {

    private val mutableState: MutableStateFlow<HistoryDetailUiState> =
        MutableStateFlow(HistoryDetailUiState())

    val state: StateFlow<HistoryDetailUiState> = mutableState.asStateFlow()

    private var entry: HistoryEntry? = null
    private var logLimit: Long = logLimitBytes
    private var logJob: Job? = null

    init {
        scope.launch {
            val loaded: HistoryEntry? = try {
                repository.get(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                null
            }
            entry = loaded
            val isDrm: Boolean = loaded?.errorClass == ErrorClass.DRM_PROTECTED
            mutableState.update {
                it.copy(
                    entry = loaded,
                    loading = false,
                    command = loaded?.let { entry -> maskedReusableCommand(entry.formatArgsJson, entry.url, shell) },
                    logExpanded = isDrm,
                )
            }
            if (isDrm) loadLog(logLimitBytes)
        }
    }

    fun showLog() {
        loadLog(logLimitBytes)
    }

    fun showLogMore() {
        loadLog(logLimit + LOG_CHUNK_BYTES)
    }

    fun openFolder() {
        val filePath: String = entry?.filePath ?: return
        val directory: Path = runCatching { Path.of(filePath).parent }.getOrNull() ?: return
        scope.launch {
            withContext(ioDispatcher) { opener.open(directory) }
        }
    }

    private fun loadLog(limit: Long) {
        val path: String = entry?.logPath ?: return
        logLimit = limit
        logJob?.cancel()
        logJob = scope.launch {
            mutableState.update { it.copy(logExpanded = true, logLoading = true, logError = false) }
            val slice: LogSlice = try {
                withContext(ioDispatcher) { logReader.read(path, limit) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.update { it.copy(logLoading = false, logError = true) }
                return@launch
            }
            mutableState.update {
                it.copy(
                    logText = slice.text,
                    logLoading = false,
                    logTruncated = slice.truncated,
                )
            }
        }
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        const val DEFAULT_LOG_LIMIT_BYTES: Long = 2L * 1024L * 1024L
        const val LOG_CHUNK_BYTES: Long = 2L * 1024L * 1024L
    }
}

internal fun detectShell(): ShellFamily {
    val osName: String = System.getProperty("os.name").orEmpty().lowercase()
    return if (osName.contains("win")) ShellFamily.POWERSHELL else ShellFamily.BASH
}
