package fetchy.ytdlp.binary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class ProcessOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class ProcessStartException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ProcessTimeoutException(message: String) : RuntimeException(message)

fun interface ProcessLauncher {
    suspend fun run(command: List<String>, timeoutMillis: Long): ProcessOutput
}

class DefaultProcessLauncher : ProcessLauncher {

    override suspend fun run(command: List<String>, timeoutMillis: Long): ProcessOutput {
        val builder: ProcessBuilder = ProcessBuilder(command)
        builder.redirectErrorStream(false)
        val environment: MutableMap<String, String> = builder.environment()
        environment["LANG"] = "C.UTF-8"
        environment["LC_ALL"] = "C.UTF-8"

        val process: Process =
            try {
                builder.start()
            } catch (e: IOException) {
                throw ProcessStartException(e.message ?: "Process failed to start", e)
            }

        return try {
            coroutineScope {
                val stdout: kotlinx.coroutines.Deferred<ByteArray> =
                    async(Dispatchers.IO) { process.inputStream.use { it.readBytes() } }
                val stderr: kotlinx.coroutines.Deferred<ByteArray> =
                    async(Dispatchers.IO) { process.errorStream.use { it.readBytes() } }
                val finished: Boolean =
                    withContext(Dispatchers.IO) { process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS) }
                if (!finished) {
                    process.destroyForcibly()
                    stdout.cancel()
                    stderr.cancel()
                    throw ProcessTimeoutException("Process timed out after ${timeoutMillis}ms")
                }
                ProcessOutput(
                    exitCode = process.exitValue(),
                    stdout = String(stdout.await(), StandardCharsets.UTF_8),
                    stderr = String(stderr.await(), StandardCharsets.UTF_8),
                )
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
