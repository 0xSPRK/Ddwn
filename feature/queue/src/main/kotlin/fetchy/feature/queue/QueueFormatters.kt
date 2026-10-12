package fetchy.feature.queue

import java.util.Locale

fun formatSpeed(bytesPerSecond: Double): String {
    val value: Double = if (bytesPerSecond.isFinite() && bytesPerSecond > 0.0) bytesPerSecond else 0.0
    return when {
        value >= GIGABYTE -> String.format(Locale.ROOT, "%.1f GB", value / GIGABYTE)
        value >= MEGABYTE -> String.format(Locale.ROOT, "%.1f MB", value / MEGABYTE)
        value >= KILOBYTE -> String.format(Locale.ROOT, "%.0f KB", value / KILOBYTE)
        else -> String.format(Locale.ROOT, "%.0f B", value)
    }
}

fun formatEta(seconds: Long): String {
    val safe: Long = if (seconds > 0L) seconds else 0L
    val hours: Long = safe / 3600L
    val minutes: Long = (safe % 3600L) / 60L
    val secs: Long = safe % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}

private const val KILOBYTE: Double = 1024.0
private const val MEGABYTE: Double = KILOBYTE * 1024.0
private const val GIGABYTE: Double = MEGABYTE * 1024.0
