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
        if (isNew) {
            schema.create(driver)
            driver.setUserVersion(schema.version)
        } else {
            val current: Long = driver.userVersion()
            if (current < schema.version) {
                schema.migrate(driver, current, schema.version)
                driver.setUserVersion(schema.version)
            }
        }
    }
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
