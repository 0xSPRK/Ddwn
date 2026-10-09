package fetchy.designsystem.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

/**
 * Ready-made [ContentTransform] specs assembled from the [FetchyDuration] / [FetchyEasing]
 * tokens.
 *
 * The specs are deliberately feature-agnostic: they animate between any two states of any type.
 * The app navigation layer (T3.2) can therefore drive them with its own `Destination` values
 * without the design system taking a dependency on them.
 */
object FetchyTransitions {

    private const val SlideFraction = 4

    /** Simple opacity cross-fade; use for subtle, peer content swaps. */
    fun crossfade(): ContentTransform =
        fadeIn(tween(FetchyDuration.fade, easing = FetchyEasing.standard)) togetherWith
            fadeOut(tween(FetchyDuration.fade, easing = FetchyEasing.standard))

    /**
     * Material "fade through": the outgoing content fades out before the incoming content fades
     * in. The default for top-level destination switches.
     */
    fun fadeThrough(): ContentTransform =
        fadeIn(tween(FetchyDuration.screenEnter, easing = FetchyEasing.emphasizedDecelerate)) togetherWith
            fadeOut(tween(FetchyDuration.screenExit, easing = FetchyEasing.emphasizedAccelerate))

    /** Horizontal push motion for hierarchical navigation (enter from the end, exit toward the start). */
    fun slideHorizontal(): ContentTransform =
        enterFromEnd() togetherWith exitToStart()

    /** Vertical push motion for sheets and overlays (enter from the bottom, exit downward). */
    fun slideVertical(): ContentTransform =
        enterFromBottom() togetherWith exitToBottom()

    private fun enterFromEnd(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(FetchyDuration.screenEnter, easing = FetchyEasing.emphasizedDecelerate),
            initialOffsetX = { width -> width / SlideFraction },
        ) + fadeIn(tween(FetchyDuration.screenEnter, easing = FetchyEasing.emphasizedDecelerate))

    private fun exitToStart(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(FetchyDuration.screenExit, easing = FetchyEasing.emphasizedAccelerate),
            targetOffsetX = { width -> -width / SlideFraction },
        ) + fadeOut(tween(FetchyDuration.screenExit, easing = FetchyEasing.emphasizedAccelerate))

    private fun enterFromBottom(): EnterTransition =
        slideInVertically(
            animationSpec = tween(FetchyDuration.screenEnter, easing = FetchyEasing.emphasizedDecelerate),
            initialOffsetY = { height -> height / SlideFraction },
        ) + fadeIn(tween(FetchyDuration.screenEnter, easing = FetchyEasing.emphasizedDecelerate))

    private fun exitToBottom(): ExitTransition =
        slideOutVertically(
            animationSpec = tween(FetchyDuration.screenExit, easing = FetchyEasing.emphasizedAccelerate),
            targetOffsetY = { height -> height / SlideFraction },
        ) + fadeOut(tween(FetchyDuration.screenExit, easing = FetchyEasing.emphasizedAccelerate))
}

/**
 * Thin wrapper over [AnimatedContent] that applies a Fetchy [ContentTransform] and keeps the
 * token choices in one place. Key on any state type `T`; the default spec is [FetchyTransitions.fadeThrough].
 */
@Composable
fun <T> FetchyAnimatedContent(
    targetState: T,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<T>.() -> ContentTransform = {
        FetchyTransitions.fadeThrough()
    },
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (T) -> Any? = { it },
    label: String = "FetchyAnimatedContent",
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = transitionSpec,
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        label = label,
    ) { state ->
        content(state)
    }
}

/**
 * Destination-agnostic navigation transitions.
 *
 * Each helper accepts a generic state `T` and renders `content` for the current value, so the
 * navigation layer can pass its `Destination` (or any route key) without a design-system
 * dependency on that type. Distinct `Detail(id)`-style routes are handled through the default
 * [contentKey] (`{ it }`), which keys content by the state's own equality.
 */
object NavigationTransition {

    /** Default cross-destination transition; see [FetchyTransitions.fadeThrough]. */
    @Composable
    fun <T> FadeThrough(
        targetState: T,
        modifier: Modifier = Modifier,
        contentKey: (T) -> Any? = { it },
        content: @Composable (T) -> Unit,
    ) {
        FetchyAnimatedContent(
            targetState = targetState,
            modifier = modifier,
            transitionSpec = { FetchyTransitions.fadeThrough() },
            contentKey = contentKey,
            label = "FetchyFadeThrough",
            content = content,
        )
    }

    /** Subtle opacity cross-fade; see [FetchyTransitions.crossfade]. */
    @Composable
    fun <T> CrossfadeWithFade(
        targetState: T,
        modifier: Modifier = Modifier,
        contentKey: (T) -> Any? = { it },
        content: @Composable (T) -> Unit,
    ) {
        FetchyAnimatedContent(
            targetState = targetState,
            modifier = modifier,
            transitionSpec = { FetchyTransitions.crossfade() },
            contentKey = contentKey,
            label = "FetchyCrossfadeWithFade",
            content = content,
        )
    }

    /** Horizontal push/pop; see [FetchyTransitions.slideHorizontal]. */
    @Composable
    fun <T> SlideInHorizontal(
        targetState: T,
        modifier: Modifier = Modifier,
        contentKey: (T) -> Any? = { it },
        content: @Composable (T) -> Unit,
    ) {
        FetchyAnimatedContent(
            targetState = targetState,
            modifier = modifier,
            transitionSpec = { FetchyTransitions.slideHorizontal() },
            contentKey = contentKey,
            label = "FetchySlideInHorizontal",
            content = content,
        )
    }

    /** Vertical push/pop for sheets and overlays; see [FetchyTransitions.slideVertical]. */
    @Composable
    fun <T> SlideInVertical(
        targetState: T,
        modifier: Modifier = Modifier,
        contentKey: (T) -> Any? = { it },
        content: @Composable (T) -> Unit,
    ) {
        FetchyAnimatedContent(
            targetState = targetState,
            modifier = modifier,
            transitionSpec = { FetchyTransitions.slideVertical() },
            contentKey = contentKey,
            label = "FetchySlideInVertical",
            content = content,
        )
    }
}
