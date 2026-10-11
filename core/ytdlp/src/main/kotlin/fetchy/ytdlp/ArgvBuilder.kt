package fetchy.ytdlp

enum class PlaylistMode {
    DEFAULT,
    NO_PLAYLIST,
    YES_PLAYLIST,
}

data class YtDlpOptions(
    val outputTemplate: String? = null,
    val outputDirectory: String? = null,
    val formatSelector: String? = null,
    val mergeOutputFormat: String? = null,
    val extractAudio: Boolean = false,
    val audioFormat: String? = null,
    val audioQuality: String? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val writeSubtitles: Boolean = false,
    val writeAutoSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
    val embedSubtitles: Boolean = false,
    val playlistMode: PlaylistMode = PlaylistMode.DEFAULT,
    val playlistItems: String? = null,
    val downloadArchive: String? = null,
    val limitRate: String? = null,
    val concurrentFragments: Int? = null,
    val retries: Int? = null,
    val fragmentRetries: Int? = null,
    val proxy: String? = null,
    val cookieFile: String? = null,
    val cookiesFromBrowser: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val geoBypassCountry: String? = null,
    val addHeaders: List<String> = emptyList(),
    val username: String? = null,
    val password: String? = null,
    val videoPassword: String? = null,
    val twoFactor: String? = null,
    val netrc: Boolean = false,
    val noCheckCertificates: Boolean = false,
    val extraOptions: List<String> = emptyList(),
)

data class YtDlpInvocation(
    val urls: List<String>,
    val options: YtDlpOptions = YtDlpOptions(),
    val userArgs: List<String> = emptyList(),
    val readUserConfig: Boolean = false,
)

data class BuiltCommand(
    val argv: List<String>,
    val maskedArgv: List<String>,
    val warnings: List<String>,
)

class ArgvBuilder {

    fun build(invocation: YtDlpInvocation): BuiltCommand {
        val warnings = ArrayList<String>()
        val options: YtDlpOptions = invocation.options
        val raw: List<String> = invocation.userArgs + options.extraOptions
        val userArgs: List<String> = sanitize(raw, warnings)

        val argv = ArrayList<String>()
        if (!invocation.readUserConfig) argv += IGNORE_CONFIG
        options.outputTemplate?.let { argv += listOf("-o", it) }
        options.outputDirectory?.let { argv += listOf("-P", it) }
        options.formatSelector?.let { argv += listOf("-f", it) }
        options.mergeOutputFormat?.let { argv += listOf("--merge-output-format", it) }
        if (options.extractAudio) argv += "-x"
        options.audioFormat?.let { argv += listOf("--audio-format", it) }
        options.audioQuality?.let { argv += listOf("--audio-quality", it) }
        if (options.embedThumbnail) argv += "--embed-thumbnail"
        if (options.embedMetadata) argv += "--embed-metadata"
        if (options.writeSubtitles) argv += "--write-subs"
        if (options.writeAutoSubtitles) argv += "--write-auto-subs"
        if (options.subtitleLanguages.isNotEmpty()) {
            argv += listOf("--sub-langs", options.subtitleLanguages.joinToString(","))
        }
        if (options.embedSubtitles) argv += "--embed-subs"
        when (options.playlistMode) {
            PlaylistMode.NO_PLAYLIST -> argv += "--no-playlist"
            PlaylistMode.YES_PLAYLIST -> argv += "--yes-playlist"
            PlaylistMode.DEFAULT -> Unit
        }
        options.playlistItems?.let { argv += listOf("--playlist-items", it) }
        options.downloadArchive?.let { argv += listOf("--download-archive", it) }
        options.limitRate?.let { argv += listOf("--limit-rate", it) }
        options.concurrentFragments?.let { argv += listOf("--concurrent-fragments", it.toString()) }
        options.retries?.let { argv += listOf("--retries", it.toString()) }
        options.fragmentRetries?.let { argv += listOf("--fragment-retries", it.toString()) }
        options.proxy?.let { argv += listOf("--proxy", it) }
        options.cookieFile?.let { argv += listOf("--cookies", it) }
        options.cookiesFromBrowser?.let { argv += listOf("--cookies-from-browser", it) }
        options.userAgent?.let { argv += listOf("--user-agent", it) }
        options.referer?.let { argv += listOf("--referer", it) }
        options.geoBypassCountry?.let { argv += listOf("--geo-bypass-country", it) }
        options.addHeaders.forEach { argv += listOf("--add-headers", it) }
        options.username?.let { argv += listOf("-u", it) }
        options.password?.let { argv += listOf("-p", it) }
        options.videoPassword?.let { argv += listOf("--video-password", it) }
        options.twoFactor?.let { argv += listOf("--twofactor", it) }
        if (options.netrc) argv += "--netrc"
        if (options.noCheckCertificates) argv += "--no-check-certificates"
        argv += NEW_LINE
        argv += listOf(PROGRESS_TEMPLATE_OPTION, PROGRESS_TEMPLATE)
        argv += userArgs
        if (invocation.urls.isNotEmpty()) {
            if (invocation.urls.any { it.startsWith("-") }) argv += URL_SEPARATOR
            argv += invocation.urls
        }

        return BuiltCommand(
            argv = argv,
            maskedArgv = CommandStringCodec.mask(argv),
            warnings = warnings,
        )
    }

    private fun sanitize(raw: List<String>, warnings: MutableList<String>): List<String> {
        val kept = ArrayList<String>(raw.size)
        var index: Int = 0
        while (index < raw.size) {
            val argument: String = raw[index]
            val name: String =
                if (argument.startsWith("--") && argument.contains('=')) argument.substringBefore('=') else argument
            if (name in GUI_OWNED) {
                warnings += "Ignored $name: it is managed by the application"
                val hasInlineValue: Boolean = argument.contains('=')
                index++
                if (!hasInlineValue && name in GUI_OWNED_WITH_VALUE && index < raw.size) index++
                continue
            }
            kept += argument
            index++
        }
        return kept
    }

    companion object {
        const val IGNORE_CONFIG: String = "--ignore-config"
        const val NEW_LINE: String = "--newline"
        const val PROGRESS_TEMPLATE_OPTION: String = "--progress-template"
        const val URL_SEPARATOR: String = "--"

        const val PROGRESS_TEMPLATE: String =
            "download:FETCHY|%(info.id)s|%(info.playlist_index)s|%(progress.status)s|" +
                "%(progress.downloaded_bytes)s|%(progress.total_bytes)s|" +
                "%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s"

        private val GUI_OWNED: Set<String> =
            setOf(
                IGNORE_CONFIG,
                NEW_LINE,
                "--no-newline",
                PROGRESS_TEMPLATE_OPTION,
                "--progress",
                "--no-progress",
                "--no-config",
            )

        private val GUI_OWNED_WITH_VALUE: Set<String> = setOf(PROGRESS_TEMPLATE_OPTION)
    }
}
