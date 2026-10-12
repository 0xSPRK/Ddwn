package fetchy.feature.add

import fetchy.model.DownloadTask
import fetchy.model.ErrorClass
import fetchy.model.TaskStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddViewModelTest {

    private val creator: DownloadCreator = mockk(relaxed = true)
    private val previewService: UrlPreviewService = mockk()
    private val validUrl: String = "https://example.com/watch?v=abc"

    private fun viewModel(): AddViewModel = AddViewModel(
        creator = creator,
        previewService = previewService,
        clock = { 1000L },
        idGenerator = { "task-1" },
    )

    @Test
    fun `url change resets previous result to idle`() = runTest {
        val viewModel = viewModel()
        coEvery { previewService.preview(validUrl) } returns
            PreviewResult.Success(UrlPreview(title = "Video", thumbnailUrl = null, durationSeconds = 30.0))
        viewModel.onUrlChange(validUrl)
        viewModel.preview()
        assertEquals(AddPhase.READY, viewModel.state.value.phase)

        viewModel.onUrlChange("https://example.com/other")

        assertEquals(AddPhase.IDLE, viewModel.state.value.phase)
        assertNull(viewModel.state.value.preview)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `invalid url without scheme sets error and skips preview`() = runTest {
        val viewModel = viewModel()

        viewModel.onUrlChange("www.example.com/video")
        viewModel.preview()

        assertTrue(viewModel.state.value.error is AddError.InvalidUrl)
        assertEquals(AddPhase.ERROR, viewModel.state.value.phase)
        coVerify(exactly = 0) { previewService.preview(any()) }
    }

    @Test
    fun `non http scheme sets error and skips preview`() = runTest {
        val viewModel = viewModel()

        viewModel.onUrlChange("ftp://example.com/file")
        viewModel.preview()

        assertTrue(viewModel.state.value.error is AddError.InvalidUrl)
        assertEquals(AddPhase.ERROR, viewModel.state.value.phase)
        coVerify(exactly = 0) { previewService.preview(any()) }
    }

    @Test
    fun `successful preview populates ready state`() = runTest {
        val viewModel = viewModel()
        coEvery { previewService.preview(validUrl) } returns
            PreviewResult.Success(
                UrlPreview(title = "My video", thumbnailUrl = "https://img/t.jpg", durationSeconds = 90.0),
            )

        viewModel.onUrlChange(validUrl)
        viewModel.preview()

        val state = viewModel.state.value
        assertEquals(AddPhase.READY, state.phase)
        assertEquals("My video", state.preview?.title)
        assertEquals("https://img/t.jpg", state.preview?.thumbnailUrl)
        assertEquals(90.0, state.preview?.durationSeconds)
        assertTrue(state.canDownload)
    }

    @Test
    fun `drm preview failure produces drm error`() = runTest {
        val viewModel = viewModel()
        coEvery { previewService.preview(validUrl) } returns
            PreviewResult.Failure(ErrorClass.DRM_PROTECTED, "This video is protected by DRM")

        viewModel.onUrlChange(validUrl)
        viewModel.preview()

        val error = viewModel.state.value.error as AddError.Classified
        assertEquals(ErrorClass.DRM_PROTECTED, error.errorClass)
        assertEquals(AddPhase.ERROR, viewModel.state.value.phase)
    }

    @Test
    fun `network preview failure produces classified error`() = runTest {
        val viewModel = viewModel()
        coEvery { previewService.preview(validUrl) } returns
            PreviewResult.Failure(ErrorClass.NETWORK, "connection reset")

        viewModel.onUrlChange(validUrl)
        viewModel.preview()

        val error = viewModel.state.value.error as AddError.Classified
        assertEquals(ErrorClass.NETWORK, error.errorClass)
        assertEquals("connection reset", error.message)
    }

    @Test
    fun `download creates queued task with selected format`() = runTest {
        val viewModel = viewModel()
        coEvery { previewService.preview(validUrl) } returns
            PreviewResult.Success(UrlPreview(title = "My video", thumbnailUrl = null, durationSeconds = 90.0))
        viewModel.onUrlChange(validUrl)
        viewModel.preview()
        viewModel.selectFormat(DownloadFormat.BEST_720P)

        val id: String? = viewModel.download()

        assertEquals("task-1", id)
        val task = slot<DownloadTask>()
        coVerify(exactly = 1) { creator.create(capture(task)) }
        assertEquals("task-1", task.captured.id)
        assertEquals(validUrl, task.captured.url)
        assertEquals("My video", task.captured.title)
        assertEquals(TaskStatus.QUEUED, task.captured.status)
        assertEquals(0.0, task.captured.progress)
        assertEquals(1000L, task.captured.createdAt)
        assertTrue(task.captured.formatArgsJson.isNotBlank())
        assertTrue(task.captured.formatArgsJson.contains("height<=720"))
        assertFalse(task.captured.formatArgsJson.contains("--ignore-config"))
        assertFalse(task.captured.formatArgsJson.contains("--progress-template"))
    }

    @Test
    fun `download stores preview thumbnail on task`() = runTest {
        val viewModel = viewModel()
        coEvery { previewService.preview(validUrl) } returns
            PreviewResult.Success(
                UrlPreview(title = "My video", thumbnailUrl = "https://img/t.jpg", durationSeconds = 90.0),
            )
        viewModel.onUrlChange(validUrl)
        viewModel.preview()

        viewModel.download()

        val task = slot<DownloadTask>()
        coVerify(exactly = 1) { creator.create(capture(task)) }
        assertEquals("https://img/t.jpg", task.captured.thumbnailUrl)
    }

    @Test
    fun `audio format stores audio yt dlp arguments`() = runTest {
        val viewModel = viewModel()
        coEvery { previewService.preview(validUrl) } returns
            PreviewResult.Success(UrlPreview(title = "V", thumbnailUrl = null, durationSeconds = 1.0))
        viewModel.onUrlChange(validUrl)
        viewModel.preview()
        viewModel.selectFormat(DownloadFormat.AUDIO_MP3)

        viewModel.download()

        val task = slot<DownloadTask>()
        coVerify(exactly = 1) { creator.create(capture(task)) }
        assertTrue(task.captured.formatArgsJson.contains("-x"))
        assertTrue(task.captured.formatArgsJson.contains("mp3"))
    }

    @Test
    fun `stale preview result is ignored after url change`() = runTest {
        val gate = CompletableDeferred<PreviewResult>()
        val gatedService = object : UrlPreviewService {
            override suspend fun preview(url: String): PreviewResult = gate.await()
        }
        val viewModel = AddViewModel(
            creator = creator,
            previewService = gatedService,
            clock = { 1L },
            idGenerator = { "t" },
        )

        viewModel.onUrlChange(validUrl)
        val job = async(start = CoroutineStart.UNDISPATCHED) { viewModel.preview() }
        viewModel.onUrlChange("https://example.com/other")
        gate.complete(PreviewResult.Success(UrlPreview(title = "Old", thumbnailUrl = null, durationSeconds = 1.0)))
        job.await()

        assertEquals("https://example.com/other", viewModel.state.value.url)
        assertEquals(AddPhase.IDLE, viewModel.state.value.phase)
        assertNull(viewModel.state.value.preview)
    }

    @Test
    fun `concurrent downloads create only one task`() = runTest {
        val preview = object : UrlPreviewService {
            override suspend fun preview(url: String): PreviewResult =
                PreviewResult.Success(UrlPreview(title = "Video", thumbnailUrl = null, durationSeconds = 1.0))
        }
        val gate = CompletableDeferred<Unit>()
        val created = mutableListOf<DownloadTask>()
        val slowCreator = DownloadCreator { task ->
            created.add(task)
            gate.await()
        }
        val viewModel = AddViewModel(
            creator = slowCreator,
            previewService = preview,
            clock = { 1L },
            idGenerator = { "t" },
        )
        viewModel.onUrlChange(validUrl)
        viewModel.preview()

        val first = async(start = CoroutineStart.UNDISPATCHED) { viewModel.download() }
        val second: String? = viewModel.download()
        gate.complete(Unit)

        assertEquals("t", first.await())
        assertNull(second)
        assertEquals(1, created.size)
    }

    @Test
    fun `download without preview returns null and creates nothing`() = runTest {
        val viewModel = viewModel()

        val id: String? = viewModel.download()

        assertNull(id)
        coVerify(exactly = 0) { creator.create(any()) }
    }

    @Test
    fun `clear resets state to defaults`() = runTest {
        val viewModel = viewModel()
        coEvery { previewService.preview(validUrl) } returns
            PreviewResult.Success(UrlPreview(title = "Video", thumbnailUrl = null, durationSeconds = 1.0))
        viewModel.onUrlChange(validUrl)
        viewModel.preview()

        viewModel.clear()

        assertEquals(AddUiState(), viewModel.state.value)
    }

    @Test
    fun `select format updates selection`() {
        val viewModel = viewModel()

        viewModel.selectFormat(DownloadFormat.AUDIO_MP3)

        assertEquals(DownloadFormat.AUDIO_MP3, viewModel.state.value.selectedFormat)
    }
}
