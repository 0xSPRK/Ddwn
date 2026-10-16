package fetchy.feature.history

import fetchy.model.TaskStatus

private const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

enum class HistoryStatusFilter(val status: TaskStatus?) {
    ALL(null),
    COMPLETED(TaskStatus.COMPLETED),
    FAILED(TaskStatus.FAILED),
    CANCELLED(TaskStatus.CANCELLED),
}

enum class HistoryPeriodFilter(val windowMillis: Long?) {
    ALL(null),
    LAST_DAY(DAY_MILLIS),
    LAST_WEEK(7L * DAY_MILLIS),
    LAST_MONTH(30L * DAY_MILLIS),
}
