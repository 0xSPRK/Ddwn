package fetchy.ytdlp.components.downloader

data class HttpTextResponse(
    val status: Int,
    val body: String,
    val etag: String?,
    val lastModified: String?,
)

interface HttpFetcher {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpTextResponse
}
