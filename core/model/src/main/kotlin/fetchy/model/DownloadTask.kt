package fetchy.model

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String? = null,
    val formatArgsJson: String = "[]",
    val presetId: String? = null,
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Double = 0.0,
    val createdAt: Long,
    val finishedAt: Long? = null,
    val errorClass: ErrorClass? = null,
    val logPath: String? = null,
)
