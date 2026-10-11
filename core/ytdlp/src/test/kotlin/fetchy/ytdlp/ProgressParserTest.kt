package fetchy.ytdlp

import fetchy.model.ProgressInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressParserTest {

    @Test
    fun parsesEveryWellFormedProgressLine() {
        val wellFormed: List<String> = lines().filter { it.isWellFormed() }
        assertTrue(wellFormed.size >= 8, "expected at least 8 golden progress lines")
        wellFormed.forEach { line ->
            assertNotNull(ProgressParser.parse(line), "failed to parse: $line")
        }
    }

    @Test
    fun ignoresNonProgressAndMalformedLines() {
        val rejected: List<String> = lines().filter { it.isNotBlank() && !it.isWellFormed() }
        assertTrue(rejected.isNotEmpty())
        rejected.forEach { line ->
            assertNull(ProgressParser.parse(line), "should have rejected: $line")
        }
    }

    @Test
    fun parsesAllFieldsOfFullLine() {
        val info: ProgressInfo = requireNotNull(
            ProgressParser.parse("download:FETCHY|dQw4w9WgXcQ|NA|downloading|1048576|10485760|NA|524288.0|18"),
        )
        assertEquals("dQw4w9WgXcQ", info.id)
        assertNull(info.playlistIndex)
        assertEquals("downloading", info.status)
        assertEquals(1048576L, info.downloadedBytes)
        assertEquals(10485760L, info.totalBytes)
        assertNull(info.totalBytesEstimate)
        assertEquals(524288.0, info.speed)
        assertEquals(18L, info.etaSeconds)
        assertEquals(0.1, requireNotNull(info.fraction), 1e-9)
    }

    @Test
    fun treatsNaTokensAsMissing() {
        val info: ProgressInfo = requireNotNull(
            ProgressParser.parse("download:FETCHY|partialTrack99|NA|downloading|NA|NA|NA|NA|NA"),
        )
        assertNull(info.downloadedBytes)
        assertNull(info.totalBytes)
        assertNull(info.totalBytesEstimate)
        assertNull(info.speed)
        assertNull(info.etaSeconds)
        assertNull(info.fraction)
    }

    @Test
    fun resolvesEstimateWhenTotalMissing() {
        val info: ProgressInfo = requireNotNull(
            ProgressParser.parse("download:FETCHY|dQw4w9WgXcQ|NA|downloading|5242880|NA|10485760|612345.5|9"),
        )
        assertNull(info.totalBytes)
        assertEquals(10485760L, info.totalBytesEstimate)
        assertEquals(10485760L, info.totalBytesResolved)
    }

    @Test
    fun parsesPlaylistIndex() {
        val info: ProgressInfo = requireNotNull(
            ProgressParser.parse("download:FETCHY|playlistVid02|2|downloading|0|NA|8192|NA|NA"),
        )
        assertEquals(2, info.playlistIndex)
        assertEquals(0L, info.downloadedBytes)
    }

    @Test
    fun rejectsMissingId() {
        assertNull(ProgressParser.parse("download:FETCHY|NA|1|downloading|1|2|NA|NA|NA"))
    }

    @Test
    fun rejectsMissingStatus() {
        assertNull(ProgressParser.parse("download:FETCHY|abc|NA||1|2|NA|NA|NA"))
    }

    @Test
    fun rejectsLineWithoutPrefix() {
        assertNull(ProgressParser.parse("[download] Destination: clip.mp4"))
    }

    @Test
    fun recognisesProgressPrefix() {
        assertTrue(ProgressParser.isProgress("download:FETCHY|a|1|downloading|1|2|NA|NA|NA"))
        assertTrue(!ProgressParser.isProgress("[download] 10% of 1MiB"))
    }

    private fun String.isWellFormed(): Boolean =
        startsWith(ProgressParser.PREFIX) && split('|').size == 9

    private fun lines(): List<String> =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/progress-samples.txt")) {
            "fixture progress-samples.txt not found"
        }.bufferedReader().readLines()
}
