package fetchy.designsystem.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DurationTest {

    @Test
    fun `primitive durations are non-negative and strictly increasing`() {
        val scale = listOf(
            FetchyDuration.instant,
            FetchyDuration.short,
            FetchyDuration.medium,
            FetchyDuration.long,
            FetchyDuration.extraLong,
        )

        assertEquals(0, FetchyDuration.instant)
        assertTrue(scale.all { it >= 0 }, "durations must not be negative")
        assertTrue(scale.zipWithNext().all { (a, b) -> a < b }, "primitive durations must increase")
    }

    @Test
    fun `semantic durations are non-negative and alias the primitive scale`() {
        val semantic = listOf(
            FetchyDuration.screenEnter,
            FetchyDuration.screenExit,
            FetchyDuration.dialogEnter,
            FetchyDuration.dialogExit,
            FetchyDuration.fade,
            FetchyDuration.progress,
            FetchyDuration.progressIndeterminate,
        )

        assertTrue(semantic.all { it >= 0 }, "semantic durations must not be negative")
        assertEquals(FetchyDuration.long, FetchyDuration.screenEnter)
        assertEquals(FetchyDuration.medium, FetchyDuration.screenExit)
        assertEquals(FetchyDuration.medium, FetchyDuration.dialogEnter)
        assertEquals(FetchyDuration.short, FetchyDuration.dialogExit)
        assertEquals(FetchyDuration.short, FetchyDuration.fade)
        assertTrue(
            FetchyDuration.progressIndeterminate > FetchyDuration.extraLong,
            "an indeterminate progress loop outlasts the largest primitive duration",
        )
    }
}
