package fetchy.ytdlp

import fetchy.model.MediaInfo
import fetchy.model.MediaStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ProbeParser {

    fun parse(json: String): MediaInfo {
        val root: JsonObject = Json.parseToJsonElement(json).jsonObject
        val streams: List<MediaStream> =
            root["streams"]?.jsonArray?.map { toStream(it.jsonObject) }.orEmpty()
        val format: JsonObject? = root["format"]?.jsonObject
        val tags: JsonObject? = format?.get("tags")?.jsonObject

        return MediaInfo(
            formatName = content(format, "format_name"),
            durationSeconds = content(format, "duration")?.toDoubleOrNull(),
            sizeBytes = content(format, "size")?.toLongOrNull(),
            bitRate = content(format, "bit_rate")?.toLongOrNull(),
            title = content(tags, "title"),
            streams = streams,
        )
    }

    private fun toStream(obj: JsonObject): MediaStream = MediaStream(
        index = content(obj, "index")?.toIntOrNull() ?: 0,
        type = content(obj, "codec_type") ?: "unknown",
        codecName = content(obj, "codec_name"),
        width = content(obj, "width")?.toIntOrNull(),
        height = content(obj, "height")?.toIntOrNull(),
        frameRate = parseFraction(content(obj, "avg_frame_rate")),
        sampleRate = content(obj, "sample_rate")?.toIntOrNull(),
        channels = content(obj, "channels")?.toIntOrNull(),
        durationSeconds = content(obj, "duration")?.toDoubleOrNull(),
        language = content(obj["tags"]?.jsonObject, "language"),
    )

    private fun content(obj: JsonObject?, key: String): String? =
        obj?.get(key)?.jsonPrimitive?.content

    private fun parseFraction(value: String?): Double? {
        if (value == null) return null
        if ('/' !in value) return value.toDoubleOrNull()
        val (numerator, denominator) = value.split('/', limit = 2)
        val n: Double = numerator.toDoubleOrNull() ?: return null
        val d: Double = denominator.toDoubleOrNull() ?: return null
        if (d == 0.0) return null
        return n / d
    }
}
