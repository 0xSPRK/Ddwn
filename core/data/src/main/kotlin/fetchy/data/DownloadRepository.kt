package fetchy.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import fetchy.data.db.Download
import fetchy.data.db.FetchyDatabase
import fetchy.model.DownloadTask
import fetchy.model.ErrorClass
import fetchy.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class DownloadRepository(
    database: FetchyDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.IO,
) {

    private val queries = database.downloadQueries

    fun observeAll(): Flow<List<DownloadTask>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map(Download::toModel) }

    fun observeActive(): Flow<List<DownloadTask>> =
        queries.selectActive().asFlow().mapToList(dispatcher).map { rows -> rows.map(Download::toModel) }

    suspend fun get(id: String): DownloadTask? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toModel()
    }

    suspend fun create(task: DownloadTask) {
        withContext(dispatcher) {
            queries.insert(
                task.id,
                task.url,
                task.title,
                task.formatArgsJson,
                task.presetId,
                task.status.name,
                task.progress,
                task.createdAt,
                task.finishedAt,
                task.errorClass?.name,
                task.logPath,
            )
        }
    }

    suspend fun updateStatus(
        id: String,
        status: TaskStatus,
        progress: Double,
        errorClass: ErrorClass? = null,
        finishedAt: Long? = null,
    ) {
        withContext(dispatcher) {
            queries.updateStatus(status.name, progress, finishedAt, errorClass?.name, id)
        }
    }

    suspend fun updateProgress(id: String, progress: Double) {
        withContext(dispatcher) { queries.updateProgress(progress, id) }
    }

    suspend fun delete(id: String) {
        withContext(dispatcher) { queries.deleteById(id) }
    }

    suspend fun activeCount(): Long = withContext(dispatcher) { queries.countActive().executeAsOne() }
}

internal fun Download.toModel(): DownloadTask = DownloadTask(
    id = id,
    url = url,
    title = title,
    formatArgsJson = format_args_json,
    presetId = preset_id,
    status = TaskStatus.valueOf(status),
    progress = progress,
    createdAt = created_at,
    finishedAt = finished_at,
    errorClass = error_class?.let(ErrorClass::valueOf),
    logPath = log_path,
)
