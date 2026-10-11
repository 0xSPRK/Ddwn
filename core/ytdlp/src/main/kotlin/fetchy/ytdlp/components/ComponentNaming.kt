package fetchy.ytdlp.components

import fetchy.ytdlp.util.Platform

object ComponentNaming {

    fun executableName(component: ComponentId, platform: Platform): String {
        val base: String =
            when (component) {
                ComponentId.YT_DLP -> "yt-dlp"
                ComponentId.FFMPEG -> "ffmpeg"
                ComponentId.FFPROBE -> "ffprobe"
                ComponentId.DENO -> "deno"
            }
        return if (platform == Platform.WINDOWS) "$base.exe" else base
    }

    fun versionArgs(component: ComponentId): List<String> =
        when (component) {
            ComponentId.YT_DLP, ComponentId.DENO -> listOf("--version")
            ComponentId.FFMPEG, ComponentId.FFPROBE -> listOf("-version")
        }
}
