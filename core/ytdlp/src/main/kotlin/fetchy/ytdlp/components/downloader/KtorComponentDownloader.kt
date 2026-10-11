package fetchy.ytdlp.components.downloader

import fetchy.ytdlp.components.ComponentError
import fetchy.ytdlp.util.FilesExt
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class KtorComponentDownloader(
    private val client: HttpClient = createDefaultClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ComponentDownloader, HttpFetcher {

    override fun download(url: String, dest: Path, opts: DownloadOpts): Flow<DownloadProgress> = flow {
        requireHttps(url, opts)
        FilesExt.ensureParent(dest)
        val part: Path = dest.resolveSibling(dest.fileName.toString() + PART_SUFFIX)
        val attempts: Int = (opts.maxRetries + 1).coerceAtLeast(1)
        var attempt: Int = 0
        var last: ComponentError? = null
        var downloaded: Boolean = false
        while (!downloaded && attempt < attempts) {
            try {
                executeDownload(clientFor(opts), url, part, opts) { progress -> emit(progress) }
                downloaded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: ComponentError.InsecureRedirect) {
                throw e
            } catch (e: ComponentError.HttpError) {
                throw e
            } catch (e: ComponentError) {
                last = e
            } catch (e: IOException) {
                last = ComponentError.Offline(e)
            } catch (e: Exception) {
                last = ComponentError.DownloadFailed(e.message ?: "download failed")
            }
            if (!downloaded) {
                attempt++
                if (attempt < attempts) delay(opts.retryBackoffMs * (1L shl (attempt - 1)))
            }
        }
        if (!downloaded) throw last ?: ComponentError.DownloadFailed("download failed")
        try {
            FilesExt.moveAtomic(part, dest)
        } catch (e: IOException) {
            throw ComponentError.DownloadFailed("Cannot finalize download: ${e.message}")
        }
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpTextResponse {
        try {
            var current: String = url
            var redirects: Int = 0
            val redirectLimit: Int = DownloadOpts().maxRedirects
            while (true) {
                requireHttps(current, DownloadOpts())
                val raw: RawResponse =
                    client.prepareGet(current) {
                        headers.forEach { (key, value) -> header(key, value) }
                        timeout {
                            connectTimeoutMillis = 10_000
                            socketTimeoutMillis = 30_000
                            requestTimeoutMillis = 60_000
                        }
                    }.execute { response ->
                        RawResponse(
                            status = response.status.value,
                            body = if (response.status.value == 200) response.bodyAsText() else "",
                            etag = response.headers[HttpHeaders.ETag],
                            lastModified = response.headers[HttpHeaders.LastModified],
                            location = response.headers[HttpHeaders.Location],
                        )
                    }
                if (raw.status in 300..399 && raw.location != null) {
                    redirects++
                    if (redirects > redirectLimit) {
                        throw ComponentError.DownloadFailed("too many redirects")
                    }
                    current = resolveLocation(current, raw.location)
                    continue
                }
                if (raw.status == 200 || raw.status == 404) {
                    return HttpTextResponse(raw.status, raw.body, raw.etag, raw.lastModified)
                }
                throw ComponentError.HttpError(raw.status, current)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ComponentError.Offline(e)
        }
    }

    private suspend fun executeDownload(
        httpClient: HttpClient,
        initialUrl: String,
        part: Path,
        opts: DownloadOpts,
        emitProgress: suspend (DownloadProgress) -> Unit,
    ) {
        try {
            var current: String = initialUrl
            var redirects: Int = 0
            var restarted: Boolean = false
            while (true) {
                requireHttps(current, opts)
                val existingSize: Long = if (opts.rangeResume) (FilesExt.sizeBytes(part) ?: 0L) else 0L
                val step: Step =
                    httpClient.prepareGet(current) {
                        if (existingSize > 0) header(HttpHeaders.Range, "bytes=$existingSize-")
                        opts.headers.forEach { (key, value) -> header(key, value) }
                        timeout {
                            connectTimeoutMillis = opts.connectTimeoutMs
                            socketTimeoutMillis = opts.socketTimeoutMs
                            requestTimeoutMillis = opts.totalTimeoutMs
                        }
                    }.execute { response ->
                        val status: Int = response.status.value
                        val location: String? = response.headers[HttpHeaders.Location]
                        when {
                            status in 300..399 && location != null ->
                                Step.Redirect(resolveLocation(current, location))

                            status == 416 && existingSize > 0 && !restarted ->
                                Step.Restart

                            status == 200 || status == 206 -> {
                                streamBody(response, part, existingSize, status, emitProgress)
                                Step.Done
                            }

                            else -> throw ComponentError.HttpError(status, current)
                        }
                    }
                when (step) {
                    is Step.Done -> return
                    is Step.Redirect -> {
                        redirects++
                        if (redirects > opts.maxRedirects) {
                            throw ComponentError.DownloadFailed("too many redirects")
                        }
                        current = step.location
                    }

                    is Step.Restart -> {
                        FilesExt.deleteIfExists(part)
                        restarted = true
                        redirects = 0
                    }
                }
            }
        } catch (e: IOException) {
            throw ComponentError.Offline(e)
        }
    }

    private suspend fun streamBody(
        response: HttpResponse,
        part: Path,
        existingSize: Long,
        status: Int,
        emitProgress: suspend (DownloadProgress) -> Unit,
    ) {
        val append: Boolean = status == 206 && existingSize > 0
        val contentLength: Long? = response.contentLength()
        val rangeTotal: Long? = response.headers[HttpHeaders.ContentRange]?.let { parseContentRangeTotal(it) }
        val total: Long? = rangeTotal ?: contentLength?.let { if (append) it + existingSize else it }
        var received: Long = if (append) existingSize else 0L
        val startWall: Long = clock()
        val base: Long = if (append) existingSize else 0L
        val options: Array<OpenOption> =
            if (append) {
                arrayOf(StandardOpenOption.APPEND, StandardOpenOption.CREATE)
            } else {
                arrayOf(
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }
        Files.newOutputStream(part, *options).use { output ->
            val channel: ByteReadChannel = response.bodyAsChannel()
            val buffer: ByteArray = ByteArray(BUFFER_SIZE)
            while (true) {
                val read: Int = channel.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue
                output.write(buffer, 0, read)
                received += read
                val elapsed: Long = clock() - startWall
                val speed: Long? = if (elapsed > 0) ((received - base) * 1000L) / elapsed else null
                emitProgress(DownloadProgress(received, total, speed))
            }
        }
        if (total != null && received < total) {
            throw ComponentError.DownloadFailed("incomplete download: $received of $total bytes")
        }
    }

    private fun clientFor(opts: DownloadOpts): HttpClient {
        val host: String = opts.proxyHost ?: return client
        val port: Int = opts.proxyPort ?: return client
        if (!opts.useProxy) return client
        return HttpClient(OkHttp) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout)
            engine { proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)) }
        }
    }

    private fun requireHttps(url: String, opts: DownloadOpts) {
        val scheme: String? = runCatching { URI(url).scheme }.getOrNull()?.lowercase()
        val allowed: Boolean =
            if (opts.allowHttp) scheme == "http" || scheme == "https" else scheme == "https"
        if (!allowed) {
            throw ComponentError.InsecureRedirect(url)
        }
    }

    private fun resolveLocation(base: String, location: String): String =
        runCatching { URI(base).resolve(location).toString() }.getOrDefault(location)

    private fun parseContentRangeTotal(header: String): Long? {
        val totalPart: String = header.substringAfterLast('/', "").trim()
        return totalPart.takeIf { it.isNotEmpty() && it != "*" }?.toLongOrNull()
    }

    private sealed interface Step {
        data object Done : Step

        data object Restart : Step

        data class Redirect(val location: String) : Step
    }

    private data class RawResponse(
        val status: Int,
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val location: String?,
    )

    companion object {
        const val PART_SUFFIX: String = ".part"
        private const val BUFFER_SIZE: Int = 64 * 1024

        fun createDefaultClient(): HttpClient =
            HttpClient(OkHttp) {
                followRedirects = false
                expectSuccess = false
                install(HttpTimeout)
            }
    }
}
