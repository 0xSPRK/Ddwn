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

interface HistoryRepository {

    fun observeAll(): Flow<List<HistoryEntry>>

    fun search(
        query: String,
        status: TaskStatus? = null,
        fromMillis: Long? = null,
        toMillis: Long? = null,
        limit: Long,
        offset: Long = 0L,
    ): Flow<List<HistoryEntry>>

    suspend fun get(id: String): HistoryEntry?

    suspend fun insert(entry: HistoryEntry)

    suspend fun delete(id: String)

    suspend fun deleteAll()

    suspend fun count(): Long
}

class SqlDelightHistoryRepository(
    database: FetchyDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.IO,
) : HistoryRepository {

    private val queries = database.historyQueries

    override fun observeAll(): Flow<List<HistoryEntry>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map(History::toModel) }

    override fun search(
        query: String,
        status: TaskStatus?,
        fromMillis: Long?,
        toMillis: Long?,
        limit: Long,
        offset: Long,
    ): Flow<List<HistoryEntry>> =
        queries.searchFiltered(query.escapeLike(), status?.name, fromMillis, toMillis, limit, offset)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map(History::toModel) }

    override suspend fun get(id: String): HistoryEntry? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun insert(entry: HistoryEntry) {
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

    override suspend fun delete(id: String) {
        withContext(dispatcher) { queries.deleteById(id) }
    }

    override suspend fun deleteAll() {
        withContext(dispatcher) { queries.deleteAll() }
    }

    override suspend fun count(): Long = withContext(dispatcher) { queries.countAll().executeAsOne() }
}

private fun String.escapeLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

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
