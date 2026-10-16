package fetchy.feature.history

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LogFileReaderTest {

    @TempDir
    lateinit var directory: Path

    private val reader = DefaultLogFileReader()

    @Test
    fun `reads a small file in full`() {
        val file = write("log.txt", "héllo wörld")

        val slice = reader.read(file.toString(), 1_000_000L)

        assertEquals("héllo wörld", slice.text)
        assertFalse(slice.truncated)
    }

    @Test
    fun `truncates when the file exceeds the limit`() {
        val file = write("log.txt", "ab€X")

        val slice = reader.read(file.toString(), 5L)

        assertEquals("ab€", slice.text)
        assertTrue(slice.truncated)
    }

    @Test
    fun `does not split a multibyte character at the truncation point`() {
        val file = write("log.txt", "€€")

        val slice = reader.read(file.toString(), 4L)

        assertEquals("€", slice.text)
        assertTrue(slice.truncated)
        assertFalse(slice.text.contains('\uFFFD'))
    }

    @Test
    fun `returns an empty slice for a missing file`() {
        val slice = reader.read(directory.resolve("missing.txt").toString(), 1024L)

        assertEquals("", slice.text)
        assertFalse(slice.truncated)
    }

    @Test
    fun `reads a file exactly at the limit as complete`() {
        val file = write("log.txt", "abcde")

        val slice = reader.read(file.toString(), 5L)

        assertEquals("abcde", slice.text)
        assertFalse(slice.truncated)
    }

    @Test
    fun `decodes invalid utf8 without throwing`() {
        val file = directory.resolve("log.txt")
        Files.write(file, byteArrayOf(0x41, 0xFF.toByte(), 0xFE.toByte(), 0x42))

        val slice = reader.read(file.toString(), 1_000_000L)

        assertFalse(slice.truncated)
        assertTrue(slice.text.startsWith("A"))
        assertTrue(slice.text.endsWith("B"))
    }

    @Test
    fun `returns an empty slice for a directory`() {
        val slice = reader.read(directory.toString(), 1024L)

        assertEquals("", slice.text)
        assertFalse(slice.truncated)
    }

    private fun write(name: String, content: String): Path {
        val file = directory.resolve(name)
        Files.writeString(file, content)
        return file
    }
}
