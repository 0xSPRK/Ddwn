package fetchy.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/**
 * Single source of truth for animation easing curves.
 *
 * Values mirror the Material 3 motion easing tokens
 * ([md.sys.motion.easing.*](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs));
 * their cubic Bezier control points are the same ones used internally by `material3`.
 *
 * Note: the baseline [emphasized] curve is identical to [standard] in the Material 3 token set —
 * the expressive character comes from the accelerate/decelerate variants and the longer
 * [FetchyDuration] values they are paired with.
 */
object FetchyEasing {

    /** Constant rate; for continuous/looping motion where easing adds nothing. */
    val linear: Easing = LinearEasing

    /** Default curve for elements that begin and end at rest. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Elements that start at rest and end at peak velocity — used for exits. */
    val standardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Elements that start at peak velocity and end at rest — used for entrances. */
    val standardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Expressive baseline; pair with [FetchyDuration.extraLong] for hero moments. */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Expressive exit; elements leaving the screen. */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Expressive entrance; elements arriving on screen. */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}
