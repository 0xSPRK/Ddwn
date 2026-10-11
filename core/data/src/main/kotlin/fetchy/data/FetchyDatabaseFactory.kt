package fetchy.data

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
}
