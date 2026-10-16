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
}
