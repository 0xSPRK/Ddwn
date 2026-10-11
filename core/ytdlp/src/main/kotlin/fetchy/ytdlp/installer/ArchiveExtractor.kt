package fetchy.ytdlp.installer

import fetchy.ytdlp.components.ComponentError
import fetchy.ytdlp.util.FilesExt
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

class ArchiveExtractor {

    fun extract(archive: Path, destDir: Path) {
        FilesExt.ensureDirectory(destDir)
        val name: String = archive.fileName.toString().lowercase()
        when {
            name.endsWith(".zip") -> extractZip(archive, destDir)
            name.endsWith(".tar.xz") || name.endsWith(".txz") -> extractTarXz(archive, destDir)
            else -> throw ComponentError.CorruptArchive("Unsupported archive type: ${archive.fileName}")
        }
    }

    private fun extractZip(archive: Path, destDir: Path) {
        val root: Path = destDir.toAbsolutePath().normalize()
        var entries: Int = 0
        try {
            Files.newInputStream(archive).use { input ->
                ZipInputStream(input).use { zip: ZipInputStream ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        entries++
                        val target: Path = safeTarget(root, entry.name)
                        if (entry.isDirectory) {
                            FilesExt.ensureDirectory(target)
                        } else {
                            FilesExt.ensureParent(target)
                            Files.newOutputStream(target).use { output -> zip.copyTo(output) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: ComponentError.UnsafeArchiveEntry) {
            throw e
        } catch (e: ZipException) {
            throw ComponentError.CorruptArchive("Corrupt zip archive: ${e.message}")
        } catch (e: IOException) {
            throw ComponentError.CorruptArchive("Corrupt zip archive: ${e.message}")
        }
        if (entries == 0) {
            throw ComponentError.CorruptArchive("Archive contains no entries")
        }
    }

    private fun extractTarXz(archive: Path, destDir: Path) {
        val root: Path = destDir.toAbsolutePath().normalize()
        val listing: List<String> = runTar(listOf("-tf", archive.toString())).lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (listing.isEmpty()) {
            throw ComponentError.CorruptArchive("Archive contains no entries")
        }
        for (entry in listing) {
            safeTarget(root, entry)
        }
        if (hasUnsafeLinkEntries(runTar(listOf("-tvf", archive.toString())))) {
            throw ComponentError.UnsafeArchiveEntry("Archive contains link entries")
        }
        val code: Int = runTarProcess(listOf("-xf", archive.toString(), "-C", destDir.toString()))
        if (code != 0) {
            throw ComponentError.CorruptArchive("Failed to extract tar.xz archive")
        }
    }

    private fun runTar(args: List<String>): String {
        val process: Process =
            try {
                ProcessBuilder(listOf("tar") + args).redirectErrorStream(false).start()
            } catch (e: IOException) {
                throw ComponentError.CorruptArchive("tar.xz extraction requires the 'tar' executable")
            }
        val out: ByteArrayOutputStream = ByteArrayOutputStream()
        val err: ByteArrayOutputStream = ByteArrayOutputStream()
        val outThread: Thread = Thread { process.inputStream.use { it.copyTo(out) } }
        val errThread: Thread = Thread { process.errorStream.use { it.copyTo(err) } }
        outThread.start()
        errThread.start()
        val finished: Boolean = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw ComponentError.CorruptArchive("tar.xz listing timed out")
        }
        outThread.join()
        errThread.join()
        if (process.exitValue() != 0) {
            throw ComponentError.CorruptArchive("tar.xz extraction failed: ${err.toString(Charsets.UTF_8)}")
        }
        return out.toString(Charsets.UTF_8)
    }

    private fun runTarProcess(args: List<String>): Int {
        val process: Process =
            try {
                ProcessBuilder(listOf("tar") + args).redirectErrorStream(false).start()
            } catch (e: IOException) {
                throw ComponentError.CorruptArchive("tar.xz extraction requires the 'tar' executable")
            }
        val outThread: Thread = Thread { process.inputStream.use { it.copyTo(ByteArrayOutputStream()) } }
        val errThread: Thread = Thread { process.errorStream.use { it.copyTo(ByteArrayOutputStream()) } }
        outThread.start()
        errThread.start()
        val finished: Boolean = process.waitFor(120, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw ComponentError.CorruptArchive("tar.xz extraction timed out")
        }
        outThread.join()
        errThread.join()
        return process.exitValue()
    }

    internal fun hasUnsafeLinkEntries(verboseListing: String): Boolean =
        verboseListing.lineSequence().any { line ->
            val trimmed: String = line.trimStart()
            if (trimmed.isEmpty()) {
                false
            } else {
                val type: Char = trimmed[0]
                type == 'l' || type == 'h' || trimmed.contains(" -> ")
            }
        }

    private fun safeTarget(root: Path, entryName: String): Path {
        if (entryName.isBlank()) {
            throw ComponentError.UnsafeArchiveEntry(entryName)
        }
        val normalizedName: String = entryName.replace('\\', '/')
        if (normalizedName.startsWith("/") || normalizedName.contains(":")) {
            throw ComponentError.UnsafeArchiveEntry(entryName)
        }
        val target: Path = root.resolve(normalizedName).normalize()
        if (!target.startsWith(root)) {
            throw ComponentError.UnsafeArchiveEntry(entryName)
        }
        return target
    }
}
