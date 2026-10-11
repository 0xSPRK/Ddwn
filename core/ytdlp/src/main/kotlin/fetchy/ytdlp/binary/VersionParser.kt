package fetchy.ytdlp.binary

import fetchy.ytdlp.components.ComponentId

object VersionParser {

    private val YT_DLP_PATTERN: Regex = Regex("""(\d{4}\.\d{2}\.\d{2}[^\s]*)""")
    private val VERSION_TOKEN_PATTERN: Regex = Regex("""version\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val DENO_PATTERN: Regex = Regex("""^\s*deno\s+(\d+(?:\.\d+)+(?:[^\s]*))""", RegexOption.MULTILINE)

    fun parse(component: ComponentId, output: String): String? {
        if (output.isBlank()) return null
        return when (component) {
            ComponentId.YT_DLP -> parseYtDlp(output)
            ComponentId.FFMPEG, ComponentId.FFPROBE -> parseFfmpeg(output)
            ComponentId.DENO -> parseDeno(output)
        }
    }

    fun detectStatus(component: ComponentId, output: String): BinaryStatus =
        if (parse(component, output) != null) BinaryStatus.OK else BinaryStatus.UNKNOWN_VERSION

    fun parseYtDlp(output: String): String? =
        YT_DLP_PATTERN.find(output)?.groupValues?.get(1)

    fun parseFfmpeg(output: String): String? {
        val firstLine: String = output.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        return VERSION_TOKEN_PATTERN.find(firstLine)?.groupValues?.get(1)
    }

    fun parseDeno(output: String): String? =
        DENO_PATTERN.find(output)?.groupValues?.get(1)
}
