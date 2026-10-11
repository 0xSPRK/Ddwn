@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class CardsTest {

    @Test
    fun `clickable card exposes click action and static card stays passive`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchyCard(onClick = {}, modifier = Modifier.testTag("clickable")) {
                        FetchySectionHeader(title = "Clickable")
                    }
                    FetchyCard(modifier = Modifier.testTag("static")) {
                        FetchySectionHeader(title = "Static")
                    }
                }
            }
            onNodeWithTag("clickable").assertHasClickAction()
            onNodeWithTag("static").assertExists()
            onNodeWithTag("static").assertHasNoClickAction()
        }
    }
}
