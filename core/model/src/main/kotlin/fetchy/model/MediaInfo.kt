package fetchy.model

data class MediaStream(
    val index: Int,
    val type: String,
    val codecName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val durationSeconds: Double? = null,
    val language: String? = null,
)

data class MediaInfo(
    val formatName: String? = null,
    val durationSeconds: Double? = null,
    val sizeBytes: Long? = null,
    val bitRate: Long? = null,
    val title: String? = null,
    val streams: List<MediaStream> = emptyList(),
) {
    val videoStream: MediaStream?
        get() = streams.firstOrNull { it.type == "video" }

    val audioStream: MediaStream?
        get() = streams.firstOrNull { it.type == "audio" }

    val hasVideo: Boolean
        get() = videoStream != null

    val hasAudio: Boolean
        get() = audioStream != null
}
