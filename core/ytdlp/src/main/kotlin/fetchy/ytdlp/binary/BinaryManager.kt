package fetchy.ytdlp.binary

import fetchy.ytdlp.components.ComponentId
import fetchy.ytdlp.components.ComponentNaming
import fetchy.ytdlp.util.AppPaths
import fetchy.ytdlp.util.FilesExt
import fetchy.ytdlp.util.Platform
import java.nio.file.Files
import java.nio.file.Path

data class BinaryConfig(
    val mode: BinaryMode = BinaryMode.SYSTEM,
    val userPaths: Map<ComponentId, Path> = emptyMap(),
)

class BinaryManager(
    private val appPaths: AppPaths,
    private val platform: Platform = Platform.current(),
    private val launcher: ProcessLauncher = DefaultProcessLauncher(),
    private val versionCache: VersionCache = VersionCache(),
    private val config: BinaryConfig = BinaryConfig(),
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pathEnv: String? = System.getenv("PATH"),
) {

    suspend fun resolve(component: ComponentId, config: BinaryConfig = this.config): BinaryInfo {
        val candidate: Candidate = candidate(component, config)
        val checkedAt: Long = clock()
        val path: Path? = candidate.path
        if (path == null || !Files.exists(path)) {
            return BinaryInfo(
                path = path,
                version = null,
                source = candidate.source,
                status = BinaryStatus.NOT_FOUND,
                mtimeMillis = null,
                sizeBytes = null,
                checkedAtMillis = checkedAt,
            )
        }

        val key: VersionCacheKey =
            VersionCacheKey(path, FilesExt.mtimeMillis(path), FilesExt.sizeBytes(path))
        versionCache.lookup(component, key)?.let { return it }

        val command: List<String> = listOf(path.toString()) + ComponentNaming.versionArgs(component)
        val info: BinaryInfo =
            try {
                val output: ProcessOutput = launcher.run(command, timeoutMillis)
                if (output.exitCode != 0) {
                    broken(candidate, key, checkedAt)
                } else {
                    val combined: String = output.stdout + "\n" + output.stderr
                    val version: String? = VersionParser.parse(component, combined)
                    BinaryInfo(
                        path = path,
                        version = version,
                        source = candidate.source,
                        status = if (version != null) BinaryStatus.OK else BinaryStatus.UNKNOWN_VERSION,
                        mtimeMillis = key.mtimeMillis,
                        sizeBytes = key.sizeBytes,
                        checkedAtMillis = checkedAt,
                    )
                }
            } catch (_: ProcessStartException) {
                failed(candidate, key, checkedAt, BinaryStatus.NOT_EXECUTABLE)
            } catch (_: ProcessTimeoutException) {
                broken(candidate, key, checkedAt)
            }

        versionCache.store(component, key, info)
        return info
    }

    fun invalidate(component: ComponentId) {
        versionCache.invalidate(component)
    }

    fun invalidateAll() {
        versionCache.invalidateAll()
    }

    private fun broken(candidate: Candidate, key: VersionCacheKey, checkedAt: Long): BinaryInfo =
        failed(candidate, key, checkedAt, BinaryStatus.BROKEN)

    private fun failed(
        candidate: Candidate,
        key: VersionCacheKey,
        checkedAt: Long,
        status: BinaryStatus,
    ): BinaryInfo =
        BinaryInfo(
            path = candidate.path,
            version = null,
            source = candidate.source,
            status = status,
            mtimeMillis = key.mtimeMillis,
            sizeBytes = key.sizeBytes,
            checkedAtMillis = checkedAt,
        )

    private data class Candidate(val path: Path?, val source: BinarySource)

    private fun candidate(component: ComponentId, config: BinaryConfig): Candidate =
        when (config.mode) {
            BinaryMode.CUSTOM -> {
                val userPath: Path? = config.userPaths[component]
                if (userPath != null) {
                    Candidate(userPath, BinarySource.USER_DEFINED)
                } else {
                    Candidate(null, BinarySource.NONE)
                }
            }

            BinaryMode.BUNDLED -> {
                val path: Path =
                    appPaths.componentsDir.resolve(ComponentNaming.executableName(component, platform))
                if (Files.exists(path)) {
                    Candidate(path, BinarySource.COMPONENTS_DIR)
                } else {
                    Candidate(null, BinarySource.NONE)
                }
            }

            BinaryMode.SYSTEM -> {
                val found: Path? = findOnPath(ComponentNaming.executableName(component, platform))
                if (found != null) {
                    Candidate(found, BinarySource.PATH)
                } else {
                    Candidate(null, BinarySource.NONE)
                }
            }
        }

    private fun findOnPath(name: String): Path? {
        val raw: String = pathEnv ?: return null
        if (raw.isBlank()) return null
        for (entry in raw.split(java.io.File.pathSeparatorChar)) {
            if (entry.isBlank()) continue
            val candidate: Path = Path.of(entry.trim().trim('"')).resolve(name)
            if (Files.isRegularFile(candidate)) return candidate
            if (platform == Platform.WINDOWS && !name.endsWith(".exe")) {
                val withExe: Path = Path.of(entry.trim().trim('"')).resolve("$name.exe")
                if (Files.isRegularFile(withExe)) return withExe
            }
        }
        return null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 8_000
    }
}
