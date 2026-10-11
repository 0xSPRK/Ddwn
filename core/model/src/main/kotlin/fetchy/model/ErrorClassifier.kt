package fetchy.model

object ErrorClassifier {

    private val drmMarkers = listOf("drm", "protected")
    private val notFoundMarkers = listOf("http error 404", "video unavailable", "video is unavailable")
    private val authMarkers = listOf("sign in to confirm", "login", "log in", "sign in to view")
    private val geoMarkers = listOf("not available in your country", "not made this video available in your country")
    private val ffmpegMarkers = listOf("ffmpeg")
    private val noSpaceMarkers = listOf("no space left", "errno 28")
    private val binaryMarkers = listOf(
        "command not found",
        "not recognized as an internal or external command",
        "executable file not found",
        "cannot find the file",
    )
    private val playlistMarkers = listOf("--download-sections", "partial playlist")
    private val networkMarkers = listOf(
        "temporary failure in name resolution",
        "connection reset",
        "unable to connect",
        "getaddrinfo",
        "timed out",
        "urlopen error",
        "connection refused",
        "ssl",
    )
    private val networkContextMarkers = listOf("network", "connection", "http error 5", "timeout", "timed out")

    fun classify(stderr: String, exitCode: Int): ErrorClass {
        val text = stderr.lowercase()

        return when {
            containsAny(text, drmMarkers) -> ErrorClass.DRM_PROTECTED
            containsAny(text, authMarkers) -> ErrorClass.AUTH_REQUIRED
            containsAny(text, geoMarkers) -> ErrorClass.GEO_BLOCKED
            containsAny(text, notFoundMarkers) -> ErrorClass.NOT_FOUND
            containsAny(text, ffmpegMarkers) -> ErrorClass.FFMPEG_MISSING
            containsAny(text, noSpaceMarkers) -> ErrorClass.NO_SPACE
            isBinaryMissing(text, exitCode) -> ErrorClass.BINARY_MISSING
            isNetwork(text) -> ErrorClass.NETWORK
            containsAny(text, playlistMarkers) -> ErrorClass.PLAYLIST_PARTIAL
            else -> ErrorClass.UNKNOWN
        }
    }

    private fun isBinaryMissing(text: String, exitCode: Int): Boolean {
        if (exitCode == 127) return true
        if (containsAny(text, binaryMarkers)) return true
        return "no such file or directory" in text &&
            ("yt-dlp" in text || "ytdlp" in text || "ffmpeg" in text || "ffprobe" in text)
    }

    private fun isNetwork(text: String): Boolean {
        if (containsAny(text, networkMarkers)) return true
        return "unable to download" in text && containsAny(text, networkContextMarkers)
    }

    private fun containsAny(text: String, markers: List<String>): Boolean =
        markers.any { it in text }
}
