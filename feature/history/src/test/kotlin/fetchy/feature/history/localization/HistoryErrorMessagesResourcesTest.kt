package fetchy.feature.history.localization

import fetchy.model.ErrorClass
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryErrorMessagesResourcesTest {

    private val expectedSuffixes: Map<ErrorClass, String> = mapOf(
        ErrorClass.NOT_FOUND to "not_found",
        ErrorClass.NETWORK to "network",
        ErrorClass.AUTH_REQUIRED to "auth_required",
        ErrorClass.GEO_BLOCKED to "geo_blocked",
        ErrorClass.DRM_PROTECTED to "drm",
        ErrorClass.PLAYLIST_PARTIAL to "playlist_partial",
        ErrorClass.NO_SPACE to "no_space",
        ErrorClass.FFMPEG_MISSING to "ffmpeg_missing",
        ErrorClass.BINARY_MISSING to "binary_missing",
        ErrorClass.UNKNOWN to "unknown",
    )

    @Test
    fun `every error class has a label and a message in both locales`() {
        val english: Map<String, String> = strings(File(locateResourcesRoot(), "values/strings.xml"))
        val russian: Map<String, String> = strings(File(locateResourcesRoot(), "values-ru/strings.xml"))

        ErrorClass.entries.forEach { errorClass ->
            val suffix: String = requireNotNull(expectedSuffixes[errorClass]) { "no suffix for $errorClass" }
            listOf("history_error_class_$suffix", "history_error_$suffix").forEach { key ->
                assertTrue(key in english, "EN is missing $key")
                assertTrue(key in russian, "RU is missing $key")
                assertTrue(english.getValue(key).isNotBlank(), "EN has a blank $key")
                assertTrue(russian.getValue(key).isNotBlank(), "RU has a blank $key")
            }
        }
    }

    @Test
    fun `the drm message states that yt-dlp cannot download the protected video`() {
        val english: Map<String, String> = strings(File(locateResourcesRoot(), "values/strings.xml"))
        val russian: Map<String, String> = strings(File(locateResourcesRoot(), "values-ru/strings.xml"))

        assertTrue(english.getValue("history_error_drm").contains("DRM"))
        assertTrue(english.getValue("history_error_drm").contains("yt-dlp"))
        assertTrue(russian.getValue("history_error_drm").contains("DRM"))
        assertTrue(russian.getValue("history_error_drm").contains("yt-dlp"))
    }

    private fun locateResourcesRoot(): File {
        var directory: File? = File(System.getProperty("user.dir"))
        while (directory != null) {
            val candidate = File(directory, "src/main/composeResources")
            if (candidate.isDirectory) return candidate
            directory = directory.parentFile
        }
        error("src/main/composeResources not found from ${System.getProperty("user.dir")}")
    }

    private fun strings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index)
                val name = element.attributes.getNamedItem("name").nodeValue
                put(name, element.textContent)
            }
        }
    }
}
