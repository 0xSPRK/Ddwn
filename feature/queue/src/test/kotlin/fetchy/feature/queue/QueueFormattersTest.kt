package fetchy.feature.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueueFormattersTest {

    @Test
    fun `formatSpeed uses binary units`() {
        assertEquals("128 B", formatSpeed(128.0))
        assertEquals("2 KB", formatSpeed(2048.0))
        assertEquals("1.5 MB", formatSpeed(1.5 * 1024 * 1024))
        assertEquals("2.0 GB", formatSpeed(2.0 * 1024 * 1024 * 1024))
    }

    @Test
    fun `formatSpeed clamps non positive and non finite values`() {
        assertEquals("0 B", formatSpeed(0.0))
        assertEquals("0 B", formatSpeed(-5.0))
        assertEquals("0 B", formatSpeed(Double.NaN))
    }

    @Test
    fun `formatEta uses mm ss below an hour`() {
        assertEquals("0:05", formatEta(5))
        assertEquals("1:05", formatEta(65))
        assertEquals("59:59", formatEta(3599))
    }

    @Test
    fun `formatEta uses h mm ss from an hour`() {
        assertEquals("1:00:00", formatEta(3600))
        assertEquals("2:03:04", formatEta(7384))
    }

    @Test
    fun `formatEta clamps negative duration`() {
        assertEquals("0:00", formatEta(-10))
    }
}
