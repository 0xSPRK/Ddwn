package fetchy.feature.queue

import fetchy.data.DownloadRepository
import fetchy.model.DownloadTask
import fetchy.ytdlp.DownloadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class QueueViewModel(
    private val repository: DownloadRepository,
    private val scheduler: DownloadScheduler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AutoCloseable {

    val tasks: StateFlow<List<DownloadTask>> =
        repository.observeActive().stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun cancel(id: String) {
        scheduler.cancel(id)
    }

    override fun close() {
        scope.cancel()
    }
}
