package fetchy.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {

    private val repository = SettingsRepository(FetchyDatabaseFactory.inMemory())

    @Test
    fun getMissingReturnsNull() = runTest {
        assertNull(repository.get("missing"))
    }

    @Test
    fun putThenGetReturnsValue() = runTest {
        repository.put("theme", "dark")

        assertEquals("dark", repository.get("theme"))
    }

    @Test
    fun putOverwritesExistingKey() = runTest {
        repository.put("theme", "dark")
        repository.put("theme", "light")

        assertEquals("light", repository.get("theme"))
    }

    @Test
    fun observeAllEmitsSortedMap() = runTest {
        repository.put("b", "2")
        repository.put("a", "1")

        val snapshot = repository.observeAll().first()

        assertEquals(listOf("a", "b"), snapshot.keys.toList())
        assertEquals("1", snapshot["a"])
        assertEquals("2", snapshot["b"])
    }

    @Test
    fun removeDeletesKey() = runTest {
        repository.put("theme", "dark")

        repository.remove("theme")

        assertNull(repository.get("theme"))
    }
}
