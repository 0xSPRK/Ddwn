package fetchy.designsystem.shapes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star

/**
 * T1.3 spike result: `androidx.graphics:graphics-shapes:1.1.0` publishes a JVM/desktop target
 * (`graphics-shapes-desktop-1.1.0.jar`), so the library is used directly. No custom
 * `Shape`-interpolation fallback is needed.
 *
 * This file is the bridge between the library's cubic output and Compose. The library has no
 * ready-made Compose `Shape`, so [MorphShape] replays the morphed cubics into a [Path].
 */
internal class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val bounds = morph.calculateBounds()
        val width = (bounds[2] - bounds[0]).takeIf { it > 0f } ?: 1f
        val height = (bounds[3] - bounds[1]).takeIf { it > 0f } ?: 1f
        val scaleX = size.width / width
        val scaleY = size.height / height

        val path = Path().apply {
            morph.forEachCubic(progress.coerceIn(0f, 1f)) { cubic ->
                moveTo(
                    (cubic.anchor0X - bounds[0]) * scaleX,
                    (cubic.anchor0Y - bounds[1]) * scaleY,
                )
                cubicTo(
                    (cubic.control0X - bounds[0]) * scaleX,
                    (cubic.control0Y - bounds[1]) * scaleY,
                    (cubic.control1X - bounds[0]) * scaleX,
                    (cubic.control1Y - bounds[1]) * scaleY,
                    (cubic.anchor1X - bounds[0]) * scaleX,
                    (cubic.anchor1Y - bounds[1]) * scaleY,
                )
            }
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Returns the demo morph shape (a rounded 6-point star morphing into a circle) at [progress]
 * (`0f` = star, `1f` = circle). The underlying [Morph] is memoized across recompositions.
 */
@Composable
fun rememberFetchyMorphShape(progress: Float): Shape {
    val morph = remember { fetchyDemoMorph() }
    return remember(morph, progress) { MorphShape(morph, progress) }
}

internal fun fetchyDemoMorph(): Morph = Morph(
    start = RoundedPolygon.star(
        numVerticesPerRadius = 6,
        radius = 1f,
        innerRadius = 0.5f,
        rounding = CornerRounding(radius = 0.12f),
    ),
    end = RoundedPolygon.circle(numVertices = 8, radius = 1f),
)
