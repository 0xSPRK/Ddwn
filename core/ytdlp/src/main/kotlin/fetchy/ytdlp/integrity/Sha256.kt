package fetchy.ytdlp.integrity

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object Sha256 {

    fun of(path: Path): String {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer: ByteArray = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read: Int = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return toHex(digest.digest())
    }

    fun of(bytes: ByteArray): String {
        val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return toHex(digest.digest())
    }

    private fun toHex(bytes: ByteArray): String {
        val builder: StringBuilder = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val value: Int = b.toInt() and 0xFF
            builder.append(HEX[value ushr 4])
            builder.append(HEX[value and 0x0F])
        }
        return builder.toString()
    }

    private const val HEX: String = "0123456789abcdef"
}
