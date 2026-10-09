package fetchy.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ErrorClassifierTest {

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("classifierCases")
    fun classifiesStderr(stderr: String, exitCode: Int, expected: ErrorClass) {
        assertEquals(expected, ErrorClassifier.classify(stderr, exitCode))
    }

    @Test
    fun classifierCasesCoverEveryErrorClass() {
        val covered = classifierCases().map { it.get()[2] as ErrorClass }.toList().toSet()
        assertEquals(ErrorClass.entries.toSet(), covered)
    }

    companion object {
        @JvmStatic
        fun classifierCases(): Stream<Arguments> = Stream.of(
            Arguments.of("ERROR: This video is DRM protected", 1, ErrorClass.DRM_PROTECTED),
            Arguments.of("ERROR: Unsupported URL: protected content", 1, ErrorClass.DRM_PROTECTED),
            Arguments.of("ERROR: Sign in to confirm you're not a bot", 1, ErrorClass.AUTH_REQUIRED),
            Arguments.of("ERROR: This video requires login", 1, ErrorClass.AUTH_REQUIRED),
            Arguments.of("Sign in to view this video", 1, ErrorClass.AUTH_REQUIRED),
            Arguments.of("ERROR: The uploader has not made this video available in your country", 1, ErrorClass.GEO_BLOCKED),
            Arguments.of("ERROR: This video is not available in your country", 1, ErrorClass.GEO_BLOCKED),
            Arguments.of("ERROR: [youtube] abcd: Video unavailable", 1, ErrorClass.NOT_FOUND),
            Arguments.of("ERROR: [youtube] abcd: Video is unavailable", 1, ErrorClass.NOT_FOUND),
            Arguments.of("ERROR: Unable to download webpage: HTTP Error 404: Not Found", 1, ErrorClass.NOT_FOUND),
            Arguments.of("ERROR: Postprocessing: ffmpeg not found. Please install ffmpeg", 1, ErrorClass.FFMPEG_MISSING),
            Arguments.of("ERROR: ffmpeg is not installed", 1, ErrorClass.FFMPEG_MISSING),
            Arguments.of("ERROR: No space left on device", 1, ErrorClass.NO_SPACE),
            Arguments.of("ERROR: OSError: [Errno 28] No space left on device", 1, ErrorClass.NO_SPACE),
            Arguments.of("ERROR: Unable to download webpage: <urlopen error [Errno -3]>", 1, ErrorClass.NETWORK),
            Arguments.of("ERROR: Unable to download video data: HTTP Error 503", 1, ErrorClass.NETWORK),
            Arguments.of("ERROR: Connection reset by peer", 1, ErrorClass.NETWORK),
            Arguments.of("ERROR: --download-sections is only supported for supported sites", 1, ErrorClass.PLAYLIST_PARTIAL),
            Arguments.of("ERROR: partial playlist download finished", 0, ErrorClass.PLAYLIST_PARTIAL),
            Arguments.of("ERROR: yt-dlp: command not found", 127, ErrorClass.BINARY_MISSING),
            Arguments.of("'yt-dlp' is not recognized as an internal or external command", 1, ErrorClass.BINARY_MISSING),
            Arguments.of("", 127, ErrorClass.BINARY_MISSING),
            Arguments.of("ERROR: something totally unexpected happened", 1, ErrorClass.UNKNOWN),
            Arguments.of("", 0, ErrorClass.UNKNOWN),
        )
    }
}
