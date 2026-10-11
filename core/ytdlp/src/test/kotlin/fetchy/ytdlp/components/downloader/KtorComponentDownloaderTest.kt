package fetchy.ytdlp.components.downloader

import fetchy.ytdlp.components.ComponentError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class KtorComponentDownloaderTest {

    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        root = Files.createTempDirectory("dl")
    }

    private fun downloader(engine: MockEngine): KtorComponentDownloader =
        KtorComponentDownloader(HttpClient(engine))

    private suspend fun expectThrow(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (t: Throwable) {
            return t
        }
        throw AssertionError("Expected an exception but none was thrown")
    }

    @Test
    fun downloadsPayloadAndReportsProgress() = runTest {
        val payload = "hello world".toByteArray()
        val engine =
            MockEngine {
                respond(
                    payload,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentLength, payload.size.toString()),
                )
            }
        val dest = root.resolve("out.bin")

        val events = downloader(engine).download("https://example.com/a.bin", dest).toList()

        assertTrue(Files.exists(dest))
        assertEquals("hello world", Files.readString(dest))
        assertEquals(payload.size.toLong(), events.last().bytes)
        assertEquals(payload.size.toLong(), events.last().total)
    }

    @Test
    fun propagatesHttpErrorStatus() = runTest {
        val engine = MockEngine { respond("missing", HttpStatusCode.NotFound) }
        val dest = root.resolve("out.bin")

        val thrown =
            expectThrow {
                downloader(engine).download("https://example.com/missing", dest, DownloadOpts(maxRetries = 0)).toList()
            }

        val httpError = assertInstanceOf(ComponentError.HttpError::class.java, thrown)
        assertEquals(404, httpError.status)
        assertTrue(!Files.exists(dest))
    }

    @Test
    fun mapsConnectionFailureToOffline() = runTest {
        val engine = MockEngine { throw IOException("connection refused") }
        val dest = root.resolve("out.bin")

        val thrown =
            expectThrow {
                downloader(engine).download("https://example.com/a.bin", dest, DownloadOpts(maxRetries = 0)).toList()
            }

        assertInstanceOf(ComponentError.Offline::class.java, thrown)
    }

    @Test
    fun rejectsRedirectDowngradeToHttp() = runTest {
        val engine =
            MockEngine {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://insecure.example/x"))
            }
        val dest = root.resolve("out.bin")

        val thrown =
            expectThrow {
                downloader(engine).download("https://example.com/a.bin", dest, DownloadOpts(maxRetries = 0)).toList()
            }

        assertInstanceOf(ComponentError.InsecureRedirect::class.java, thrown)
    }

    @Test
    fun resumesPartialDownloadUsingRange() = runTest {
        val prefix = "hello".toByteArray()
        val dest = root.resolve("out.bin")
        val part = root.resolve("out.bin.part")
        Files.write(part, prefix)

        var seenRange: String? = null
        val engine =
            MockEngine { request ->
                seenRange = request.headers[HttpHeaders.Range]
                respond(
                    " world".toByteArray(),
                    HttpStatusCode.PartialContent,
                    headersOf(HttpHeaders.ContentRange, "bytes 5-10/11"),
                )
            }

        downloader(engine).download("https://example.com/a.bin", dest, DownloadOpts(maxRetries = 0)).toList()

        assertEquals("bytes=5-", seenRange)
        assertEquals("hello world", Files.readString(dest))
    }

    @Test
    fun restartsFromScratchWhenServerIgnoresRange() = runTest {
        val dest = root.resolve("out.bin")
        val part = root.resolve("out.bin.part")
        Files.write(part, "stale".toByteArray())

        val engine =
            MockEngine {
                respond(
                    "hi world".toByteArray(),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentLength, "8"),
                )
            }

        downloader(engine).download("https://example.com/a.bin", dest, DownloadOpts(maxRetries = 0)).toList()

        assertEquals("hi world", Files.readString(dest))
    }

    @Test
    fun failsOnIncompleteDownload() = runTest {
        val engine =
            MockEngine {
                respond(
                    "abcde".toByteArray(),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentLength, "10"),
                )
            }
        val dest = root.resolve("out.bin")

        val thrown =
            expectThrow {
                downloader(engine).download("https://example.com/a.bin", dest, DownloadOpts(maxRetries = 0)).toList()
            }

        assertInstanceOf(ComponentError.DownloadFailed::class.java, thrown)
        assertTrue(!Files.exists(dest))
    }

    @Test
    fun failsWhenRedirectLimitExceeded() = runTest {
        val engine =
            MockEngine {
                respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://example.com/next"),
                )
            }
        val dest = root.resolve("out.bin")

        val thrown =
            expectThrow {
                downloader(engine)
                    .download("https://example.com/a.bin", dest, DownloadOpts(maxRetries = 0, maxRedirects = 2))
                    .toList()
            }

        assertInstanceOf(ComponentError.DownloadFailed::class.java, thrown)
        assertTrue(!Files.exists(dest))
    }
}