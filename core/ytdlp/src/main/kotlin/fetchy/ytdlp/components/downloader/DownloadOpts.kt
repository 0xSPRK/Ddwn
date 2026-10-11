package fetchy.ytdlp.components.downloader

data class DownloadOpts(
    val connectTimeoutMs: Long = 5_000,
    val socketTimeoutMs: Long = 30_000,
    val totalTimeoutMs: Long = 600_000,
    val maxRedirects: Int = 5,
    val allowHttp: Boolean = false,
    val useProxy: Boolean = false,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val rangeResume: Boolean = true,
    val maxRetries: Int = 3,
    val retryBackoffMs: Long = 500,
    val keepPartialOnCancel: Boolean = true,
)
