package fetchy.designsystem.motion

import androidx.compose.animation.core.Easing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EasingsTest {

    private val easings: Map<String, Easing> = mapOf(
        "linear" to FetchyEasing.linear,
        "standard" to FetchyEasing.standard,
        "standardAccelerate" to FetchyEasing.standardAccelerate,
        "standardDecelerate" to FetchyEasing.standardDecelerate,
        "emphasized" to FetchyEasing.emphasized,
        "emphasizedAccelerate" to FetchyEasing.emphasizedAccelerate,
        "emphasizedDecelerate" to FetchyEasing.emphasizedDecelerate,
    )

    @Test
    fun `every easing maps the endpoints to 0 and 1`() {
        easings.forEach { (name, easing) ->
            assertEquals(0f, easing.transform(0f), 1e-4f, "$name must start at 0")
            assertEquals(1f, easing.transform(1f), 1e-4f, "$name must end at 1")
        }
    }

    @Test
    fun `easing output is finite and monotonic across the unit range`() {
        val tolerance = 1e-4f
        easings.forEach { (name, easing) ->
            val samples = (0..10).map { step -> easing.transform(step / 10f) }
            assertTrue(samples.all { it.isFinite() }, "$name produced a non-finite value")
            assertTrue(
                samples.zipWithNext().all { (a, b) -> b >= a - tolerance },
                "$name must be monotonic over the sampled range",
            )
        }
    }
}
