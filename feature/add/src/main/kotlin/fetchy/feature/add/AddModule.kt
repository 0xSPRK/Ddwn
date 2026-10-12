package fetchy.feature.add

import fetchy.data.DownloadRepository
import fetchy.ytdlp.ArgvBuilder
import fetchy.ytdlp.YtdlpProcessRunner
import fetchy.ytdlp.binary.BinaryManager
import fetchy.ytdlp.util.DefaultAppPathsProvider
import org.koin.core.module.Module
import org.koin.dsl.module

val addModule: Module = module {
    single<UrlPreviewService> {
        DefaultUrlPreviewService(
            binaryManager = BinaryManager(appPaths = DefaultAppPathsProvider().resolve()),
            runner = YtdlpProcessRunner(),
            argvBuilder = ArgvBuilder(),
        )
    }

    single<DownloadCreator> {
        val repository: DownloadRepository = get()
        DownloadCreator { task -> repository.create(task) }
    }

    factory {
        AddViewModel(
            creator = get(),
            previewService = get(),
        )
    }
}
