package fetchy.model

data class ProgressInfo(
    val id: String,
    val playlistIndex: Int?,
    val status: String,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val totalBytesEstimate: Long?,
    val speed: Double?,
    val etaSeconds: Long?,
) {
    val totalBytesResolved: Long?
        get() = totalBytes ?: totalBytesEstimate

    val fraction: Double?
        get() {
            val total = totalBytesResolved ?: return null
            val downloaded = downloadedBytes ?: return null
            if (total <= 0L) return null
            return (downloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
        }
}
