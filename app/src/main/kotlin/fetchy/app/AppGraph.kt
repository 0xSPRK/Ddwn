package fetchy.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import fetchy.core.platform.AppPaths
import fetchy.data.DownloadRepository
import fetchy.data.FetchyDatabaseFactory
import fetchy.data.HistoryRepository
import fetchy.data.PresetRepository
import fetchy.data.SettingsRepository
import fetchy.data.SqlDelightHistoryRepository
import fetchy.data.db.FetchyDatabase
import fetchy.feature.add.addModule
import fetchy.feature.history.historyModule
import fetchy.feature.presets.presetsModule
import fetchy.feature.queue.queueModule
import fetchy.feature.settings.settingsModule
import fetchy.feature.tools.toolsModule
import java.nio.file.Files
import java.nio.file.Path
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose

class AppGraph private constructor(
    private val app: KoinApplication,
) : AutoCloseable {

    val koin: Koin
        get() = app.koin

    override fun close() {
        runCatching { app.close() }
    }

    companion object {

        fun start(paths: AppPaths): AppGraph {
            val application = koinApplication {
                modules(appModules(paths))
            }
            return AppGraph(application)
        }

        fun appModules(paths: AppPaths): List<Module> = listOf(
            coreModule(paths),
            addModule,
            queueModule,
            historyModule,
            presetsModule,
            settingsModule,
            toolsModule,
        )

        fun coreModule(paths: AppPaths): Module = module {
            single { paths }
            single<SqlDriver>(createdAtStart = true) { createDriver(paths.databaseFile) } onClose {
                it?.close()
            }
            single { FetchyDatabaseFactory.create(get()) }
            single { DownloadRepository(get()) }
            single<HistoryRepository> { SqlDelightHistoryRepository(get()) }
            single { PresetRepository(get()) }
            single { SettingsRepository(get()) }
        }

        fun createDriver(databaseFile: Path): SqlDriver {
            Files.createDirectories(databaseFile.parent)
            val isNew = !Files.exists(databaseFile)
            val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.toAbsolutePath()}")
            FetchyDatabaseFactory.open(driver, isNew)
            return driver
        }
    }
}
