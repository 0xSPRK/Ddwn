package fetchy.ytdlp.binary

import java.nio.file.Path

data class BinaryInfo(
    val path: Path?,
    val version: String?,
    val source: BinarySource,
    val status: BinaryStatus,
    val mtimeMillis: Long?,
    val sizeBytes: Long?,
    val checkedAtMillis: Long,
)
