package fetchy.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import fetchy.data.db.FetchyDatabase
import fetchy.data.db.Setting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class SettingsRepository(
    database: FetchyDatabase,
    private val dispatcher: CoroutineContext = Dispatchers.IO,
) {

    private val queries = database.settingQueries

    fun observeAll(): Flow<Map<String, String>> =
        queries.selectAll().asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate(Setting::toEntry) }

    suspend fun get(key: String): String? = withContext(dispatcher) {
        queries.selectByKey(key).executeAsOneOrNull()?.value_
    }

    suspend fun put(key: String, value: String) {
        withContext(dispatcher) { queries.insert(key, value) }
    }

    suspend fun remove(key: String) {
        withContext(dispatcher) { queries.deleteByKey(key) }
    }
}

private fun Setting.toEntry(): Pair<String, String> = setting_key to value_
