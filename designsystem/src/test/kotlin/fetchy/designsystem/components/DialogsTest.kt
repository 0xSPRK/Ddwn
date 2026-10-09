@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.components

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class DialogsTest {

    @Test
    fun `alert dialog renders title text and confirm action`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchyAlertDialog(
                        title = "Delete?",
                        text = "This cannot be undone",
                        confirmLabel = "Delete",
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
            onNodeWithText("Delete?").assertExists()
            onNodeWithText("This cannot be undone").assertExists()
            onNodeWithText("Delete").assertExists()
        }
    }
}
