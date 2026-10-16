package fetchy.feature.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HISTORY_TIMESTAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

fun formatHistoryTimestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    HISTORY_TIMESTAMP.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatDuration(millis: Long?): String {
    if (millis == null || millis <= 0L) return "0:00"
    val totalSeconds: Long = millis / 1000L
    val hours: Long = totalSeconds / 3600L
    val minutes: Long = (totalSeconds % 3600L) / 60L
    val seconds: Long = totalSeconds % 60L
    val secondsLabel: String = seconds.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:$secondsLabel"
    } else {
        "$minutes:$secondsLabel"
    }
}
