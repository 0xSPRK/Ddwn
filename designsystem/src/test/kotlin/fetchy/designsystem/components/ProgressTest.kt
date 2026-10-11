@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package fetchy.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import fetchy.designsystem.theme.FetchyTheme
import org.junit.jupiter.api.Test

class ProgressTest {

    @Test
    fun `determinate linear progress exposes range info`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchyLinearProgress(progress = 0.5f, modifier = Modifier.testTag("bar"))
                }
            }
            onNodeWithTag("bar").assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
        }
    }

    @Test
    fun `indeterminate progress renders`() {
        runComposeUiTest {
            setContent {
                FetchyTheme {
                    FetchyCircularProgress(modifier = Modifier.testTag("spinner"))
                }
            }
            onNodeWithTag("spinner").assertExists()
        }
    }
}
