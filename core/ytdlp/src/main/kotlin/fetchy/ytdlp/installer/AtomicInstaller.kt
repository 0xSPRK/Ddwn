package fetchy.ytdlp.installer

import fetchy.ytdlp.components.ComponentError
import fetchy.ytdlp.util.FilesExt
import fetchy.ytdlp.util.Platform
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

sealed interface AtomicInstallResult {

    data object Installed : AtomicInstallResult

    data class RolledBack(val reason: String) : AtomicInstallResult

    data class Failed(val error: ComponentError) : AtomicInstallResult
}

class AtomicInstaller(
    private val platform: Platform = Platform.current(),
) {

    suspend fun install(stagedBinary: Path, target: Path, postCheck: suspend () -> Boolean): AtomicInstallResult {
        FilesExt.ensureParent(target)
        val backup: Path = target.resolveSibling(target.fileName.toString() + BACKUP_SUFFIX)

        val hadPrevious: Boolean = Files.exists(target)
        if (hadPrevious) {
            try {
                FilesExt.deleteIfExists(backup)
                FilesExt.moveAtomic(target, backup)
            } catch (e: IOException) {
                return AtomicInstallResult.Failed(ComponentError.PostCheckFailed("Cannot back up ${target.fileName}: ${e.message}"))
            }
        }

        try {
            FilesExt.moveAtomic(stagedBinary, target)
        } catch (e: IOException) {
            if (hadPrevious) {
                runCatching { FilesExt.moveAtomic(backup, target) }
            }
            return AtomicInstallResult.Failed(ComponentError.PostCheckFailed("Cannot install ${target.fileName}: ${e.message}"))
        }

        if (platform != Platform.WINDOWS) {
            FilesExt.setExecutable(target)
        }

        val ok: Boolean =
            try {
                postCheck()
            } catch (_: Exception) {
                false
            }

        if (!ok) {
            FilesExt.deleteIfExists(target)
            if (hadPrevious && Files.exists(backup)) {
                runCatching { FilesExt.moveAtomic(backup, target) }
            }
            return AtomicInstallResult.RolledBack("Post-check failed for ${target.fileName}")
        }

        return AtomicInstallResult.Installed
    }

    companion object {
        const val BACKUP_SUFFIX: String = ".bak"
    }
}
