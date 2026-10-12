package fetchy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationTest {

    @Test
    fun startsAtInitialDestination() {
        assertEquals(Destination.Add, FetchyNavController().current)
        assertEquals(Destination.History, FetchyNavController(Destination.History).current)
    }

    @Test
    fun navigatePushesAndUpdatesCurrent() {
        val nav = FetchyNavController()
        nav.navigate(Destination.Queue)
        nav.navigate(Destination.Settings)

        assertEquals(Destination.Settings, nav.current)
        assertEquals(listOf(Destination.Add, Destination.Queue, Destination.Settings), nav.backStack)
        assertEquals(3, nav.size)
    }

    @Test
    fun popReturnsFalseAtRoot() {
        val nav = FetchyNavController()

        assertFalse(nav.pop())
        assertEquals(Destination.Add, nav.current)
    }

    @Test
    fun popReturnsToPreviousDestination() {
        val nav = FetchyNavController()
        nav.navigate(Destination.Queue)

        assertTrue(nav.pop())
        assertEquals(Destination.Add, nav.current)
        assertFalse(nav.canPop)
    }

    @Test
    fun replaceReplacesCurrentTop() {
        val nav = FetchyNavController()
        nav.navigate(Destination.Queue)
        nav.replace(Destination.History)

        assertEquals(listOf(Destination.Add, Destination.History), nav.backStack)
        assertEquals(2, nav.size)
    }

    @Test
    fun popToPopsUntilDestination() {
        val nav = FetchyNavController()
        nav.navigate(Destination.Queue)
        nav.navigate(Destination.Detail("x"))

        assertTrue(nav.popTo(Destination.Queue))
        assertEquals(Destination.Queue, nav.current)
        assertEquals(listOf(Destination.Add, Destination.Queue), nav.backStack)
    }

    @Test
    fun popToMissingDestinationReturnsFalse() {
        val nav = FetchyNavController()
        nav.navigate(Destination.Queue)

        assertFalse(nav.popTo(Destination.Settings))
        assertEquals(Destination.Queue, nav.current)
    }

    @Test
    fun topLevelClearsStackToSingleDestination() {
        val nav = FetchyNavController()
        nav.navigate(Destination.Queue)
        nav.navigate(Destination.Detail("x"))
        nav.topLevel(Destination.Gallery)

        assertEquals(listOf(Destination.Gallery), nav.backStack)
        assertEquals(Destination.Gallery, nav.current)
        assertFalse(nav.canPop)
    }
}