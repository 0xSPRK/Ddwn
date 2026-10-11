package fetchy.ytdlp.installer

import fetchy.ytdlp.components.ComponentError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveExtractorTest {

    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        root = Files.createTempDirectory("extract")
    }

    private fun zipWith(vararg entries: Pair<String, ByteArray>): Path {
        val file = root.resolve("archive-${System.nanoTime()}.zip")
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun extractsNestedZipEntries() {
        val archive =
            zipWith(
                "dir/" to ByteArray(0),
                "dir/a.txt" to "hello".toByteArray(),
                "b.txt" to "world".toByteArray(),
            )
        val dest = root.resolve("out")

        ArchiveExtractor().extract(archive, dest)

        assertEquals("hello", Files.readString(dest.resolve("dir/a.txt")))
        assertEquals("world", Files.readString(dest.resolve("b.txt")))
    }

    @Test
    fun rejectsZipSlipEntry() {
        val archive = zipWith("../evil.txt" to "boom".toByteArray())
        val dest = root.resolve("out")

        val error =
            org.junit.jupiter.api.Assertions.assertThrows(ComponentError.UnsafeArchiveEntry::class.java) {
                ArchiveExtractor().extract(archive, dest)
            }
        assertEquals("../evil.txt", error.entry)
        assertTrue(!Files.exists(root.resolve("evil.txt")))
    }

    @Test
    fun rejectsAbsoluteEntry() {
        val archive = zipWith("/etc/passwd" to "boom".toByteArray())
        org.junit.jupiter.api.Assertions.assertThrows(ComponentError.UnsafeArchiveEntry::class.java) {
            ArchiveExtractor().extract(archive, root.resolve("out"))
        }
    }

    @Test
    fun rejectsDriveQualifiedEntry() {
        val archive = zipWith("C:/Windows/evil.txt" to "boom".toByteArray())
        org.junit.jupiter.api.Assertions.assertThrows(ComponentError.UnsafeArchiveEntry::class.java) {
            ArchiveExtractor().extract(archive, root.resolve("out"))
        }
    }

    @Test
    fun rejectsCorruptZip() {
        val file = root.resolve("broken.zip")
        Files.write(file, "this is not a zip archive".toByteArray())
        org.junit.jupiter.api.Assertions.assertThrows(ComponentError.CorruptArchive::class.java) {
            ArchiveExtractor().extract(file, root.resolve("out"))
        }
    }

    @Test
    fun rejectsEmptyZip() {
        val file = root.resolve("empty.zip")
        try {
            ZipOutputStream(Files.newOutputStream(file)).use { }
        } catch (e: IOException) {
            throw AssertionError("failed to create empty zip", e)
        }
        org.junit.jupiter.api.Assertions.assertThrows(ComponentError.CorruptArchive::class.java) {
            ArchiveExtractor().extract(file, root.resolve("out"))
        }
    }

    @Test
    fun rejectsUnsupportedArchiveType() {
        val file = root.resolve("archive.rar")
        Files.write(file, "nope".toByteArray())
        org.junit.jupiter.api.Assertions.assertThrows(ComponentError.CorruptArchive::class.java) {
            ArchiveExtractor().extract(file, root.resolve("out"))
        }
    }

    @Test
    fun flagsSymlinkAndHardlinkEntriesInTarListing() {
        val extractor = ArchiveExtractor()
        val symlink = "lrwxrwxrwx user/group 0 2026-01-01 00:00 link -> /etc/passwd"
        val hardlink = "hrw-r--r-- user/group 0 2026-01-01 00:00 hard link to target"
        assertTrue(extractor.hasUnsafeLinkEntries(symlink))
        assertTrue(extractor.hasUnsafeLinkEntries(hardlink))
    }

    @Test
    fun allowsRegularTarEntries() {
        val extractor = ArchiveExtractor()
        val regular = "-rw-r--r-- user/group 1234 2026-01-01 00:00 dir/ffmpeg"
        val directory = "drwxr-xr-x user/group 0 2026-01-01 00:00 dir/"
        assertTrue(!extractor.hasUnsafeLinkEntries(regular))
        assertTrue(!extractor.hasUnsafeLinkEntries(directory))
    }
}