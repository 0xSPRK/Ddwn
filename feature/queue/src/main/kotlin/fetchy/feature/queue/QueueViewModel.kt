package fetchy.feature.queue

import fetchy.data.DownloadRepository
import fetchy.model.DownloadTask
import fetchy.model.ProgressInfo
import fetchy.model.TaskStatus
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

    private val selected: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    val tasks: StateFlow<List<QueueTaskUiState>> =
        combine(repository.observeIncomplete(), live) { rows, liveMap ->
            rows.map { row -> row.toUiState(liveMap[row.id]) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val selectedIds: StateFlow<Set<String>> = selected

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
        scope.launch {
            tasks.collect { list ->
                val ids: Set<String> = list.mapTo(HashSet(), QueueTaskUiState::id)
                selected.update { current -> current intersect ids }
            }
        }
    }

    fun toggleSelection(id: String) {
        selected.update { current -> if (id in current) current - id else current + id }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun cancel(id: String) {
        scheduler.cancel(id)
        scope.launch { markCancelledIfQueued(id) }
    }

    fun retry(id: String) {
        scope.launch {
            val task: DownloadTask = repository.get(id) ?: return@launch
            if (task.status != TaskStatus.FAILED && task.status != TaskStatus.CANCELLED) return@launch
            repository.updateStatus(id, TaskStatus.QUEUED, 0.0, null, null)
            if (!scheduler.submit(task)) restore(task)
        }
    }

    fun restart(id: String) {
        scope.launch {
            val task: DownloadTask = repository.get(id) ?: return@launch
            repository.updateStatus(id, TaskStatus.QUEUED, 0.0, null, null)
            if (!scheduler.restart(task.copy(progress = 0.0))) restore(task)
        }
    }

    fun cancelSelected() {
        val ids: Set<String> = selected.value
        ids.forEach { id -> scheduler.cancel(id) }
        clearSelection()
        scope.launch { ids.forEach { id -> markCancelledIfQueued(id) } }
    }

    private suspend fun markCancelledIfQueued(id: String) {
        val task: DownloadTask = repository.get(id) ?: return
        if (task.status == TaskStatus.QUEUED) {
            repository.updateStatus(id, TaskStatus.CANCELLED, task.progress, null, null)
        }
    }

    private suspend fun restore(task: DownloadTask) {
        repository.updateStatus(task.id, task.status, task.progress, task.errorClass, task.finishedAt)
    }

    override fun close() {
        scope.cancel()
    }
}

internal fun DownloadTask.toUiState(info: ProgressInfo?): QueueTaskUiState = QueueTaskUiState(
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
