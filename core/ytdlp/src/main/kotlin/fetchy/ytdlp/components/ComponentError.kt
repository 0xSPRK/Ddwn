package fetchy.ytdlp.components

sealed class ComponentError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    class InsecureRedirect(val location: String) :
        ComponentError("Refusing insecure redirect to $location")

    class HttpError(val status: Int, val url: String) :
        ComponentError("HTTP $status for $url")

    class Offline(cause: Throwable?) :
        ComponentError("Network unavailable: ${cause?.message ?: "unknown"}", cause)

    class ChecksumMismatch(val expected: String, val actual: String) :
        ComponentError("SHA-256 mismatch: expected $expected, got $actual")

    class CorruptArchive(message: String) : ComponentError(message)

    class UnsafeArchiveEntry(val entry: String) :
        ComponentError("Unsafe archive entry: $entry")

    class PostCheckFailed(message: String) : ComponentError(message)

    class NoAsset(message: String) : ComponentError(message)

    class DownloadFailed(message: String) : ComponentError(message)

    class UpdateBlockedByActiveTasks :
        ComponentError("Update blocked by active tasks")
}
