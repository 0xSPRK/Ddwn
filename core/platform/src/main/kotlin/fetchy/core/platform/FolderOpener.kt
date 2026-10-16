package fetchy.core.platform

import java.nio.file.Files
import java.nio.file.Path

fun interface FolderOpener {
    fun open(directory: Path): Boolean
}

class DefaultFolderOpener : FolderOpener {

    override fun open(directory: Path): Boolean {
        if (!Files.isDirectory(directory)) return false
        val target: String = directory.toAbsolutePath().toString()
        val command: List<String> = explorerCommand(target) ?: return false
        return runCatching {
            ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.isSuccess
    }

    private fun explorerCommand(target: String): List<String>? {
        val osName: String = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            osName.contains("win") -> listOf("explorer.exe", target)
            osName.contains("mac") || osName.contains("darwin") -> listOf("open", target)
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") ->
                listOf("xdg-open", target)

            else -> null
        }
    }
}
