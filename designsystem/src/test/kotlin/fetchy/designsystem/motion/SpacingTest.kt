package fetchy.designsystem.motion

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpacingTest {

    @Test
    fun `spacing tokens are non-negative and monotonically increasing`() {
        val tokens = listOf(
            FetchySpacing.none,
            FetchySpacing.xs,
            FetchySpacing.sm,
            FetchySpacing.md,
            FetchySpacing.lg,
            FetchySpacing.xl,
            FetchySpacing.xxl,
        )

        assertEquals(0.dp, FetchySpacing.none)
        assertTrue(tokens.all { it.value >= 0f }, "spacing tokens must not be negative")
        assertTrue(tokens.zipWithNext().all { (a, b) -> a.value < b.value }, "tokens must increase")
    }
}
