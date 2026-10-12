package fetchy.feature.queue

import fetchy.data.DownloadRepository
import fetchy.model.DownloadTask
import fetchy.model.ProgressInfo
import fetchy.ytdlp.DownloadScheduler
import fetchy.ytdlp.SchedulerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QueueViewModel(
    private val repository: DownloadRepository,
    private val scheduler: DownloadScheduler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AutoCloseable {

    private val live: MutableStateFlow<Map<String, ProgressInfo>> = MutableStateFlow(emptyMap())

    val tasks: StateFlow<List<QueueTaskUiState>> =
        combine(repository.observeActive(), live) { rows, liveMap ->
            rows.map { row -> row.toUiState(liveMap[row.id]) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            scheduler.events.collect { event ->
                when (event) {
                    is SchedulerEvent.Progressed ->
                        live.update { it + (event.taskId to event.info) }

                    is SchedulerEvent.Started ->
                        live.update { it - event.taskId }

                    is SchedulerEvent.Finished ->
                        live.update { it - event.taskId }
                }
            }
        }
    }

    fun cancel(id: String) {
        scheduler.cancel(id)
    }

    override fun close() {
        scope.cancel()
    }
}

private fun DownloadTask.toUiState(info: ProgressInfo?): QueueTaskUiState = QueueTaskUiState(
    id = id,
    title = title ?: url,
    url = url,
    thumbnailUrl = thumbnailUrl,
    status = status,
    progress = progress,
    speedBytesPerSecond = info?.speed,
    etaSeconds = info?.etaSeconds,
    playlistIndex = info?.playlistIndex,
)
