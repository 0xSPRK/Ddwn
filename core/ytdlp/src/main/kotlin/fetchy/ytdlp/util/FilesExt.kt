package fetchy.ytdlp.util

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

object FilesExt {

    fun ensureDirectory(directory: Path): Path {
        Files.createDirectories(directory)
        return directory
    }

    fun ensureParent(file: Path): Path {
        file.parent?.let { Files.createDirectories(it) }
        return file
    }

    fun exists(path: Path?): Boolean = path != null && Files.exists(path)

    fun mtimeMillis(path: Path): Long? =
        try {
            Files.getLastModifiedTime(path).toMillis()
        } catch (_: IOException) {
            null
        }

    fun sizeBytes(path: Path): Long? =
        try {
            Files.size(path)
        } catch (_: IOException) {
            null
        }

    fun copy(source: Path, target: Path, overwrite: Boolean = true) {
        ensureParent(target)
        if (overwrite) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        } else {
            Files.copy(source, target)
        }
    }

    fun move(source: Path, target: Path, overwrite: Boolean = true) {
        ensureParent(target)
        val options: Array<StandardCopyOption> =
            if (overwrite) {
                arrayOf(StandardCopyOption.REPLACE_EXISTING)
            } else {
                emptyArray()
            }
        Files.move(source, target, *options)
    }

    fun moveAtomic(source: Path, target: Path) {
        ensureParent(target)
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun deleteIfExists(path: Path): Boolean =
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            false
        }

    fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    fun setExecutable(path: Path) {
        val file = path.toFile()
        if (file.exists() && !file.canExecute()) {
            file.setExecutable(true, false)
        }
    }
}
