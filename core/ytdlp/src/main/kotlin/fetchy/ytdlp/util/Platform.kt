package fetchy.ytdlp.util

enum class Platform {
    WINDOWS,
    LINUX,
    OTHER,
    ;

    companion object {
        fun current(): Platform {
            val osName: String = System.getProperty("os.name").orEmpty().lowercase()
            return when {
                osName.contains("win") -> WINDOWS
                osName.contains("linux") -> LINUX
                else -> OTHER
            }
        }
    }
}
