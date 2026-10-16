package fetchy.feature.history

import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryFormattersTest {

    @Test
    fun `formats a timestamp in the supplied zone`() {
        val millis = Instant.parse("2026-10-16T12:34:00Z").toEpochMilli()

        assertEquals("2026-10-16 12:34", formatHistoryTimestamp(millis, ZoneOffset.UTC))
    }

    @Test
    fun `applies the zone offset when formatting`() {
        val millis = Instant.parse("2026-10-16T00:30:00Z").toEpochMilli()

        assertEquals("2026-10-16 03:30", formatHistoryTimestamp(millis, ZoneOffset.ofHours(3)))
    }

    @Test
    fun `formats a duration with minutes and seconds`() {
        assertEquals("2:03", formatDuration(123_000L))
    }

    @Test
    fun `formats a duration with hours`() {
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    @Test
    fun `formats a missing or non positive duration as zero`() {
        assertEquals("0:00", formatDuration(null))
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:00", formatDuration(-5_000L))
    }
}
