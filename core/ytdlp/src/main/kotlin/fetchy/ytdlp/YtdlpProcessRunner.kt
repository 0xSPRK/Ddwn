package fetchy.ytdlp

import fetchy.model.ErrorClassifier
import fetchy.model.ProgressInfo
import fetchy.model.YtdlpError
import fetchy.ytdlp.binary.ProcessStartException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit

sealed interface ProgressEvent {
    data class Stdout(val line: String) : ProgressEvent
    data class Stderr(val line: String) : ProgressEvent
    data class Progress(val info: ProgressInfo) : ProgressEvent
    data class Completed(val exitCode: Int) : ProgressEvent
    data class Failed(val error: YtdlpError) : ProgressEvent
}

data class RunnerConfig(
    val workingDirectory: Path? = null,
    val extraEnvironment: Map<String, String> = emptyMap(),
    val terminateGraceMillis: Long = 2_000L,
)

class YtdlpProcessRunner(private val config: RunnerConfig = RunnerConfig()) {

    fun run(command: List<String>): Flow<ProgressEvent> = channelFlow {
        require(command.isNotEmpty()) { "command must not be empty" }

        val builder: ProcessBuilder = ProcessBuilder(command).apply {
            redirectErrorStream(false)
            directory(config.workingDirectory?.toFile())
            environment().apply {
                put("LANG", "C.UTF-8")
                put("LC_ALL", "C.UTF-8")
                put("PYTHONIOENCODING", "UTF-8")
                putAll(config.extraEnvironment)
            }
        }

        val process: Process =
            try {
                builder.start()
            } catch (e: IOException) {
                throw ProcessStartException(e.message ?: "Process failed to start", e)
            }

        try {
            val stderrText: StringBuilder = StringBuilder()
            val producer: ProducerScope<ProgressEvent> = this
            val stdoutJob = launch(Dispatchers.IO) { producer.drainStdout(process.inputStream) }
            val stderrJob = launch(Dispatchers.IO) { producer.drainStderr(process.errorStream, stderrText) }

            val exitCode: Int = awaitExit(process)
            stdoutJob.join()
            stderrJob.join()

            if (exitCode == 0) {
                send(ProgressEvent.Completed(exitCode))
            } else {
                val rawStderr: String = stderrText.toString()
                send(ProgressEvent.Failed(toError(rawStderr, exitCode)))
            }
        } finally {
            terminate(process)
        }
    }

    private suspend fun awaitExit(process: Process): Int = suspendCancellableCoroutine { cont ->
        process.onExit().whenComplete { finished, _ ->
            if (cont.isActive) cont.resumeWith(Result.success(finished.exitValue()))
        }
        cont.invokeOnCancellation { terminate(process) }
    }

    private suspend fun ProducerScope<ProgressEvent>.drainStdout(stream: InputStream) {
        stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line: String = reader.readLine() ?: break
                val info: ProgressInfo? = ProgressParser.parse(line)
                if (info != null) {
                    send(ProgressEvent.Progress(info))
                } else {
                    send(ProgressEvent.Stdout(line))
                }
            }
        }
    }

    private suspend fun ProducerScope<ProgressEvent>.drainStderr(stream: InputStream, sink: StringBuilder) {
        stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line: String = reader.readLine() ?: break
                sink.append(line).append('\n')
                send(ProgressEvent.Stderr(line))
            }
        }
    }

    private fun toError(rawStderr: String, exitCode: Int): YtdlpError {
        val message: String = rawStderr.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            ?: "yt-dlp exited with code $exitCode"
        return YtdlpError(
            errorClass = ErrorClassifier.classify(rawStderr, exitCode),
            message = message,
            exitCode = exitCode,
            rawStderr = rawStderr,
        )
    }

    private fun terminate(process: Process) {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(config.terminateGraceMillis, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
        }
    }
}
