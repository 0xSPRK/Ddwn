package fetchy.feature.add

import fetchy.model.ErrorClass
import fetchy.model.YtdlpError
import fetchy.ytdlp.ProgressEvent
import fetchy.ytdlp.YtdlpProcessRunner
import fetchy.ytdlp.binary.BinaryInfo
import fetchy.ytdlp.binary.BinaryManager
import fetchy.ytdlp.binary.BinarySource
import fetchy.ytdlp.binary.BinaryStatus
import fetchy.ytdlp.components.ComponentId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Path
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultUrlPreviewServiceTest {

    private val runner: YtdlpProcessRunner = mockk()
    private val binaryManager: BinaryManager = mockk()
    private val service = DefaultUrlPreviewService(binaryManager = binaryManager, runner = runner)

    private val ytDlpJson: String =
        """{"title":"Clip","thumbnail":"https://img/t.jpg","duration":12.5}"""
    private val probeJson: String =
        """{"format":{"format_name":"mp4","duration":"30.0","tags":{"title":"Probe title"}},"streams":[{"index":0,"codec_type":"video"}]}"""

    @Test
    fun `yt dlp success yields parsed preview`() = runTest {
        coEvery { binaryManager.resolve(ComponentId.YT_DLP, any()) } returns info(Path.of("/bin/yt-dlp"))
        every { runner.run(any()) } returns flowOf(
            ProgressEvent.Stdout(ytDlpJson),
            ProgressEvent.Completed(0),
        )

        val result = service.preview("https://example.com/v")

        val success = result as PreviewResult.Success
        assertEquals("Clip", success.preview.title)
        assertEquals("https://img/t.jpg", success.preview.thumbnailUrl)
        assertEquals(12.5, success.preview.durationSeconds)
    }

    @Test
    fun `yt dlp failure falls back to ffprobe`() = runTest {
        coEvery { binaryManager.resolve(ComponentId.YT_DLP, any()) } returns info(Path.of("/bin/yt-dlp"))
        coEvery { binaryManager.resolve(ComponentId.FFPROBE, any()) } returns info(Path.of("/bin/ffprobe"))
        every { runner.run(any()) } returnsMany listOf(
            flowOf(ProgressEvent.Failed(YtdlpError(ErrorClass.GEO_BLOCKED, "geo"))),
            flowOf(ProgressEvent.Stdout(probeJson), ProgressEvent.Completed(0)),
        )

        val result = service.preview("https://example.com/v")

        val success = result as PreviewResult.Success
        assertEquals("Probe title", success.preview.title)
        assertEquals(30.0, success.preview.durationSeconds)
        assertNull(success.preview.thumbnailUrl)
    }

    @Test
    fun `when both paths fail the yt dlp error is surfaced`() = runTest {
        coEvery { binaryManager.resolve(ComponentId.YT_DLP, any()) } returns info(Path.of("/bin/yt-dlp"))
        coEvery { binaryManager.resolve(ComponentId.FFPROBE, any()) } returns info(Path.of("/bin/ffprobe"))
        every { runner.run(any()) } returnsMany listOf(
            flowOf(ProgressEvent.Failed(YtdlpError(ErrorClass.DRM_PROTECTED, "drm protected"))),
            flowOf(ProgressEvent.Failed(YtdlpError(ErrorClass.UNKNOWN, "probe failed"))),
        )

        val result = service.preview("https://example.com/v")

        val failure = result as PreviewResult.Failure
        assertEquals(ErrorClass.DRM_PROTECTED, failure.errorClass)
    }

    @Test
    fun `missing yt dlp binary falls back to ffprobe`() = runTest {
        coEvery { binaryManager.resolve(ComponentId.YT_DLP, any()) } returns info(null)
        coEvery { binaryManager.resolve(ComponentId.FFPROBE, any()) } returns info(Path.of("/bin/ffprobe"))
        every { runner.run(any()) } returns flowOf(
            ProgressEvent.Stdout(probeJson),
            ProgressEvent.Completed(0),
        )

        val result = service.preview("https://example.com/v")

        assertTrue(result is PreviewResult.Success)
    }

    @Test
    fun `missing both binaries yields binary missing failure`() = runTest {
        coEvery { binaryManager.resolve(ComponentId.YT_DLP, any()) } returns info(null)
        coEvery { binaryManager.resolve(ComponentId.FFPROBE, any()) } returns info(null)

        val result = service.preview("https://example.com/v")

        val failure = result as PreviewResult.Failure
        assertEquals(ErrorClass.BINARY_MISSING, failure.errorClass)
    }

    private fun info(path: Path?): BinaryInfo = BinaryInfo(
        path = path,
        version = if (path == null) null else "1.0",
        source = if (path == null) BinarySource.NONE else BinarySource.PATH,
        status = if (path == null) BinaryStatus.NOT_FOUND else BinaryStatus.OK,
        mtimeMillis = null,
        sizeBytes = null,
        checkedAtMillis = 0L,
    )
}
