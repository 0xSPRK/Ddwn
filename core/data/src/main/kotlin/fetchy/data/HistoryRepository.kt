package fetchy.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import fetchy.data.db.FetchyDatabase
import fetchy.data.db.History
import fetchy.model.ErrorClass
import fetchy.model.HistoryEntry
import fetchy.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class HistoryRepository(
    database: FetchyDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.IO,
) {

    private val queries = database.historyQueries

    fun observeAll(): Flow<List<HistoryEntry>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map(History::toModel) }

    fun search(query: String, limit: Long, offset: Long): Flow<List<HistoryEntry>> =
        queries.search(query, query, limit, offset)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(History::toModel) }

    suspend fun get(id: String): HistoryEntry? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toModel()
    }

    suspend fun insert(entry: HistoryEntry) {
        withContext(dispatcher) {
            queries.insert(
                entry.id,
                entry.url,
                entry.title,
                entry.formatArgsJson,
                entry.presetId,
                entry.status.name,
                entry.progress,
                entry.createdAt,
                entry.finishedAt,
                entry.errorClass?.name,
                entry.logPath,
                entry.filePath,
                entry.durationMs,
            )
        }
    }

    suspend fun delete(id: String) {
        withContext(dispatcher) { queries.deleteById(id) }
    }

    suspend fun deleteAll() {
        withContext(dispatcher) { queries.deleteAll() }
    }

    suspend fun count(): Long = withContext(dispatcher) { queries.countAll().executeAsOne() }
}

internal fun History.toModel(): HistoryEntry = HistoryEntry(
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
    filePath = file_path,
    durationMs = duration_ms,
)
