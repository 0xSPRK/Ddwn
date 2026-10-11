@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class TextFieldsTest {

    @Test
    fun `text field is editable and shows its label`() {
        runComposeUiTest {
            setContent {
                var value by remember { mutableStateOf("") }
                FetchyTheme {
                    FetchyTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.testTag("url"),
                        label = "Address",
                    )
                }
            }
            onNodeWithTag("url").assert(hasSetTextAction())
            onNodeWithText("Address").assertExists()
        }
    }
}
