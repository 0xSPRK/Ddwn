@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class ChipsTest {

    @Test
    fun `filter chip reflects selected and unselected states`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchyFilterChip(
                        label = "Selected",
                        selected = true,
                        onClick = {},
                        modifier = Modifier.testTag("selected"),
                    )
                    FetchyFilterChip(
                        label = "Unselected",
                        selected = false,
                        onClick = {},
                        modifier = Modifier.testTag("unselected"),
                    )
                }
            }
            onNodeWithTag("selected").assertIsSelected()
            onNodeWithTag("unselected").assertIsNotSelected()
            onNodeWithTag("selected").assertHasClickAction()
        }
    }
}
