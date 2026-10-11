package fetchy.ytdlp.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class AppPaths(
    val appDir: Path,
    val componentsDir: Path,
    val cacheDir: Path,
)

fun interface AppPathsProvider {
    fun resolve(): AppPaths
}

class DefaultAppPathsProvider(
    private val platform: Platform = Platform.current(),
    private val env: Map<String, String> = System.getenv(),
    private val userDir: Path = Paths.get(System.getProperty("user.dir").orEmpty().ifEmpty { "." }),
    private val userHome: Path = Paths.get(System.getProperty("user.home").orEmpty().ifEmpty { "." }),
) : AppPathsProvider {

    override fun resolve(): AppPaths {
        val portableFlag: Path = userDir.resolve(PORTABLE_FLAG)
        if (Files.exists(portableFlag)) {
            return AppPaths(
                appDir = userDir,
                componentsDir = userDir.resolve(COMPONENTS_DIR),
                cacheDir = userDir.resolve(CACHE_DIR),
            )
        }
        return when (platform) {
            Platform.WINDOWS -> {
                val localAppData: Path =
                    env[LOCALAPPDATA]?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
                        ?: userHome.resolve("AppData").resolve("Local")
                val appDir: Path = localAppData.resolve(APP_NAME)
                AppPaths(
                    appDir = appDir,
                    componentsDir = appDir.resolve(COMPONENTS_DIR),
                    cacheDir = appDir.resolve(CACHE_DIR),
                )
            }

            else -> {
                val dataHome: Path =
                    env[XDG_DATA_HOME]?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
                        ?: userHome.resolve(".local").resolve("share")
                val appDir: Path = dataHome.resolve(APP_NAME.lowercase())
                val cacheHome: Path =
                    env[XDG_CACHE_HOME]?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
                        ?: userHome.resolve(".cache")
                AppPaths(
                    appDir = appDir,
                    componentsDir = appDir.resolve(COMPONENTS_DIR),
                    cacheDir = cacheHome.resolve(APP_NAME.lowercase()),
                )
            }
        }
    }

    companion object {
        const val APP_NAME: String = "Fetchy"
        const val PORTABLE_FLAG: String = "portable.flag"
        const val COMPONENTS_DIR: String = "components"
        const val CACHE_DIR: String = "cache"
        const val LOCALAPPDATA: String = "LOCALAPPDATA"
        const val XDG_DATA_HOME: String = "XDG_DATA_HOME"
        const val XDG_CACHE_HOME: String = "XDG_CACHE_HOME"
    }
}
