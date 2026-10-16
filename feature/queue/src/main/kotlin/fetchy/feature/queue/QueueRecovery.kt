package fetchy.feature.queue

import fetchy.data.DownloadRepository
import fetchy.model.DownloadTask
import fetchy.ytdlp.DownloadScheduler

class QueueRecovery(
    private val repository: DownloadRepository,
    private val scheduler: DownloadScheduler,
) {

    suspend fun recover(): List<DownloadTask> = repository.requeue()

    fun resume(tasks: List<DownloadTask>): Int {
        var submitted = 0
        tasks.forEach { task ->
            if (scheduler.submit(task)) submitted++
        }
        return submitted
    }
}
