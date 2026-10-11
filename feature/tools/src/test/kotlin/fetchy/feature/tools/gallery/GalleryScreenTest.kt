@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.feature.tools.gallery

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class GalleryScreenTest {

    @Test
    fun `renders the component gallery headless`() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                FetchyTheme {
                    GalleryScreen()
                }
            }
            mainClock.advanceTimeBy(16)

            onNodeWithText("Fetchy design system").assertExists()
            onNodeWithText("Primary").assertExists()
            onNodeWithText("Show dialog").assertExists()
            onNodeWithText("Nothing here yet").assertExists()
        }
    }

    @Test
    fun `opens the dialog from the gallery`() {
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                FetchyTheme {
                    GalleryScreen()
                }
            }
            mainClock.advanceTimeBy(16)

            onNodeWithText("Show dialog").performSemanticsAction(SemanticsActions.OnClick)
            mainClock.advanceTimeBy(16)

            onNodeWithText("Remove download").assertExists()
            onNodeWithText("Cancel").assertExists()
        }
    }
}
