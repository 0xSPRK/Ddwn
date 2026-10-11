package fetchy.model

data class YtdlpError(
    val errorClass: ErrorClass,
    val message: String,
    val exitCode: Int = 0,
    val rawStderr: String = "",
)
