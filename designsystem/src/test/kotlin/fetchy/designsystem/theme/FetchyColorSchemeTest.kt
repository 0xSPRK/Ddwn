package fetchy.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class FetchyColorSchemeTest {

    @Test
    fun `brand seed color is stable`() {
        assertEquals(Color(0xFF00696E), FetchySeedColor)
    }

    @Test
    fun `light and dark schemes are generated from the expressive seed`() {
        val light = fetchyColorScheme(isDark = false)
        val dark = fetchyColorScheme(isDark = true)

        assertEquals(1f, light.primary.alpha)
        assertEquals(1f, dark.primary.alpha)
        assertNotEquals(light.primary, dark.primary)
        assertNotEquals(light.surface, dark.surface)
        assertNotEquals(light.onSurface, dark.onSurface)
    }

    @Test
    fun `schemes are opaque`() {
        val scheme = fetchyColorScheme(isDark = false)

        assertEquals(1f, scheme.background.alpha)
        assertEquals(1f, scheme.surfaceContainer.alpha)
        assertEquals(1f, scheme.onPrimary.alpha)
    }
}
