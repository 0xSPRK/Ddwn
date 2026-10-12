package fetchy.core.platform

import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
}