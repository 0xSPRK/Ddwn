package fetchy.feature.add

import fetchy.core.platform.AppPaths as PlatformAppPaths
import fetchy.data.DownloadRepository
import fetchy.ytdlp.ArgvBuilder
import fetchy.ytdlp.YtdlpProcessRunner
import fetchy.ytdlp.binary.BinaryManager
import fetchy.ytdlp.util.AppPaths
import org.koin.core.module.Module
import org.koin.dsl.module

val addModule: Module = module {
    single<UrlPreviewService> {
        val platformPaths: PlatformAppPaths = get()
        val ytdlpPaths: AppPaths = AppPaths(
            appDir = platformPaths.root,
            componentsDir = platformPaths.root.resolve(COMPONENTS_DIR),
            cacheDir = platformPaths.cacheDirectory,
        )
        DefaultUrlPreviewService(
            binaryManager = BinaryManager(appPaths = ytdlpPaths),
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

private const val COMPONENTS_DIR: String = "components"
