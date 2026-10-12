package fetchy.core.platform

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class SingleInstance(
    private val portFile: Path,
    private val onFocusRequested: () -> Unit = {},
) : AutoCloseable {

    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    fun acquire(): Boolean {
        repeat(MAX_CLAIM_ATTEMPTS) {
            if (trySignalRunningInstance()) return false
            val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            try {
                claimPortFile(server.localPort)
            } catch (exception: FileAlreadyExistsException) {
                runCatching { server.close() }
                if (trySignalRunningInstance()) return false
                runCatching { Files.deleteIfExists(portFile) }
                return@repeat
            }
            synchronized(lock) { serverSocket = server }
            startAcceptLoop(server)
            return true
        }
        return false
    }

    override fun close() {
        synchronized(lock) {
            serverSocket?.close()
            serverSocket = null
        }
        acceptThread?.let { runCatching { it.join(2000) } }
        runCatching { Files.deleteIfExists(portFile) }
    }

    private fun startAcceptLoop(server: ServerSocket) {
        val thread = Thread {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (exception: Exception) {
                    return@Thread
                }
                runCatching {
                    client.use {
                        val line = it.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                        if (line == FOCUS_MESSAGE) {
                            onFocusRequested()
                        }
                    }
                }
            }
        }
        thread.isDaemon = true
        thread.name = "fetchy-single-instance"
        thread.start()
        acceptThread = thread
    }

    private fun trySignalRunningInstance(): Boolean {
        val port = readPort() ?: return false
        return port in 1..65535 && signalFocus(port)
    }

    private fun signalFocus(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1500)
            socket.getOutputStream().write((FOCUS_MESSAGE + "\n").toByteArray(Charsets.US_ASCII))
        }
        true
    }.getOrDefault(false)

    private fun readPort(): Int? = runCatching {
        Files.readString(portFile).trim().toInt()
    }.getOrNull()

    private fun claimPortFile(port: Int) {
        portFile.parent?.let { Files.createDirectories(it) }
        Files.write(
            portFile,
            port.toString().toByteArray(Charsets.US_ASCII),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }

    companion object {
        const val FOCUS_MESSAGE = "FETCHY_FOCUS"
        private const val MAX_CLAIM_ATTEMPTS = 5
    }
}
