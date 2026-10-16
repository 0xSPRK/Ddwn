package fetchy.model

enum class TaskStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean
        get() = when (this) {
            QUEUED, DOWNLOADING -> false
            COMPLETED, FAILED, CANCELLED -> true
        }
}
