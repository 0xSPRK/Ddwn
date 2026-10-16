package fetchy.ytdlp

import fetchy.model.DownloadTask
import fetchy.model.ErrorClass
import fetchy.model.ProgressInfo
import fetchy.model.TaskStateStore
import fetchy.model.TaskStatus
import fetchy.model.YtdlpError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadSchedulerTest {

    @Test
    fun `start is idempotent and task runs once`() = runTest {
        val fixture = Fixture(this)
        var runs = 0
        every { fixture.runner.run(any()) } answers {
            runs++
            flowOf(ProgressEvent.Completed(0))
        }
        val scheduler = fixture.scheduler()
        scheduler.start()
        scheduler.start()

        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertEquals(1, runs)
        assertEquals(1, fixture.store.completed.size)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `submit before start is processed after start`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns flowOf(ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler()

        scheduler.submit(task("a"))
        advanceUntilIdle()
        assertTrue(fixture.store.completed.isEmpty())

        scheduler.start()
        advanceUntilIdle()

        assertEquals(1, fixture.store.completed.size)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `concurrency never exceeds max parallel`() = runTest {
        val fixture = Fixture(this)
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        every { fixture.runner.run(any()) } returns
            flow {
                val now = active.incrementAndGet()
                maxActive.updateAndGet { max(it, now) }
                try {
                    delay(1_000L)
                    emit(ProgressEvent.Completed(0))
                } finally {
                    active.decrementAndGet()
                }
            }
        val scheduler = fixture.scheduler(maxParallel = 2)

        scheduler.start()
        scheduler.submit(task("a"))
        scheduler.submit(task("b"))
        scheduler.submit(task("c"))
        advanceUntilIdle()

        assertEquals(2, maxActive.get())
        assertEquals(3, fixture.store.completed.size)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `fifo order is preserved with single worker`() = runTest {
        val fixture = Fixture(this)
        val order = mutableListOf<String>()
        every { fixture.runner.run(any()) } answers {
            order.add(firstArg<List<String>>()[1])
            flowOf(ProgressEvent.Completed(0))
        }
        val scheduler = fixture.scheduler(maxParallel = 1)

        scheduler.start()
        scheduler.submit(task("a"))
        scheduler.submit(task("b"))
        scheduler.submit(task("c"))
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), order)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `retries after failure then succeeds`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returnsMany
            listOf(
                flowOf(ProgressEvent.Failed(YtdlpError(ErrorClass.NETWORK, "reset"))),
                flowOf(ProgressEvent.Completed(0)),
            )
        val scheduler = fixture.scheduler(maxAttempts = 2)

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertEquals(1, fixture.store.completed.size)
        assertTrue(fixture.store.failed.isEmpty())
        verify(exactly = 2) { fixture.runner.run(any()) }
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `exhausted retries mark failed with error class`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns
            flowOf(ProgressEvent.Failed(YtdlpError(ErrorClass.NETWORK, "reset")))
        val scheduler = fixture.scheduler(maxAttempts = 2)

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertEquals(1, fixture.store.failed.size)
        assertEquals(ErrorClass.NETWORK, fixture.store.failed.single().errorClass)
        verify(exactly = 2) { fixture.runner.run(any()) }
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `non retryable failure is not retried`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns
            flowOf(ProgressEvent.Failed(YtdlpError(ErrorClass.DRM_PROTECTED, "drm")))
        val scheduler = fixture.scheduler(maxAttempts = 2)

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertEquals(ErrorClass.DRM_PROTECTED, fixture.store.failed.single().errorClass)
        verify(exactly = 1) { fixture.runner.run(any()) }
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `exception from runner marks failed and worker keeps processing`() = runTest {
        val fixture = Fixture(this)
        var calls = 0
        every { fixture.runner.run(any()) } answers {
            calls++
            if (calls == 1) throw RuntimeException("boom") else flowOf(ProgressEvent.Completed(0))
        }
        val scheduler = fixture.scheduler(maxParallel = 1)

        scheduler.start()
        scheduler.submit(task("a"))
        scheduler.submit(task("b"))
        advanceUntilIdle()

        assertEquals("a", fixture.store.failed.single().id)
        assertEquals(ErrorClass.UNKNOWN, fixture.store.failed.single().errorClass)
        assertEquals("b", fixture.store.completed.single().id)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `submit after close is rejected`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns flowOf(ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler()

        scheduler.start()
        scheduler.close()

        assertFalse(scheduler.submit(task("a")))
        advanceUntilIdle()
        verify(exactly = 0) { fixture.runner.run(any()) }
    }

    @Test
    fun `duplicate active submit is rejected`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        every { fixture.runner.run(any()) } returns
            flow {
                gate.await()
                emit(ProgressEvent.Completed(0))
            }
        val scheduler = fixture.scheduler(maxParallel = 2)

        scheduler.start()
        assertTrue(scheduler.submit(task("a")))
        assertFalse(scheduler.submit(task("a")))
        gate.complete(Unit)
        advanceUntilIdle()

        verify(exactly = 1) { fixture.runner.run(any()) }
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `cancel running task kills process flow and records cancelled`() = runTest {
        val fixture = Fixture(this)
        val flowCancelled = AtomicBoolean(false)
        every { fixture.runner.run(any()) } returns
            flow {
                try {
                    emit(ProgressEvent.Progress(progress(0.5)))
                    awaitCancellation()
                } finally {
                    flowCancelled.set(true)
                }
            }
        val scheduler = fixture.scheduler(maxParallel = 1)
        val events = mutableListOf<SchedulerEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            scheduler.events.collect { events.add(it) }
        }

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()
        scheduler.cancel("a")
        advanceUntilIdle()

        assertTrue(flowCancelled.get())
        assertEquals(1, fixture.store.cancelled.size)
        assertEquals("a", fixture.store.cancelled.single().id)
        assertTrue(events.any { it is SchedulerEvent.Finished && it.status == TaskStatus.CANCELLED })
        assertTrue(fixture.store.failed.isEmpty())
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `cancel queued task never runs and records cancelled`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        every { fixture.runner.run(any()) } answers {
            if (firstArg<List<String>>()[1] == "a") {
                flow {
                    gate.await()
                    emit(ProgressEvent.Completed(0))
                }
            } else {
                flowOf(ProgressEvent.Completed(0))
            }
        }
        val scheduler = fixture.scheduler(maxParallel = 1)

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()
        scheduler.submit(task("b"))
        scheduler.cancel("b")
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(fixture.store.downloading.contains("b"))
        assertEquals(1, fixture.store.cancelled.size)
        assertEquals("b", fixture.store.cancelled.single().id)
        verify(exactly = 1) { fixture.runner.run(any()) }
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `empty command marks binary missing without running`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns flowOf(ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler(builder = YtDlpCommandBuilder { emptyList() })

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertEquals(1, fixture.store.failed.size)
        assertEquals(ErrorClass.BINARY_MISSING, fixture.store.failed.single().errorClass)
        verify(exactly = 0) { fixture.runner.run(any()) }
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `restart of an inactive task submits and runs it`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns flowOf(ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler()

        scheduler.start()
        assertTrue(scheduler.restart(task("a")))
        advanceUntilIdle()

        assertEquals(1, fixture.store.completed.size)
        verify(exactly = 1) { fixture.runner.run(any()) }
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `restart of a running task cancels it and runs it again`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        every { fixture.runner.run(any()) } answers {
            calls++
            if (calls == 1) {
                flow {
                    emit(ProgressEvent.Progress(progress(0.5)))
                    gate.await()
                }
            } else {
                flowOf(ProgressEvent.Completed(0))
            }
        }
        val scheduler = fixture.scheduler(maxParallel = 1)

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertTrue(scheduler.restart(task("a")))
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(1, fixture.store.cancelled.size)
        assertEquals(1, fixture.store.completed.size)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `restart of a queued task cancels the queued run and executes the restart`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns flowOf(ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler(maxParallel = 1)

        scheduler.submit(task("a"))
        assertTrue(scheduler.restart(task("a")))
        scheduler.start()
        advanceUntilIdle()

        assertEquals(1, fixture.store.cancelled.size)
        assertEquals(1, fixture.store.completed.size)
        assertEquals("a", fixture.store.completed.single().id)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `restart after close is rejected`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns flowOf(ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler()

        scheduler.start()
        scheduler.close()

        assertFalse(scheduler.restart(task("a")))
        advanceUntilIdle()
        verify(exactly = 0) { fixture.runner.run(any()) }
    }

    @Test
    fun `progress fraction is persisted`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns
            flowOf(ProgressEvent.Progress(progress(0.5)), ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler()

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertEquals(1, fixture.store.progress.size)
        assertEquals(0.5, fixture.store.progress.single().progress, 1e-9)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `progress without fraction keeps last value`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns
            flowOf(
                ProgressEvent.Progress(progress(0.25)),
                ProgressEvent.Progress(progress(null)),
                ProgressEvent.Completed(0),
            )
        val scheduler = fixture.scheduler()

        scheduler.start()
        scheduler.submit(task("a", progress = 0.1))
        advanceUntilIdle()

        assertEquals(listOf(0.25, 0.25), fixture.store.progress.map { it.progress })
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `mark downloading is recorded on start`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns flowOf(ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler()

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertEquals(listOf("a"), fixture.store.downloading)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `completion records finished timestamp and full progress`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns flowOf(ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler()

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        val completed = fixture.store.completed.single()
        assertEquals("a", completed.id)
        assertEquals(1.0, completed.progress, 1e-9)
        assertEquals(CLOCK, completed.finishedAt)
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `events are emitted for the lifecycle`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns
            flowOf(ProgressEvent.Progress(progress(0.5)), ProgressEvent.Completed(0))
        val scheduler = fixture.scheduler()
        val events = mutableListOf<SchedulerEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            scheduler.events.collect { events.add(it) }
        }

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        assertEquals(SchedulerEvent.Started("a"), events.first())
        assertTrue(events.contains(SchedulerEvent.Progressed("a", 0.5, progress(0.5))))
        assertEquals(SchedulerEvent.Finished("a", TaskStatus.COMPLETED, null), events.last())
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `cancel during backoff records cancelled without retry`() = runTest {
        val fixture = Fixture(this)
        every { fixture.runner.run(any()) } returns
            flowOf(ProgressEvent.Failed(YtdlpError(ErrorClass.NETWORK, "reset")))
        val scheduler = fixture.scheduler(maxAttempts = 2, backoffMillis = 1_000L)

        scheduler.start()
        scheduler.submit(task("a"))
        scheduler.cancel("a")
        advanceUntilIdle()

        assertEquals(1, fixture.store.cancelled.size)
        assertEquals("a", fixture.store.cancelled.single().id)
        assertTrue(fixture.store.failed.isEmpty())
        verify(exactly = 1) { fixture.runner.run(any()) }
        scheduler.close()
        advanceUntilIdle()
    }

    @Test
    fun `close cancels running task without recording cancelled`() = runTest {
        val fixture = Fixture(this)
        val flowCancelled = AtomicBoolean(false)
        every { fixture.runner.run(any()) } returns
            flow {
                try {
                    emit(ProgressEvent.Progress(progress(0.5)))
                    awaitCancellation()
                } finally {
                    flowCancelled.set(true)
                }
            }
        val scheduler = fixture.scheduler(maxParallel = 1)

        scheduler.start()
        scheduler.submit(task("a"))
        advanceUntilIdle()

        scheduler.close()
        advanceUntilIdle()

        assertTrue(flowCancelled.get())
        assertTrue(fixture.store.cancelled.isEmpty())
        assertTrue(fixture.store.failed.isEmpty())
    }

    private class Fixture(private val testScope: TestScope) {
        val runner: YtdlpProcessRunner = mockk()
        val store: RecordingStateStore = RecordingStateStore()
        val builder: YtDlpCommandBuilder = YtDlpCommandBuilder { t -> listOf("yt-dlp", t.id) }

        fun scheduler(
            maxParallel: Int = 2,
            maxAttempts: Int = 2,
            backoffMillis: Long = 1_000L,
            builder: YtDlpCommandBuilder = this.builder,
        ): DownloadScheduler =
            DownloadScheduler(
                runner = runner,
                commandBuilder = builder,
                stateStore = store,
                maxParallel = maxParallel,
                maxAttempts = maxAttempts,
                backoffMillis = backoffMillis,
                clock = { CLOCK },
                dispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
            )
    }

    private class RecordingStateStore : TaskStateStore {
        val downloading: MutableList<String> = mutableListOf()
        val progress: MutableList<ProgressCall> = mutableListOf()
        val completed: MutableList<FinishedCall> = mutableListOf()
        val failed: MutableList<FinishedCall> = mutableListOf()
        val cancelled: MutableList<FinishedCall> = mutableListOf()

        override suspend fun markDownloading(id: String) {
            downloading.add(id)
        }

        override suspend fun updateProgress(id: String, progress: Double) {
            this.progress.add(ProgressCall(id, progress))
        }

        override suspend fun markCompleted(id: String, progress: Double, finishedAt: Long) {
            completed.add(FinishedCall(id, progress, null, finishedAt))
        }

        override suspend fun markFailed(
            id: String,
            progress: Double,
            errorClass: ErrorClass?,
            finishedAt: Long,
        ) {
            failed.add(FinishedCall(id, progress, errorClass, finishedAt))
        }

        override suspend fun markCancelled(id: String, progress: Double, finishedAt: Long) {
            cancelled.add(FinishedCall(id, progress, null, finishedAt))
        }
    }

    private data class ProgressCall(val id: String, val progress: Double)

    private data class FinishedCall(
        val id: String,
        val progress: Double,
        val errorClass: ErrorClass?,
        val finishedAt: Long,
    )
}

private const val CLOCK: Long = 4_242L

private fun task(id: String, progress: Double = 0.0): DownloadTask =
    DownloadTask(
        id = id,
        url = taskUrl(id),
        createdAt = 1L,
        progress = progress,
    )

private fun taskUrl(id: String): String = "$id-url"

private fun progress(fraction: Double?): ProgressInfo =
    ProgressInfo(
        id = "video",
        playlistIndex = null,
        status = "downloading",
        downloadedBytes = fraction?.let { (it * 100.0).toLong() },
        totalBytes = 100L,
        totalBytesEstimate = null,
        speed = null,
        etaSeconds = null,
    )
