@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class ButtonsTest {

    @Test
    fun `renders label and reflects enabled and disabled states`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchyButton(
                        text = "Download",
                        onClick = {},
                        modifier = Modifier.testTag("primary"),
                    )
                    FetchyButton(
                        text = "Blocked",
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.testTag("disabled"),
                    )
                }
            }

            onNodeWithText("Download").assertTextEquals("Download")
            onNodeWithTag("primary").assertIsEnabled()
            onNodeWithTag("disabled").assertIsNotEnabled()
        }
    }
}
