package fetchy.ytdlp

import fetchy.model.DownloadTask
import fetchy.model.ErrorClass
import fetchy.model.TaskStateStore
import fetchy.model.TaskStatus
import fetchy.model.YtdlpError
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SchedulerEvent {
    data class Started(val taskId: String) : SchedulerEvent

    data class Progressed(val taskId: String, val progress: Double) : SchedulerEvent

    data class Finished(
        val taskId: String,
        val status: TaskStatus,
        val errorClass: ErrorClass?,
    ) : SchedulerEvent
}

class DownloadScheduler(
    private val runner: YtdlpProcessRunner,
    private val commandBuilder: YtDlpCommandBuilder,
    private val stateStore: TaskStateStore,
    private val maxParallel: Int = 2,
    private val maxAttempts: Int = 2,
    private val backoffMillis: Long = 1_000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue: Channel<DownloadTask> = Channel(Channel.UNLIMITED)
    private val eventsFlow: MutableSharedFlow<SchedulerEvent> =
        MutableSharedFlow(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val lock: Any = Any()
    private val queuedIds: MutableSet<String> = HashSet()
    private val cancelledIds: MutableSet<String> = HashSet()
    private val runningJobs: MutableMap<String, Job> = HashMap()

    private val started: AtomicBoolean = AtomicBoolean(false)
    private val closed: AtomicBoolean = AtomicBoolean(false)

    val events: Flow<SchedulerEvent> = eventsFlow.asSharedFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        repeat(maxParallel.coerceAtLeast(1)) {
            scope.launch { worker() }
        }
    }

    fun submit(task: DownloadTask): Boolean {
        if (closed.get()) return false
        synchronized(lock) {
            if (task.id in queuedIds || task.id in runningJobs) return false
            queuedIds.add(task.id)
        }
        if (!queue.trySend(task).isSuccess) {
            synchronized(lock) { queuedIds.remove(task.id) }
            return false
        }
        return true
    }

    fun cancel(id: String) {
        val job: Job? =
            synchronized(lock) {
                if (queuedIds.remove(id)) {
                    cancelledIds.add(id)
                    null
                } else {
                    runningJobs[id]
                }
            }
        job?.cancel()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        queue.close()
        scope.cancel()
    }

    private suspend fun worker() {
        for (task in queue) {
            val began: AtomicBoolean = AtomicBoolean(false)
            var cancelQueued: Boolean = false
            val job: Job? =
                synchronized(lock) {
                    if (cancelledIds.remove(task.id)) {
                        queuedIds.remove(task.id)
                        cancelQueued = true
                        null
                    } else if (queuedIds.remove(task.id)) {
                        val launched: Job =
                            scope.launch(start = CoroutineStart.LAZY) {
                                began.set(true)
                                execute(task)
                            }
                        runningJobs[task.id] = launched
                        launched
                    } else {
                        null
                    }
                }

            if (cancelQueued) {
                recordCancelled(task)
                continue
            }
            if (job == null) continue

            job.start()
            job.join()
            val cancelledBeforeRun: Boolean = job.isCancelled && !began.get()
            synchronized(lock) { runningJobs.remove(task.id) }
            if (cancelledBeforeRun) recordCancelled(task)
        }
    }

    private suspend fun execute(task: DownloadTask) {
        var lastProgress: Double = task.progress
        var attempt: Int = 0
        try {
            stateStore.markDownloading(task.id)
            eventsFlow.tryEmit(SchedulerEvent.Started(task.id))

            val attempts: Int = maxAttempts.coerceAtLeast(1)
            while (attempt < attempts) {
                attempt++
                val command: List<String> = commandBuilder.build(task)
                if (command.isEmpty()) {
                    fail(task.id, lastProgress, ErrorClass.BINARY_MISSING)
                    return
                }

                var failure: YtdlpError? = null
                var completed: Boolean = false
                runner.run(command).collect { event ->
                    coroutineContext.ensureActive()
                    when (event) {
                        is ProgressEvent.Progress -> {
                            val fraction: Double = event.info.fraction ?: lastProgress
                            lastProgress = fraction
                            stateStore.updateProgress(task.id, fraction)
                            eventsFlow.tryEmit(SchedulerEvent.Progressed(task.id, fraction))
                        }

                        is ProgressEvent.Completed -> {
                            if (event.exitCode == 0) {
                                completed = true
                            } else {
                                failure =
                                    YtdlpError(
                                        errorClass = ErrorClass.UNKNOWN,
                                        message = "unexpected exit code ${event.exitCode}",
                                        exitCode = event.exitCode,
                                    )
                            }
                        }

                        is ProgressEvent.Failed -> failure = event.error
                        is ProgressEvent.Stdout -> Unit
                        is ProgressEvent.Stderr -> Unit
                    }
                }

                if (completed) {
                    stateStore.markCompleted(task.id, COMPLETED_PROGRESS, clock())
                    eventsFlow.tryEmit(SchedulerEvent.Finished(task.id, TaskStatus.COMPLETED, null))
                    return
                }

                val error: YtdlpError =
                    failure
                        ?: YtdlpError(
                            errorClass = ErrorClass.UNKNOWN,
                            message = "download ended unexpectedly",
                        )
                if (attempt >= attempts || !isRetryable(error.errorClass)) {
                    fail(task.id, lastProgress, error.errorClass)
                    return
                }
                delay(backoffMillis)
            }
        } catch (cancellation: CancellationException) {
            if (!closed.get()) {
                withContext(NonCancellable) { stateStore.markCancelled(task.id, lastProgress, clock()) }
                eventsFlow.tryEmit(SchedulerEvent.Finished(task.id, TaskStatus.CANCELLED, null))
            }
            throw cancellation
        } catch (exception: Exception) {
            if (!closed.get()) {
                withContext(NonCancellable) {
                    stateStore.markFailed(task.id, lastProgress, ErrorClass.UNKNOWN, clock())
                }
                eventsFlow.tryEmit(SchedulerEvent.Finished(task.id, TaskStatus.FAILED, ErrorClass.UNKNOWN))
            }
        }
    }

    private suspend fun fail(id: String, progress: Double, errorClass: ErrorClass?) {
        stateStore.markFailed(id, progress, errorClass, clock())
        eventsFlow.tryEmit(SchedulerEvent.Finished(id, TaskStatus.FAILED, errorClass))
    }

    private suspend fun recordCancelled(task: DownloadTask) {
        stateStore.markCancelled(task.id, task.progress, clock())
        eventsFlow.tryEmit(SchedulerEvent.Finished(task.id, TaskStatus.CANCELLED, null))
    }

    companion object {
        private const val EVENT_BUFFER: Int = 64
        private const val COMPLETED_PROGRESS: Double = 1.0

        private val NON_RETRYABLE: Set<ErrorClass> =
            setOf(
                ErrorClass.NOT_FOUND,
                ErrorClass.AUTH_REQUIRED,
                ErrorClass.GEO_BLOCKED,
                ErrorClass.DRM_PROTECTED,
                ErrorClass.FFMPEG_MISSING,
                ErrorClass.NO_SPACE,
                ErrorClass.BINARY_MISSING,
            )

        private fun isRetryable(errorClass: ErrorClass?): Boolean = errorClass !in NON_RETRYABLE
    }
}
