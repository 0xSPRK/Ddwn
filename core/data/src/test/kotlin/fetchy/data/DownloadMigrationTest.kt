package fetchy.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import fetchy.data.db.FetchyDatabase
import fetchy.model.DownloadTask
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadMigrationTest {

    @Test
    fun `migrate from version 1 adds thumbnail_url`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, VERSION_1_SCHEMA, 0)
        driver.setUserVersion(1)

        FetchyDatabaseFactory.open(driver, isNew = false)

        assertEquals(FetchyDatabase.Schema.version, driver.userVersion())

        val repository = DownloadRepository(FetchyDatabase(driver))
        val task = DownloadTask(
            id = "a",
            url = "https://example.test/a",
            thumbnailUrl = "https://img/t.jpg",
            createdAt = 1L,
        )

        repository.create(task)

        assertEquals("https://img/t.jpg", repository.get("a")?.thumbnailUrl)
    }

    private companion object {
        private const val VERSION_1_SCHEMA: String = """
            CREATE TABLE download (
                id TEXT NOT NULL PRIMARY KEY,
                url TEXT NOT NULL,
                title TEXT,
                format_args_json TEXT NOT NULL DEFAULT '[]',
                preset_id TEXT,
                status TEXT NOT NULL,
                progress REAL NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                finished_at INTEGER,
                error_class TEXT,
                log_path TEXT
            );
        """
    }
}
