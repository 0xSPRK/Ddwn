package fetchy.feature.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HISTORY_TIMESTAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

fun formatHistoryTimestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    HISTORY_TIMESTAMP.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
