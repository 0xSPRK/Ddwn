package fetchy.feature.add.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddStringResourcesParityTest {

    @Test
    fun `english and russian string keys are in parity`() {
        val resourcesRoot = locateResourcesRoot()
        val english = stringKeys(File(resourcesRoot, "values/strings.xml"))
        val russian = stringKeys(File(resourcesRoot, "values-ru/strings.xml"))

        assertTrue(english.isNotEmpty(), "default strings.xml must define at least one string")
        assertEquals(english, russian, "EN/RU string keys must be in parity")
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

    private fun stringKeys(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until nodes.length) {
                val name = nodes.item(index).attributes.getNamedItem("name")
                add(name.nodeValue)
            }
        }
    }
}
