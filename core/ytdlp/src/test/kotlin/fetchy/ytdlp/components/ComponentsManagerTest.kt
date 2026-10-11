package fetchy.ytdlp.components

import fetchy.ytdlp.binary.ProcessLauncher
import fetchy.ytdlp.binary.ProcessOutput
import fetchy.ytdlp.components.downloader.ComponentDownloader
import fetchy.ytdlp.components.downloader.DownloadOpts
import fetchy.ytdlp.components.downloader.DownloadProgress
import fetchy.ytdlp.components.downloader.HttpFetcher
import fetchy.ytdlp.components.downloader.HttpTextResponse
import fetchy.ytdlp.integrity.IntegrityResult
import fetchy.ytdlp.integrity.Sha256
import fetchy.ytdlp.util.AppPaths
import fetchy.ytdlp.util.Platform
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ComponentsManagerTest {

    private lateinit var root: Path
    private lateinit var appPaths: AppPaths

    @BeforeEach
    fun setUp() {
        root = Files.createTempDirectory("cmgr")
        appPaths = AppPaths(root, root.resolve("components"), root.resolve("cache"))
    }

    private class FakeSources(private val descriptor: ReleaseDescriptor) : ComponentSources {
        override fun descriptor(component: ComponentId, channel: YtDlpChannel): ReleaseDescriptor = descriptor
    }

    private class StubDownloader(private val content: ByteArray) : ComponentDownloader {
        var calls: Int = 0
            private set

        override fun download(url: String, dest: Path, opts: DownloadOpts): Flow<DownloadProgress> = flow {
            calls++
            dest.parent?.let { Files.createDirectories(it) }
            Files.write(dest, content)
            emit(DownloadProgress(content.size.toLong(), content.size.toLong(), null))
        }
    }

    private class StubFetcher(
        private val responses: Map<String, () -> HttpTextResponse>,
    ) : HttpFetcher {
        override suspend fun get(url: String, headers: Map<String, String>): HttpTextResponse {
            yield()
            return responses.getValue(url).invoke()
        }
    }

    private class FakeLauncher(private val exitCode: Int = 0) : ProcessLauncher {
        override suspend fun run(command: List<String>, timeoutMillis: Long): ProcessOutput =
            ProcessOutput(exitCode, "2026.08.19", "")
    }

    private fun releaseJson(vararg assets: Pair<String, String>): String {
        val assetsJson =
            assets.joinToString(",") { (name, url) ->
                """{"name":"$name","browser_download_url":"$url","size":16}"""
            }
        return """{"tag_name":"2026.08.19","assets":[$assetsJson]}"""
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private fun targetPath(): Path = appPaths.componentsDir.resolve("yt-dlp")

    private fun manager(
        downloader: ComponentDownloader,
        fetcher: HttpFetcher,
        descriptor: ReleaseDescriptor,
        activeTasks: () -> Boolean = { false },
    ): ComponentsManager =
        ComponentsManager(
            appPaths = appPaths,
            downloader = downloader,
            httpFetcher = fetcher,
            platform = Platform.LINUX,
            sources = FakeSources(descriptor),
            processLauncher = FakeLauncher(),
            activeTasks = activeTasks,
        )

    private fun binaryDescriptor(assetName: String): ReleaseDescriptor =
        ReleaseDescriptor(
            component = ComponentId.YT_DLP,
            releaseApiUrl = "https://api.github.com/repos/x/y/releases/latest",
            assetCandidates = listOf(assetName),
            checksumCandidates = listOf("SHA2-256SUMS"),
        )

    @Test
    fun installsVerifiedBinary() = runTest {
        val binary = "fake-binary".toByteArray()
        val hash = Sha256.of(binary)
        val descriptor = binaryDescriptor("fetchy-bin")
        val fetcher =
            StubFetcher(
                mapOf(
                    descriptor.releaseApiUrl to {
                        HttpTextResponse(
                            200,
                            releaseJson(
                                "fetchy-bin" to "https://cdn.example.com/fetchy-bin",
                                "SHA2-256SUMS" to "https://cdn.example.com/SHA2-256SUMS",
                            ),
                            null,
                            null,
                        )
                    },
                    "https://cdn.example.com/SHA2-256SUMS" to {
                        HttpTextResponse(200, "$hash  fetchy-bin\n", null, null)
                    },
                ),
            )

        val result = manager(StubDownloader(binary), fetcher, descriptor).install(ComponentId.YT_DLP)

        val installed = assertInstanceOf(InstallOutcome.Installed::class.java, result)
        assertEquals("2026.08.19", installed.version)
        assertEquals(hash, assertInstanceOf(IntegrityResult.Verified::class.java, installed.integrity).sha256)
        assertEquals(targetPath(), installed.target)
        assertEquals("fake-binary", Files.readString(targetPath()))
        assertTrue(noWorkDirsLeft())
    }

    @Test
    fun failsWithChecksumMismatch() = runTest {
        val binary = "fake-binary".toByteArray()
        val wrong = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val descriptor = binaryDescriptor("fetchy-bin")
        val fetcher =
            StubFetcher(
                mapOf(
                    descriptor.releaseApiUrl to {
                        HttpTextResponse(
                            200,
                            releaseJson(
                                "fetchy-bin" to "https://cdn.example.com/fetchy-bin",
                                "SHA2-256SUMS" to "https://cdn.example.com/SHA2-256SUMS",
                            ),
                            null,
                            null,
                        )
                    },
                    "https://cdn.example.com/SHA2-256SUMS" to {
                        HttpTextResponse(200, "$wrong  fetchy-bin\n", null, null)
                    },
                ),
            )

        val result = manager(StubDownloader(binary), fetcher, descriptor).install(ComponentId.YT_DLP)

        val failed = assertInstanceOf(InstallOutcome.Failed::class.java, result)
        val error = assertInstanceOf(ComponentError.ChecksumMismatch::class.java, failed.error)
        assertEquals(wrong, error.expected)
        assertEquals(Sha256.of(binary), error.actual)
        assertTrue(!Files.exists(targetPath()))
    }

    @Test
    fun marksNotVerifiedWhenSourceHasNoChecksumAsset() = runTest {
        val binary = "fake-binary".toByteArray()
        val descriptor = binaryDescriptor("fetchy-bin")
        val fetcher =
            StubFetcher(
                mapOf(
                    descriptor.releaseApiUrl to {
                        HttpTextResponse(
                            200,
                            releaseJson("fetchy-bin" to "https://cdn.example.com/fetchy-bin"),
                            null,
                            null,
                        )
                    },
                ),
            )

        val result = manager(StubDownloader(binary), fetcher, descriptor).install(ComponentId.YT_DLP)

        val installed = assertInstanceOf(InstallOutcome.Installed::class.java, result)
        assertInstanceOf(IntegrityResult.NotVerified::class.java, installed.integrity)
    }

    @Test
    fun failsOnCorruptArchive() = runTest {
        val descriptor = binaryDescriptor("fetchy-bin.zip")
        val fetcher =
            StubFetcher(
                mapOf(
                    descriptor.releaseApiUrl to {
                        HttpTextResponse(
                            200,
                            releaseJson("fetchy-bin.zip" to "https://cdn.example.com/fetchy-bin.zip"),
                            null,
                            null,
                        )
                    },
                ),
            )

        val result =
            manager(StubDownloader("not a zip".toByteArray()), fetcher, descriptor).install(ComponentId.YT_DLP)

        val failed = assertInstanceOf(InstallOutcome.Failed::class.java, result)
        assertInstanceOf(ComponentError.CorruptArchive::class.java, failed.error)
        assertTrue(!Files.exists(targetPath()))
    }

    @Test
    fun failsOnZipSlipEntry() = runTest {
        val descriptor = binaryDescriptor("fetchy-bin.zip")
        val fetcher =
            StubFetcher(
                mapOf(
                    descriptor.releaseApiUrl to {
                        HttpTextResponse(
                            200,
                            releaseJson("fetchy-bin.zip" to "https://cdn.example.com/fetchy-bin.zip"),
                            null,
                            null,
                        )
                    },
                ),
            )
        val archive = zipBytes("../evil.txt" to "boom".toByteArray())

        val result = manager(StubDownloader(archive), fetcher, descriptor).install(ComponentId.YT_DLP)

        val failed = assertInstanceOf(InstallOutcome.Failed::class.java, result)
        assertInstanceOf(ComponentError.UnsafeArchiveEntry::class.java, failed.error)
    }

    @Test
    fun blocksInstallWhileTasksAreActive() = runTest {
        val descriptor = binaryDescriptor("fetchy-bin")
        val downloader = StubDownloader("fake-binary".toByteArray())
        val fetcher = StubFetcher(emptyMap())

        val result =
            manager(downloader, fetcher, descriptor, activeTasks = { true }).install(ComponentId.YT_DLP)

        assertInstanceOf(InstallOutcome.BlockedByActiveTasks::class.java, result)
        assertEquals(0, downloader.calls)
    }

    @Test
    fun coalescesConcurrentInstallsIntoOneDownload() = runTest {
        val binary = "fake-binary".toByteArray()
        val hash = Sha256.of(binary)
        val descriptor = binaryDescriptor("fetchy-bin")
        val fetcher =
            StubFetcher(
                mapOf(
                    descriptor.releaseApiUrl to {
                        HttpTextResponse(
                            200,
                            releaseJson(
                                "fetchy-bin" to "https://cdn.example.com/fetchy-bin",
                                "SHA2-256SUMS" to "https://cdn.example.com/SHA2-256SUMS",
                            ),
                            null,
                            null,
                        )
                    },
                    "https://cdn.example.com/SHA2-256SUMS" to {
                        HttpTextResponse(200, "$hash  fetchy-bin\n", null, null)
                    },
                ),
            )
        val downloader = StubDownloader(binary)
        val manager = manager(downloader, fetcher, descriptor)

        val first = async { manager.install(ComponentId.YT_DLP) }
        val second = async { manager.install(ComponentId.YT_DLP) }
        val results = listOf(first.await(), second.await())

        results.forEach { assertInstanceOf(InstallOutcome.Installed::class.java, it) }
        assertEquals(1, downloader.calls)
    }

    private fun noWorkDirsLeft(): Boolean =
        Files.list(appPaths.cacheDir).use { stream ->
            stream.noneMatch { it.fileName.toString().startsWith("install-") }
        }
}