package fetchy.feature.history

import fetchy.feature.history.generated.resources.Res
import fetchy.feature.history.generated.resources.history_error_class_auth_required
import fetchy.feature.history.generated.resources.history_error_class_binary_missing
import fetchy.feature.history.generated.resources.history_error_class_drm
import fetchy.feature.history.generated.resources.history_error_class_ffmpeg_missing
import fetchy.feature.history.generated.resources.history_error_class_geo_blocked
import fetchy.feature.history.generated.resources.history_error_class_network
import fetchy.feature.history.generated.resources.history_error_class_no_space
import fetchy.feature.history.generated.resources.history_error_class_not_found
import fetchy.feature.history.generated.resources.history_error_class_playlist_partial
import fetchy.feature.history.generated.resources.history_error_class_unknown
import fetchy.feature.history.generated.resources.history_error_auth_required
import fetchy.feature.history.generated.resources.history_error_binary_missing
import fetchy.feature.history.generated.resources.history_error_drm
import fetchy.feature.history.generated.resources.history_error_ffmpeg_missing
import fetchy.feature.history.generated.resources.history_error_geo_blocked
import fetchy.feature.history.generated.resources.history_error_network
import fetchy.feature.history.generated.resources.history_error_no_space
import fetchy.feature.history.generated.resources.history_error_not_found
import fetchy.feature.history.generated.resources.history_error_playlist_partial
import fetchy.feature.history.generated.resources.history_error_unknown
import fetchy.model.ErrorClass
import org.jetbrains.compose.resources.StringResource

fun errorClassLabel(errorClass: ErrorClass): StringResource = when (errorClass) {
    ErrorClass.NOT_FOUND -> Res.string.history_error_class_not_found
    ErrorClass.NETWORK -> Res.string.history_error_class_network
    ErrorClass.AUTH_REQUIRED -> Res.string.history_error_class_auth_required
    ErrorClass.GEO_BLOCKED -> Res.string.history_error_class_geo_blocked
    ErrorClass.DRM_PROTECTED -> Res.string.history_error_class_drm
    ErrorClass.PLAYLIST_PARTIAL -> Res.string.history_error_class_playlist_partial
    ErrorClass.NO_SPACE -> Res.string.history_error_class_no_space
    ErrorClass.FFMPEG_MISSING -> Res.string.history_error_class_ffmpeg_missing
    ErrorClass.BINARY_MISSING -> Res.string.history_error_class_binary_missing
    ErrorClass.UNKNOWN -> Res.string.history_error_class_unknown
}

fun errorMessage(errorClass: ErrorClass): StringResource = when (errorClass) {
    ErrorClass.NOT_FOUND -> Res.string.history_error_not_found
    ErrorClass.NETWORK -> Res.string.history_error_network
    ErrorClass.AUTH_REQUIRED -> Res.string.history_error_auth_required
    ErrorClass.GEO_BLOCKED -> Res.string.history_error_geo_blocked
    ErrorClass.DRM_PROTECTED -> Res.string.history_error_drm
    ErrorClass.PLAYLIST_PARTIAL -> Res.string.history_error_playlist_partial
    ErrorClass.NO_SPACE -> Res.string.history_error_no_space
    ErrorClass.FFMPEG_MISSING -> Res.string.history_error_ffmpeg_missing
    ErrorClass.BINARY_MISSING -> Res.string.history_error_binary_missing
    ErrorClass.UNKNOWN -> Res.string.history_error_unknown
}
