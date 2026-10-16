package fetchy.ytdlp

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

const val YTDLP_COMMAND_NAME: String = "yt-dlp"

fun maskedReusableCommand(
    formatArgsJson: String,
    url: String,
    shell: ShellFamily,
): String {
    val invocation: YtDlpInvocation =
        YtDlpInvocation(urls = listOf(url), userArgs = parseFormatArgs(formatArgsJson))
    val argv: List<String> = listOf(YTDLP_COMMAND_NAME) + ArgvBuilder().build(invocation).maskedArgv
    return CommandStringCodec.format(argv, shell)
}

internal fun parseFormatArgs(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        Json.decodeFromString(ListSerializer(String.serializer()), raw)
    }.getOrElse { emptyList() }
}
