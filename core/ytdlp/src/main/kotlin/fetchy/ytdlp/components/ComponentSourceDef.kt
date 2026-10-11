package fetchy.ytdlp.components

import fetchy.ytdlp.util.Platform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

enum class YtDlpChannel {
    STABLE,
    NIGHTLY,
    MASTER,
}

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long?,
)

data class ParsedRelease(
    val tagName: String?,
    val assets: List<ReleaseAsset>,
)

data class ReleaseDescriptor(
    val component: ComponentId,
    val releaseApiUrl: String,
    val assetCandidates: List<String>,
    val checksumCandidates: List<String>,
)

interface ComponentSources {
    fun descriptor(component: ComponentId, channel: YtDlpChannel = YtDlpChannel.STABLE): ReleaseDescriptor
}

object GitHubReleaseParser {

    fun parse(json: String): ParsedRelease {
        val root = Json.parseToJsonElement(json).jsonObject
        val tag: String? = root["tag_name"]?.jsonPrimitive?.contentOrNull
        val assets: List<ReleaseAsset> =
            root["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                val name: String? = obj["name"]?.jsonPrimitive?.contentOrNull
                val url: String? = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull
                if (name == null || url == null) {
                    null
                } else {
                    ReleaseAsset(name, url, obj["size"]?.jsonPrimitive?.longOrNull)
                }
            }
        return ParsedRelease(tag, assets)
    }
}

class DefaultComponentSources(
    private val platform: Platform = Platform.current(),
    private val arch: String = System.getProperty("os.arch").orEmpty().lowercase(),
) : ComponentSources {

    override fun descriptor(component: ComponentId, channel: YtDlpChannel): ReleaseDescriptor =
        when (component) {
            ComponentId.YT_DLP -> ytDlp(channel)
            ComponentId.FFMPEG, ComponentId.FFPROBE -> ffmpeg()
            ComponentId.DENO -> deno()
        }

    private fun ytDlp(channel: YtDlpChannel): ReleaseDescriptor {
        val repo: String =
            when (channel) {
                YtDlpChannel.STABLE -> "yt-dlp/yt-dlp"
                YtDlpChannel.NIGHTLY -> "yt-dlp/yt-dlp-nightly-builds"
                YtDlpChannel.MASTER -> "yt-dlp/yt-dlp-master-builds"
            }
        val assets: List<String> =
            when {
                platform == Platform.WINDOWS -> listOf("yt-dlp.exe")
                isAarch64() -> listOf("yt-dlp_linux_aarch64", "yt-dlp_linux")
                else -> listOf("yt-dlp_linux")
            }
        return ReleaseDescriptor(ComponentId.YT_DLP, latest(repo), assets, listOf("SHA2-256SUMS"))
    }

    private fun ffmpeg(): ReleaseDescriptor {
        val assets: List<String> =
            when {
                platform == Platform.WINDOWS -> listOf("ffmpeg-master-latest-win64-gpl.zip")
                isAarch64() -> listOf("ffmpeg-master-latest-linuxarm64-gpl.tar.xz")
                else -> listOf("ffmpeg-master-latest-linux64-gpl.tar.xz")
            }
        return ReleaseDescriptor(ComponentId.FFMPEG, latest("yt-dlp/FFmpeg-Builds"), assets, listOf("checksums.sha256"))
    }

    private fun deno(): ReleaseDescriptor {
        val assets: List<String> =
            when {
                platform == Platform.WINDOWS -> listOf("deno-x86_64-pc-windows-msvc.zip")
                isAarch64() -> listOf("deno-aarch64-unknown-linux-gnu.zip")
                else -> listOf("deno-x86_64-unknown-linux-gnu.zip")
            }
        val checksums: List<String> = assets.map { "$it.sha256sum" }
        return ReleaseDescriptor(ComponentId.DENO, latest("denoland/deno"), assets, checksums)
    }

    private fun isAarch64(): Boolean = arch.contains("aarch64") || arch.contains("arm64")

    private fun latest(repo: String): String = "https://api.github.com/repos/$repo/releases/latest"
}
