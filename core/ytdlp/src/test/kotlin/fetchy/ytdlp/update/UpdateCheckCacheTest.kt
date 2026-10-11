package fetchy.ytdlp.update

import fetchy.ytdlp.components.ComponentError
import fetchy.ytdlp.components.ComponentId
import fetchy.ytdlp.components.downloader.HttpFetcher
import fetchy.ytdlp.components.downloader.HttpTextResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class UpdateCheckCacheTest {

    private lateinit var root: Path
    private lateinit var cacheFile: Path
    private var now: Long = 0L

    @BeforeEach
    fun setUp() {
        root = Files.createTempDirectory("upd")
        cacheFile = root.resolve("updates.json")
        now = 0L
    }

    private class StubFetcher(
        private val handler: suspend (String, Map<String, String>) -> HttpTextResponse,
    ) : HttpFetcher {
        var calls: Int = 0
            private set
        var lastHeaders: Map<String, String> = emptyMap()
            private set

        override suspend fun get(url: String, headers: Map<String, String>): HttpTextResponse {
            calls++
            lastHeaders = headers
            return handler(url, headers)
        }
    }

    private fun okFetcher(etag: String?, tag: String = "2026.08.19"): StubFetcher =
        StubFetcher { _, _ -> HttpTextResponse(200, """{"tag_name":"$tag","assets":[]}""", etag, null) }

    private fun cache(fetcher: HttpFetcher, ttlMillis: Long = 21_600_000L, manualRateLimitMillis: Long = 1_000L) =
        UpdateCheckCache(
            cacheFile = cacheFile,
            fetcher = fetcher,
            clock = { now },
            ttlMillis = ttlMillis,
            manualRateLimitMillis = manualRateLimitMillis,
        )

    @Test
    fun servesFromCacheWithinTtlWithoutNetwork() = runTest {
        val fetcher = okFetcher(null)
        val cache = cache(fetcher)

        val first = cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS)
        now = 600_000L
        val second = cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS)

        assertEquals(1, fetcher.calls)
        assertEquals(first, second)
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, second.status)
    }

    @Test
    fun manualRecheckIsRateLimited() = runTest {
        val fetcher = okFetcher(null)
        val cache = cache(fetcher)
        cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS)

        now = 10_000L
        cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS, manual = true)
        assertEquals(2, fetcher.calls)

        now = 10_500L
        cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS, manual = true)

        assertEquals(2, fetcher.calls)
    }

    @Test
    fun sendsIfNoneMatchAndHandlesNotModified() = runTest {
        val etag = "W/\"abc123\""
        var respondNotModified = false
        val fetcher =
            StubFetcher { _, headers ->
                if (respondNotModified && headers["If-None-Match"] == etag) {
                    HttpTextResponse(304, "", null, null)
                } else {
                    HttpTextResponse(200, """{"tag_name":"2026.08.19","assets":[]}""", etag, null)
                }
            }
        val cache = cache(fetcher)
        val warm = cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS)
        assertEquals(UpdateStatus.UPDATE_AVAILABLE, warm.status)

        respondNotModified = true
        now = 22_000_000L
        val refreshed = cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS)

        assertEquals(2, fetcher.calls)
        assertEquals(etag, fetcher.lastHeaders["If-None-Match"])
        assertEquals(now, refreshed.checkedAtMillis)
        assertEquals(etag, refreshed.etag)
    }

    @Test
    fun reportsOfflineWithoutThrowing() = runTest {
        val fetcher = StubFetcher { _, _ -> throw ComponentError.Offline(null) }
        val cache = cache(fetcher)

        val info = cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS, currentVersion = "1.2.3")

        assertEquals(UpdateStatus.OFFLINE, info.status)
        assertEquals("1.2.3", info.currentVersion)
    }

    @Test
    fun persistsAndReloadsCache() = runTest {
        val fetcher = okFetcher("W/\"e\"")
        val cache = cache(fetcher)
        cache.check(ComponentId.YT_DLP, RELEASE_URL, ASSETS, currentVersion = "2026.01.01")

        val reloaded = cache(StubFetcher { _, _ -> error("should not be called") })
        val cached = reloaded.cached(ComponentId.YT_DLP)

        assertNotNull(cached)
        assertEquals("2026.01.01", cached?.currentVersion)
        assertEquals("2026.08.19", cached?.latestVersion)
        assertEquals("W/\"e\"", cached?.etag)
    }

    @Test
    fun returnsNullForUnknownComponent() {
        assertNull(cache(okFetcher(null)).cached(ComponentId.DENO))
    }

    private companion object {
        const val RELEASE_URL: String = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        val ASSETS: List<String> = listOf("yt-dlp_linux")
    }
}