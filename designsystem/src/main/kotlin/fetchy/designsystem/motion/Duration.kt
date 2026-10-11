package fetchy.designsystem.motion

/**
 * Single source of truth for animation durations in milliseconds.
 *
 * [instant] .. [extraLong] is the compact primitive ladder (the duration counterpart of
 * [FetchySpacing]). The named semantic tokens below map that ladder to concrete UI moments so
 * callers never hand-pick a raw millisecond number.
 */
object FetchyDuration {

    /** No animation; apply the state change immediately. */
    const val instant = 0

    /** Micro-interactions: ripples, hover, small fades. */
    const val short = 100

    /** Default for most state changes and secondary screen motion. */
    const val medium = 250

    /** Screen enter/exit and content that changes the whole viewport. */
    const val long = 400

    /** Large expressive/ambient motion that is not user-driven. */
    const val extraLong = 700

    /** Screen push/enter transition. */
    const val screenEnter = long

    /** Screen pop/exit transition. */
    const val screenExit = medium

    /** Dialog or overlay entrance. */
    const val dialogEnter = medium

    /** Dialog or overlay dismissal. */
    const val dialogExit = short

    /** Cross-fade between peer content. */
    const val fade = short

    /** One indeterminate progress animation cycle. */
    const val progressIndeterminate = 1200

    /** Tween applied to a determinate progress value. */
    const val progress = extraLong
}
