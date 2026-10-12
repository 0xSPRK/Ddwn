package fetchy.core.platform

import java.nio.file.Path
import java.nio.file.Paths

object AppDirs {

    fun resolve(): Path {
        val os = System.getProperty("os.name").lowercase()
        val root = when {
            os.contains("win") -> windowsBase()?.resolve("Fetchy") ?: homeFallback("AppData", "Fetchy")
            os.contains("mac") -> Paths.get(System.getProperty("user.home"), "Library", "Application Support", "Fetchy")
            else -> envPath("XDG_DATA_HOME")?.resolve("fetchy")
                ?: Paths.get(System.getProperty("user.home"), ".local", "share", "fetchy")
        }
        return root.toAbsolutePath().normalize()
    }

    fun appPaths(): AppPaths = AppPaths(resolve())

    private fun windowsBase(): Path? =
        envPath("LOCALAPPDATA") ?: envPath("APPDATA")

    private fun envPath(name: String): Path? =
        System.getenv(name)?.takeIf { it.isNotBlank() }?.let(Paths::get)

    private fun homeFallback(first: String, second: String): Path =
        Paths.get(System.getProperty("user.home"), first, second)
}