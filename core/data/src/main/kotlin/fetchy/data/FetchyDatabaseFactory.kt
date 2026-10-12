package fetchy.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import fetchy.data.db.FetchyDatabase

object FetchyDatabaseFactory {

    fun create(driver: SqlDriver): FetchyDatabase = FetchyDatabase(driver)

    fun inMemory(): FetchyDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FetchyDatabase.Schema.create(driver)
        return FetchyDatabase(driver)
    }

    fun open(driver: SqlDriver, isNew: Boolean) {
        val schema = FetchyDatabase.Schema
        val current: Long = if (isNew) 0L else driver.userVersion()
        if (isNew || (current == 0L && !driver.hasTable(TABLE_DOWNLOAD))) {
            schema.create(driver)
            driver.setUserVersion(schema.version)
        } else if (current < schema.version) {
            schema.migrate(driver, current, schema.version)
            driver.setUserVersion(schema.version)
        }
    }

    private const val TABLE_DOWNLOAD: String = "download"
}

@Suppress("UNCHECKED_CAST")
fun SqlDriver.hasTable(name: String): Boolean {
    val result: QueryResult<Long> =
        executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 1,
            binders = { bindString(0, name) },
        )
    return (result as QueryResult.Value<Long>).value > 0L
}

@Suppress("UNCHECKED_CAST")
fun SqlDriver.userVersion(): Long {
    val result: QueryResult<Long> =
        executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0,
        )
    return (result as QueryResult.Value<Long>).value
}

fun SqlDriver.setUserVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0)
}
