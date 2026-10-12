package fetchy.core.platform

import java.nio.file.Files
import java.nio.file.Path

class AppPaths(val root: Path) {

    val configDirectory: Path = root.resolve("config")
    val cacheDirectory: Path = root.resolve("cache")
    val logsDirectory: Path = root.resolve("logs")
    val dataDirectory: Path = root.resolve("data")
    val databaseFile: Path = dataDirectory.resolve("fetchy.db")
    val singleInstancePortFile: Path = root.resolve("fetchy.port")

    fun ensureExists(): AppPaths {
        Files.createDirectories(configDirectory)
        Files.createDirectories(cacheDirectory)
        Files.createDirectories(logsDirectory)
        Files.createDirectories(dataDirectory)
        return this
    }
}