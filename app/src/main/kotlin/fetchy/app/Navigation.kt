package fetchy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import fetchy.designsystem.motion.NavigationTransition

sealed interface Destination {
    data object Add : Destination
    data object Queue : Destination
    data object History : Destination
    data object Presets : Destination
    data object Settings : Destination
    data object Tools : Destination
    data object Gallery : Destination
    data class Detail(val id: String) : Destination
}

class FetchyNavController(initial: Destination = Destination.Add) {

    private val stack = mutableStateListOf(initial)

    val current: Destination
        get() = stack.last()

    val canPop: Boolean
        get() = stack.size > 1

    val size: Int
        get() = stack.size

    val backStack: List<Destination>
        get() = stack.toList()

    fun navigate(destination: Destination) {
        stack.add(destination)
    }

    fun replace(destination: Destination) {
        if (stack.isEmpty()) {
            stack.add(destination)
        } else {
            stack[stack.lastIndex] = destination
        }
    }

    fun pop(): Boolean {
        if (!canPop) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popTo(destination: Destination): Boolean {
        val index = stack.indexOfLast { it == destination }
        if (index < 0) return false
        while (stack.size - 1 > index) {
            stack.removeAt(stack.lastIndex)
        }
        return true
    }

    fun topLevel(destination: Destination) {
        while (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
        stack[0] = destination
    }
}

@Composable
fun FetchyNavHost(
    controller: FetchyNavController,
    modifier: Modifier = Modifier,
    content: @Composable (Destination) -> Unit,
) {
    NavigationTransition.FadeThrough(
        targetState = controller.current,
        modifier = modifier,
        content = content,
    )
}
