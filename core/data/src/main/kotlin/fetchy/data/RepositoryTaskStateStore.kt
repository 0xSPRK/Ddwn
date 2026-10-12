package fetchy.data

import fetchy.model.ErrorClass
import fetchy.model.TaskStateStore
import fetchy.model.TaskStatus

class RepositoryTaskStateStore(private val repository: DownloadRepository) : TaskStateStore {

    override suspend fun markDownloading(id: String) {
        repository.updateStatus(id, TaskStatus.DOWNLOADING, 0.0)
    }

    override suspend fun updateProgress(id: String, progress: Double) {
        repository.updateProgress(id, progress)
    }

    override suspend fun markCompleted(id: String, progress: Double, finishedAt: Long) {
        repository.updateStatus(id, TaskStatus.COMPLETED, COMPLETED_PROGRESS, finishedAt = finishedAt)
    }

    override suspend fun markFailed(
        id: String,
        progress: Double,
        errorClass: ErrorClass?,
        finishedAt: Long,
    ) {
        repository.updateStatus(
            id = id,
            status = TaskStatus.FAILED,
            progress = progress,
            errorClass = errorClass,
            finishedAt = finishedAt,
        )
    }

    override suspend fun markCancelled(id: String, progress: Double, finishedAt: Long) {
        repository.updateStatus(id, TaskStatus.CANCELLED, progress, finishedAt = finishedAt)
    }

    private companion object {
        const val COMPLETED_PROGRESS: Double = 1.0
    }
}
