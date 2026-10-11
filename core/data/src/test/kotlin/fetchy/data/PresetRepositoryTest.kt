package fetchy.data

import fetchy.model.Preset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PresetRepositoryTest {

    private val repository = PresetRepository(FetchyDatabaseFactory.inMemory())

    @Test
    fun createThenGetRoundTrips() = runTest {
        val preset = preset("p1", name = "Best", argsJson = """["-f","best"]""")

        repository.create(preset)

        assertEquals(preset, repository.get("p1"))
    }

    @Test
    fun observeAllOrdersByName() = runTest {
        repository.create(preset("b", name = "Zeta", createdAt = 1L))
        repository.create(preset("a", name = "Alpha", createdAt = 2L))

        val names = repository.observeAll().first().map { it.name }

        assertEquals(listOf("Alpha", "Zeta"), names)
    }

    @Test
    fun updateChangesNameAndArgs() = runTest {
        repository.create(preset("p1", name = "Old", argsJson = "[]"))

        repository.update(preset("p1", name = "New", argsJson = """["-x"]"""))

        val updated = requireNotNull(repository.get("p1"))
        assertEquals("New", updated.name)
        assertEquals("""["-x"]""", updated.argsJson)
    }

    @Test
    fun deleteRemovesPreset() = runTest {
        repository.create(preset("p1", name = "Best"))

        repository.delete("p1")

        assertNull(repository.get("p1"))
    }

    private fun preset(
        id: String,
        name: String = "Preset $id",
        argsJson: String = "[]",
        createdAt: Long = 0L,
    ) = Preset(id = id, name = name, argsJson = argsJson, createdAt = createdAt)
}
