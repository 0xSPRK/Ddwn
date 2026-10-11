package fetchy.ytdlp

import fetchy.model.ProgressInfo

object ProgressParser {

    const val LABEL: String = "download:FETCHY"
    const val PREFIX: String = "$LABEL|"
    private const val FIELD_COUNT: Int = 9
    private val NA_TOKENS: Set<String> = setOf("NA", "N/A", "None", "-")

    fun isProgress(line: String): Boolean = line.startsWith(PREFIX)

    fun parse(line: String): ProgressInfo? {
        if (!isProgress(line)) return null
        val parts: List<String> = line.split('|')
        if (parts.size != FIELD_COUNT) return null
        if (parts[0] != LABEL) return null

        val id: String = parts[1]
        if (id.isBlank() || isMissing(id)) return null

        val status: String = parts[3].ifBlank { return null }

        return ProgressInfo(
            id = id,
            playlistIndex = missingAware(parts[2])?.toIntOrNull(),
            status = status,
            downloadedBytes = missingAware(parts[4])?.toLongOrNull(),
            totalBytes = missingAware(parts[5])?.toLongOrNull(),
            totalBytesEstimate = missingAware(parts[6])?.toLongOrNull(),
            speed = missingAware(parts[7])?.toDoubleOrNull(),
            etaSeconds = missingAware(parts[8])?.toLongOrNull(),
        )
    }

    private fun missingAware(token: String): String? =
        token.takeIf { it.isNotBlank() && !isMissing(it) }

    private fun isMissing(token: String): Boolean = token.trim() in NA_TOKENS
}
