package fetchy.feature.queue.components

import fetchy.model.TaskStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskActionsTest {

    @Test
    fun `active and resumable statuses expose actions`() {
        assertTrue(hasTaskActions(TaskStatus.QUEUED))
        assertTrue(hasTaskActions(TaskStatus.DOWNLOADING))
        assertTrue(hasTaskActions(TaskStatus.FAILED))
        assertTrue(hasTaskActions(TaskStatus.CANCELLED))
    }

    @Test
    fun `completed status has no actions`() {
        assertFalse(hasTaskActions(TaskStatus.COMPLETED))
    }
}
