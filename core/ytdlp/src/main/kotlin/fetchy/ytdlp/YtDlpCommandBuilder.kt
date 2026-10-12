package fetchy.ytdlp

import fetchy.model.DownloadTask
import fetchy.ytdlp.binary.BinaryManager
import fetchy.ytdlp.components.ComponentId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

fun interface YtDlpCommandBuilder {
    suspend fun build(task: DownloadTask): List<String>
}

class DefaultYtDlpCommandBuilder(
    private val binaryManager: BinaryManager,
    private val argvBuilder: ArgvBuilder = ArgvBuilder(),
    private val outputDirectory: String? = null,
) : YtDlpCommandBuilder {

    override suspend fun build(task: DownloadTask): List<String> {
        val path = binaryManager.resolve(ComponentId.YT_DLP).path ?: return emptyList()
        val invocation =
            YtDlpInvocation(
                urls = listOf(task.url),
                options = YtDlpOptions(outputDirectory = outputDirectory),
                userArgs = parseFormatArgs(task.formatArgsJson),
            )
        return listOf(path.toString()) + argvBuilder.build(invocation).argv
    }

    private fun parseFormatArgs(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            Json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrElse { emptyList() }
    }
}
