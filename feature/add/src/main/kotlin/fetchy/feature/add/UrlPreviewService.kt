package fetchy.feature.add

import fetchy.model.ErrorClass

data class UrlPreview(
    val title: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Double?,
)

sealed interface PreviewResult {
    data class Success(val preview: UrlPreview) : PreviewResult
    data class Failure(val errorClass: ErrorClass, val message: String) : PreviewResult
}

interface UrlPreviewService {
    suspend fun preview(url: String): PreviewResult
}
