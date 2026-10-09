package fetchy.model

enum class ErrorClass {
    NOT_FOUND,
    NETWORK,
    AUTH_REQUIRED,
    GEO_BLOCKED,
    DRM_PROTECTED,
    PLAYLIST_PARTIAL,
    NO_SPACE,
    FFMPEG_MISSING,
    BINARY_MISSING,
    UNKNOWN,
}
