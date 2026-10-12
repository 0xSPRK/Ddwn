package fetchy.feature.add

import fetchy.model.DownloadTask
import fetchy.model.ErrorClass
import fetchy.model.TaskStatus
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class AddPhase {
    IDLE,
    PARSING,
    READY,
    ERROR,
}

enum class DownloadFormat(val formatArgs: List<String>) {
    BEST(emptyList()),
    BEST_1080P(
        listOf(
            "-f",
            "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
            "--merge-output-format",
            "mp4",
        ),
    ),
    BEST_720P(
        listOf(
            "-f",
            "bestvideo[height<=720]+bestaudio/best[height<=720]",
            "--merge-output-format",
            "mp4",
        ),
    ),
    BEST_480P(
        listOf(
            "-f",
            "bestvideo[height<=480]+bestaudio/best[height<=480]",
            "--merge-output-format",
            "mp4",
        ),
    ),
    AUDIO_MP3(listOf("-x", "--audio-format", "mp3")),
    AUDIO_M4A(listOf("-x", "--audio-format", "m4a")),
}

sealed interface AddError {
    data object InvalidUrl : AddError
    data class Classified(val errorClass: ErrorClass, val message: String) : AddError
}

data class AddUiState(
    val url: String = "",
    val phase: AddPhase = AddPhase.IDLE,
    val preview: UrlPreview? = null,
    val selectedFormat: DownloadFormat = DownloadFormat.BEST,
    val error: AddError? = null,
    val isCreating: Boolean = false,
) {
    val canPreview: Boolean
        get() = url.isNotBlank() && phase != AddPhase.PARSING

    val canDownload: Boolean
        get() = phase == AddPhase.READY && !isCreating
}

fun interface DownloadCreator {
    suspend fun create(task: DownloadTask)
}

class AddViewModel(
    private val creator: DownloadCreator,
    private val previewService: UrlPreviewService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    private val _state: MutableStateFlow<AddUiState> = MutableStateFlow(AddUiState())

    val state: StateFlow<AddUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) {
        _state.update {
            it.copy(url = value, phase = AddPhase.IDLE, preview = null, error = null)
        }
    }

    fun selectFormat(format: DownloadFormat) {
        _state.update { it.copy(selectedFormat = format) }
    }

    fun clear() {
        _state.value = AddUiState()
    }

    suspend fun preview() {
        val url: String = _state.value.url.trim()
        if (!isValidHttpUrl(url)) {
            _state.update { it.copy(phase = AddPhase.ERROR, error = AddError.InvalidUrl, preview = null) }
            return
        }

        _state.update { it.copy(phase = AddPhase.PARSING, error = null, preview = null) }

        when (val result: PreviewResult = previewService.preview(url)) {
            is PreviewResult.Success -> _state.update { current ->
                if (current.url.trim() != url) current
                else current.copy(phase = AddPhase.READY, preview = result.preview, error = null)
            }

            is PreviewResult.Failure -> _state.update { current ->
                if (current.url.trim() != url) {
                    current
                } else {
                    current.copy(
                        phase = AddPhase.ERROR,
                        error = AddError.Classified(result.errorClass, result.message),
                        preview = null,
                    )
                }
            }
        }
    }

    suspend fun download(): String? {
        val current: AddUiState = _state.value
        val url: String = current.url.trim()
        if (current.phase != AddPhase.READY || current.isCreating || !isValidHttpUrl(url)) return null

        _state.update { it.copy(isCreating = true) }

        val id: String = idGenerator()
        val task: DownloadTask = DownloadTask(
            id = id,
            url = url,
            title = current.preview?.title,
            thumbnailUrl = current.preview?.thumbnailUrl,
            formatArgsJson = formatArgsJson(current.selectedFormat),
            status = TaskStatus.QUEUED,
            createdAt = clock(),
        )
        try {
            creator.create(task)
        } finally {
            _state.update { it.copy(isCreating = false) }
        }
        return id
    }

    private fun formatArgsJson(format: DownloadFormat): String =
        Json.encodeToString(ListSerializer(String.serializer()), format.formatArgs)

    private fun isValidHttpUrl(value: String): Boolean {
        val uri: URI = runCatching { URI(value) }.getOrNull() ?: return false
        val scheme: String? = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }
}
