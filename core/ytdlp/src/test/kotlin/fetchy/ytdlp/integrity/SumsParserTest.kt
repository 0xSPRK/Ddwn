package fetchy.ytdlp.integrity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SumsParserTest {

    private val hashA = "aa11bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff00112233"
    private val hashB = "112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00"

    @Test
    fun parsesHashAndNamePairs() {
        val map = SumsParser.parse("$hashA  file-a\n$hashB  file-b\n")
        assertEquals(hashA, map["file-a"])
        assertEquals(hashB, map["file-b"])
    }

    @Test
    fun findMatchesExactName() {
        val text = "$hashA  yt-dlp_linux\n"
        assertEquals(hashA, SumsParser.find(text, "yt-dlp_linux"))
    }

    @Test
    fun findMatchesByBasenameWhenPathDiffers() {
        val text = "$hashA  bin/yt-dlp_linux\n"
        assertEquals(hashA, SumsParser.find(text, "yt-dlp_linux"))
    }

    @Test
    fun handlesBinaryStarPrefix() {
        val text = "$hashA *yt-dlp.exe\n"
        assertEquals(hashA, SumsParser.find(text, "yt-dlp.exe"))
    }

    @Test
    fun normalizesUppercaseHashToLowercase() {
        val upper = hashA.uppercase()
        val text = "$upper  file\n"
        assertEquals(hashA, SumsParser.find(text, "file"))
    }

    @Test
    fun ignoresCommentsBlankAndMalformedLines() {
        val text =
            "# comment\n\nnotahash file\n$hashA file\nshort gg file\n"
        val map = SumsParser.parse(text)
        assertEquals(1, map.size)
        assertEquals(hashA, map["file"])
    }

    @Test
    fun findReturnsNullWhenAbsent() {
        assertNull(SumsParser.find("$hashA  other\n", "missing"))
    }
}