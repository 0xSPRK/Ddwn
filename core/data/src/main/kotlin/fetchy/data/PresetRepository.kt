package fetchy.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import fetchy.data.db.FetchyDatabase
import fetchy.data.db.Preset as PresetRow
import fetchy.model.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class PresetRepository(
    database: FetchyDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.IO,
) {

    private val queries = database.presetQueries

    fun observeAll(): Flow<List<Preset>> =
        queries.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map(PresetRow::toModel) }

    suspend fun get(id: String): Preset? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toModel()
    }

    suspend fun create(preset: Preset) {
        withContext(dispatcher) {
            queries.insert(preset.id, preset.name, preset.argsJson, preset.createdAt)
        }
    }

    suspend fun update(preset: Preset) {
        withContext(dispatcher) {
            queries.update(preset.name, preset.argsJson, preset.id)
        }
    }

    suspend fun delete(id: String) {
        withContext(dispatcher) { queries.deleteById(id) }
    }
}

internal fun PresetRow.toModel(): Preset = Preset(
    id = id,
    name = name,
    argsJson = args_json,
    createdAt = created_at,
)
