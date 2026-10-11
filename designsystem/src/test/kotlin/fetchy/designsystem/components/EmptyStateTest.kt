@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class EmptyStateTest {

    @Test
    fun `empty state renders title description and action`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchyEmptyState(
                        title = "No downloads",
                        description = "Add a URL to start",
                        modifier = Modifier.testTag("empty"),
                        action = {
                            FetchyButton(
                                text = "Add",
                                onClick = {},
                                modifier = Modifier.testTag("empty-action"),
                            )
                        },
                    )
                }
            }
            onNodeWithTag("empty").assertExists()
            onNodeWithText("No downloads").assertExists()
            onNodeWithText("Add a URL to start").assertExists()
            onNodeWithTag("empty-action").assertHasClickAction()
        }
    }
}
