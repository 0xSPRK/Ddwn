package fetchy.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

val FetchySeedColor: Color = Color(0xFF00696E)

fun fetchyColorScheme(
    isDark: Boolean,
    seedColor: Color = FetchySeedColor,
): ColorScheme = dynamicColorScheme(
    seedColor = seedColor,
    isDark = isDark,
    style = PaletteStyle.Expressive,
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
)
