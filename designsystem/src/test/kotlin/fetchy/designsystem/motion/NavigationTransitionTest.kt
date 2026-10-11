@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.motion

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class NavigationTransitionTest {

    @Test
    fun `fade through renders the target state and swaps when it changes`() {
        runComposeUiTest {
            var route by mutableStateOf("home")
            setContent {
                FetchyTheme {
                    NavigationTransition.FadeThrough(targetState = route) { state ->
                        Text(state)
                    }
                }
            }

            onNodeWithText("home").assertIsDisplayed()

            route = "settings"
            waitForIdle()

            onNodeWithText("settings").assertIsDisplayed()
        }
    }

    @Test
    fun `animated content wrapper renders the initial state through a custom spec`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchyAnimatedContent(
                        targetState = "loading",
                        transitionSpec = { FetchyTransitions.crossfade() },
                    ) { state ->
                        Text(state)
                    }
                }
            }

            onNodeWithText("loading").assertIsDisplayed()
        }
    }
}
