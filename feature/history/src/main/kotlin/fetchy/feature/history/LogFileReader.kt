package fetchy.feature.history

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class LogSlice(
    val text: String,
    val truncated: Boolean,
)

fun interface LogFileReader {
    fun read(path: String, limitBytes: Long): LogSlice
}

class DefaultLogFileReader : LogFileReader {

    override fun read(path: String, limitBytes: Long): LogSlice {
        val file: Path = Path.of(path)
        if (!Files.isRegularFile(file)) return LogSlice(text = "", truncated = false)
        if (Files.size(file) <= limitBytes) {
            val bytes: ByteArray = Files.readAllBytes(file)
            return LogSlice(text = String(bytes, StandardCharsets.UTF_8), truncated = false)
        }
        val limit: Int = limitBytes.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        return Files.newInputStream(file).use { input ->
            val bytes: ByteArray = input.readNBytes(limit)
            LogSlice(text = String(completeUtf8(bytes), StandardCharsets.UTF_8), truncated = true)
        }
    }
}

private fun completeUtf8(bytes: ByteArray): ByteArray {
    var end: Int = bytes.size
    while (end > 0 && isContinuation(bytes[end - 1])) end--
    if (end == 0) return bytes.copyOf(0)
    val needed: Int = utf8Length(bytes[end - 1])
    if (end - 1 + needed > bytes.size) return bytes.copyOf(end - 1)
    return bytes
}

private fun isContinuation(byte: Byte): Boolean = (byte.toInt() and 0xC0) == 0x80

private fun utf8Length(lead: Byte): Int =
    when (lead.toInt() and 0xFF) {
        in 0x00..0x7F -> 1
        in 0xC0..0xDF -> 2
        in 0xE0..0xEF -> 3
        in 0xF0..0xF7 -> 4
        else -> 1
    }
