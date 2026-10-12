package fetchy.core.platform

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SingleInstanceTest {

    @Test
    fun acquireReturnsTrueWhenPortFileAbsent(@TempDir dir: Path) {
        SingleInstance(dir.resolve("port")).use { instance ->
            assertTrue(instance.acquire())
        }
    }

    @Test
    fun secondInstanceSignalsFocusAndIsSecondary(@TempDir dir: Path) {
        val portFile = dir.resolve("port")
        val focused = CountDownLatch(1)
        SingleInstance(portFile, onFocusRequested = { focused.countDown() }).use { primary ->
            assertTrue(primary.acquire())

            assertFalse(SingleInstance(portFile).acquire())
            assertTrue(focused.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun stalePortFileIsOverridden(@TempDir dir: Path) {
        val portFile = dir.resolve("port")
        portFile.toFile().writeText("1")

        SingleInstance(portFile).use { instance ->
            assertTrue(instance.acquire())
            val newPort = portFile.toFile().readText().trim().toInt()
            assertTrue(newPort in 1..65535)
            assertTrue(newPort != 1)
        }
    }

    @Test
    fun closeReleasesTheInstance(@TempDir dir: Path) {
        val portFile = dir.resolve("port")
        SingleInstance(portFile).use { first ->
            assertTrue(first.acquire())
        }
        SingleInstance(portFile).use { second ->
            assertTrue(second.acquire())
        }
    }

    @Test
    fun wrongMessageDoesNotSignalFocusButCorrectDoes(@TempDir dir: Path) {
        val portFile = dir.resolve("port")
        val count = AtomicInteger(0)
        val focused = CountDownLatch(1)
        SingleInstance(portFile, onFocusRequested = {
            count.incrementAndGet()
            focused.countDown()
        }).use { primary ->
            assertTrue(primary.acquire())

            sendMessage(portFile, "NOPE")
            sendMessage(portFile, SingleInstance.FOCUS_MESSAGE)

            assertTrue(focused.await(5, TimeUnit.SECONDS))
            Thread.sleep(200)
            assertEquals(1, count.get())
        }
    }

    @Test
    fun closeIsIdempotentAndRemovesPortFile(@TempDir dir: Path) {
        val portFile = dir.resolve("port")
        val instance = SingleInstance(portFile)
        assertTrue(instance.acquire())

        instance.close()
        instance.close()

        assertFalse(Files.exists(portFile))
    }

    private fun sendMessage(portFile: Path, message: String) {
        val port = portFile.toFile().readText().trim().toInt()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1500)
            socket.getOutputStream().write((message + "\n").toByteArray(Charsets.US_ASCII))
        }
    }
}
