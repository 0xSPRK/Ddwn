package fetchy.feature.queue

import fetchy.model.TaskStatus

data class QueueTaskUiState(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val status: TaskStatus,
    val progress: Double,
    val speedBytesPerSecond: Double?,
    val etaSeconds: Long?,
    val playlistIndex: Int?,
)
