package fetchy.ytdlp.components.downloader

import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

interface ComponentDownloader {
    fun download(url: String, dest: Path, opts: DownloadOpts = DownloadOpts()): Flow<DownloadProgress>
}
