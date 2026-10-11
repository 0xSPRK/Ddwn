package fetchy.ytdlp

import fetchy.ytdlp.util.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class YtdlpProcessRunnerTest {

    private val runner = YtdlpProcessRunner()

    @Test
    fun streamsStdoutAndCompletes() = runBlocking {
        val events = runner.run(echo("hello")).toList()

        assertEquals(ProgressEvent.Completed(0), events.last())
        assertTrue(events.any { it is ProgressEvent.Stdout && it.line.trim() == "hello" })
    }

    @Test
    fun streamsStderrFromRealProcess() = runBlocking {
        val events = runner.run(listOf(javaExecutable(), "-version")).toList()

        assertEquals(ProgressEvent.Completed(0), events.last())
        assertTrue(events.any { it is ProgressEvent.Stderr && it.line.isNotBlank() })
    }

    @Test
    fun reportsFailureWithExitCodeAndStderr() = runBlocking {
        val events = runner.run(listOf(javaExecutable(), "-no-such-option-xyz")).toList()

        val failure = events.filterIsInstance<ProgressEvent.Failed>().single()
        assertNotEquals(0, failure.error.exitCode)
        assertTrue(failure.error.rawStderr.isNotBlank())
        assertTrue(failure.error.message.isNotBlank())
    }

    @Test
    fun cancelsLongRunningProcess() = runBlocking {
        val config = RunnerConfig(terminateGraceMillis = 1_000L)
        val cancellable = YtdlpProcessRunner(config)

        val start = System.currentTimeMillis()
        val job = launch(Dispatchers.IO) {
            cancellable.run(sleeper()).collect { }
        }
        delay(300L)
        job.cancel()
        job.join()

        val elapsed = System.currentTimeMillis() - start
        assertTrue(elapsed < 8_000L, "cancellation took ${elapsed}ms")
    }

    private fun echo(text: String): List<String> =
        if (Platform.current() == Platform.WINDOWS) {
            listOf("cmd", "/c", "echo", text)
        } else {
            listOf("/bin/echo", text)
        }

    private fun sleeper(): List<String> =
        if (Platform.current() == Platform.WINDOWS) {
            listOf("ping", "-n", "30", "127.0.0.1")
        } else {
            listOf("sleep", "30")
        }

    private fun javaExecutable(): String {
        val name = if (Platform.current() == Platform.WINDOWS) "java.exe" else "java"
        return Paths.get(System.getProperty("java.home"), "bin", name).toString()
    }
}
