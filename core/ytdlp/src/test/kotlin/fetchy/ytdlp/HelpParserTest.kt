package fetchy.ytdlp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class HelpParserTest {

    private lateinit var registry: HelpRegistry

    @BeforeEach
    fun setUp() {
        registry = HelpParser.parse(fixture(), VERSION)
    }

    @Test
    fun parsesAtLeast250Options() {
        assertTrue(registry.size >= 250, "parsed only ${registry.size} options")
        assertTrue(registry.size <= 400, "parsed suspiciously many options: ${registry.size}")
    }

    @Test
    fun findsOutputOption() {
        val option = registry.find("--output")
        requireNotNull(option)
        assertEquals("-o", option.short)
        assertEquals("--output", option.long)
        assertEquals("Filesystem Options", option.section)
        assertTrue(option.takesValue)
        assertTrue(option.metavar.orEmpty().contains("TEMPLATE"))
    }

    @Test
    fun resolvesShortFlags() {
        assertEquals("--paths", registry.find("-P")?.long)
        assertEquals("--password", registry.find("-p")?.long)
    }

    @Test
    fun findsNoProgress() {
        requireNotNull(registry.find("--no-progress"))
    }

    @Test
    fun findsNetworkSection() {
        assertFalse(registry.inSection("Network Options").isEmpty())
        assertEquals("Network Options", registry.find("--proxy")?.section)
    }

    @Test
    fun joinsWrappedDescriptions() {
        val option = registry.find("--cookies-from-browser")
        requireNotNull(option)
        assertTrue(option.description.contains("browser to load cookies"))
    }

    @Test
    fun expandsNegatableOptions() {
        val text = "Options:\n    --[no-]write-subs   Toggle subtitle writing\n"
        val parsed = HelpParser.parse(text, "synthetic")
        requireNotNull(parsed.find("--write-subs"))
        requireNotNull(parsed.find("--no-write-subs"))
        assertTrue(parsed.find("--write-subs")?.negatable == true)
    }

    @Test
    fun roundTripsJson() {
        assertEquals(registry, HelpParser.fromJson(HelpParser.toJson(registry)))
    }

    @Test
    fun cachesOnDiskByVersion() {
        val directory: Path = Files.createTempDirectory("help-cache")
        val cache = HelpRegistryCache(directory)
        cache.save(registry)
        assertEquals(registry, cache.load(VERSION))
        assertEquals(null, cache.load("0.0.0"))
    }

    @Test
    fun toleratesUnknownFormat() {
        assertEquals(0, HelpParser.parse("complete nonsense\nno options here\n", "x").size)
    }

    private fun fixture(): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/ytdlp-help.txt")) {
            "fixture ytdlp-help.txt not found"
        }.bufferedReader().use { it.readText() }

    companion object {
        private const val VERSION: String = "2026.08.19"
    }
}
