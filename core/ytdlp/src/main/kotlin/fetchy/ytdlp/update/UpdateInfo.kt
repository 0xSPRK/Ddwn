package fetchy.ytdlp.update

import fetchy.ytdlp.components.ComponentId

enum class UpdateStatus {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    UNKNOWN,
    OFFLINE,
}

data class UpdateInfo(
    val component: ComponentId,
    val currentVersion: String?,
    val latestVersion: String?,
    val status: UpdateStatus,
    val downloadUrl: String?,
    val assetName: String?,
    val checksumsUrl: String?,
    val etag: String?,
    val checkedAtMillis: Long,
)
