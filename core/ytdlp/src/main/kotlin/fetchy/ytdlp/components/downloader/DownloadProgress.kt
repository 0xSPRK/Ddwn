package fetchy.ytdlp.components.downloader

data class DownloadProgress(
    val bytes: Long,
    val total: Long?,
    val speedBps: Long?,
)
