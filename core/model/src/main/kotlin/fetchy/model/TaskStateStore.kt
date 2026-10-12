package fetchy.model

interface TaskStateStore {
    suspend fun markDownloading(id: String)

    suspend fun updateProgress(id: String, progress: Double)

    suspend fun markCompleted(id: String, progress: Double, finishedAt: Long)

    suspend fun markFailed(id: String, progress: Double, errorClass: ErrorClass?, finishedAt: Long)

    suspend fun markCancelled(id: String, progress: Double, finishedAt: Long)
}
