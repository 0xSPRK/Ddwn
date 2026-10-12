package fetchy.feature.add

import fetchy.model.ErrorClass
import fetchy.model.MediaInfo
import fetchy.ytdlp.ArgvBuilder
import fetchy.ytdlp.ProbeParser
import fetchy.ytdlp.ProgressEvent
import fetchy.ytdlp.YtDlpInvocation
import fetchy.ytdlp.YtdlpProcessRunner
import fetchy.ytdlp.binary.BinaryInfo
import fetchy.ytdlp.binary.BinaryManager
import fetchy.ytdlp.binary.BinaryStatus
import fetchy.ytdlp.binary.ProcessStartException
import fetchy.ytdlp.components.ComponentId
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DefaultUrlPreviewService(
    private val binaryManager: BinaryManager,
    private val runner: YtdlpProcessRunner = YtdlpProcessRunner(),
    private val argvBuilder: ArgvBuilder = ArgvBuilder(),
) : UrlPreviewService {

    override suspend fun preview(url: String): PreviewResult {
        val ytDlpPath: Path? = binaryManager.resolve(ComponentId.YT_DLP).takeIf { usable(it) }?.path
        if (ytDlpPath == null) return ffprobePreview(url)

        val command: List<String> =
            listOf(ytDlpPath.toString()) +
                argvBuilder.build(YtDlpInvocation(urls = listOf(url), userArgs = listOf("-J"))).argv

        return when (val result: CommandResult = execute(command)) {
            is CommandResult.Success -> {
                val preview: UrlPreview? = runCatching { YtDlpJsonParser.parse(result.output) }.getOrNull()
                if (preview != null && !preview.isEmpty()) PreviewResult.Success(preview) else ffprobePreview(url)
            }

            is CommandResult.Failure -> {
                if (result.errorClass == ErrorClass.BINARY_MISSING || result.errorClass == ErrorClass.UNKNOWN) {
                    val fallback: PreviewResult = ffprobePreview(url)
                    if (fallback is PreviewResult.Success) fallback else PreviewResult.Failure(result.errorClass, result.message)
                } else {
                    PreviewResult.Failure(result.errorClass, result.message)
                }
            }
        }
    }

    private fun usable(info: BinaryInfo): Boolean =
        info.path != null && (info.status == BinaryStatus.OK || info.status == BinaryStatus.UNKNOWN_VERSION)

    private suspend fun ffprobePreview(url: String): PreviewResult {
        val ffprobePath: Path? = binaryManager.resolve(ComponentId.FFPROBE).takeIf { usable(it) }?.path
            ?: return PreviewResult.Failure(ErrorClass.BINARY_MISSING, "ffprobe is not available")

        val command: List<String> = listOf(
            ffprobePath.toString(),
            "-v",
            "error",
            "-print_format",
            "json",
            "-show_format",
            "-show_streams",
            url,
        )

        return when (val result: CommandResult = execute(command)) {
            is CommandResult.Success -> {
                val info: MediaInfo? = runCatching { ProbeParser.parse(result.output) }.getOrNull()
                if (info == null) {
                    PreviewResult.Failure(ErrorClass.UNKNOWN, "Unable to parse ffprobe output")
                } else {
                    PreviewResult.Success(info.toPreview())
                }
            }

            is CommandResult.Failure -> PreviewResult.Failure(result.errorClass, "ffprobe: ${result.message}")
        }
    }

    private suspend fun execute(command: List<String>): CommandResult {
        val stdout: StringBuilder = StringBuilder()
        var failure: CommandResult.Failure? = null
        try {
            runner.run(command).collect { event: ProgressEvent ->
                when (event) {
                    is ProgressEvent.Stdout -> stdout.append(event.line).append('\n')
                    is ProgressEvent.Failed ->
                        failure = CommandResult.Failure(event.error.errorClass, event.error.message)

                    is ProgressEvent.Completed,
                    is ProgressEvent.Stderr,
                    is ProgressEvent.Progress,
                    -> Unit
                }
            }
        } catch (e: ProcessStartException) {
            return CommandResult.Failure(ErrorClass.BINARY_MISSING, e.message ?: "Process failed to start")
        }
        return failure ?: CommandResult.Success(stdout.toString())
    }

    private sealed interface CommandResult {
        data class Success(val output: String) : CommandResult
        data class Failure(val errorClass: ErrorClass, val message: String) : CommandResult
    }
}

internal fun MediaInfo.toPreview(): UrlPreview = UrlPreview(
    title = title,
    thumbnailUrl = null,
    durationSeconds = durationSeconds,
)

private fun UrlPreview.isEmpty(): Boolean =
    title == null && thumbnailUrl == null && durationSeconds == null

internal object YtDlpJsonParser {

    fun parse(json: String): UrlPreview {
        val root: JsonObject = Json.parseToJsonElement(json).jsonObject
        val target: JsonObject = root["entries"]?.jsonArray?.firstOrNull()?.jsonObject ?: root
        return UrlPreview(
            title = target.string("title") ?: root.string("title"),
            thumbnailUrl = target.string("thumbnail") ?: root.string("thumbnail"),
            durationSeconds = (target.string("duration") ?: root.string("duration"))?.toDoubleOrNull(),
        )
    }

    private fun JsonObject.string(key: String): String? {
        val element = this[key] ?: return null
        if (element is JsonNull) return null
        val primitive: JsonPrimitive = element as? JsonPrimitive ?: return null
        return primitive.content.takeIf { it.isNotBlank() }
    }
}
