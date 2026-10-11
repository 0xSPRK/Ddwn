package fetchy.designsystem.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Cubic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MorphTest {

    @Test
    fun `graphics-shapes builds a star to circle morph on the JVM target`() {
        val morph = fetchyDemoMorph()

        val bounds = morph.calculateBounds()

        assertTrue(bounds[2] - bounds[0] > 0f, "morph bounds width should be positive")
        assertTrue(bounds[3] - bounds[1] > 0f, "morph bounds height should be positive")
    }

    @Test
    fun `morph interpolates between distinct start and end curves`() {
        val morph = fetchyDemoMorph()

        val start = morph.asCubics(0f)
        val end = morph.asCubics(1f)

        assertEquals(start.size, end.size, "morph must keep a one-to-one cubic count")
        assertNotEquals(
            start.first().points(),
            end.first().points(),
            "start and end shapes must differ",
        )
    }

    @Test
    fun `morph shape produces a generic outline for progress across the range`() {
        val morph = fetchyDemoMorph()

        listOf(0f, 0.5f, 1f).forEach { progress ->
            val outline = MorphShape(morph, progress)
                .createOutline(Size(100f, 100f), LayoutDirection.Ltr, Density(1f))

            assertTrue(outline is Outline.Generic, "outline at $progress should be generic")
            val bounds = (outline as Outline.Generic).path.getBounds()
            assertTrue(bounds.width > 0f && bounds.height > 0f, "outline at $progress should be non-empty")
            assertTrue(bounds.width <= 100f && bounds.height <= 100f, "outline at $progress should fit its size")
        }
    }

    private fun Cubic.points(): List<Float> = listOf(
        anchor0X,
        anchor0Y,
        control0X,
        control0Y,
        control1X,
        control1Y,
        anchor1X,
        anchor1Y,
    )
}
