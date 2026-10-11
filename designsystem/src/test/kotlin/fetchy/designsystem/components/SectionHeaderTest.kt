@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class SectionHeaderTest {

    @Test
    fun `section header renders title subtitle and trailing slot`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchySectionHeader(
                        title = "Advanced",
                        subtitle = "Optional flags",
                        modifier = Modifier.testTag("header"),
                        trailing = { Text(text = "Reset", modifier = Modifier.testTag("trailing")) },
                    )
                }
            }
            onNodeWithTag("header").assertExists()
            onNodeWithText("Advanced").assertExists()
            onNodeWithText("Optional flags").assertExists()
            onNodeWithTag("trailing").assertExists()
        }
    }
}
