package fetchy.feature.queue

import fetchy.data.RepositoryTaskStateStore
import fetchy.ytdlp.DefaultYtDlpCommandBuilder
import fetchy.ytdlp.DownloadScheduler
import fetchy.ytdlp.YtdlpProcessRunner
import fetchy.ytdlp.binary.BinaryManager
import fetchy.ytdlp.util.DefaultAppPathsProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

val queueModule: Module = module {
    single {
        DownloadScheduler(
            runner = YtdlpProcessRunner(),
            commandBuilder =
                DefaultYtDlpCommandBuilder(
                    binaryManager = BinaryManager(DefaultAppPathsProvider().resolve()),
                ),
            stateStore = RepositoryTaskStateStore(get()),
        ).also { it.start() }
    } onClose {
        it?.close()
    }

    single { QueueViewModel(get(), get()) } onClose {
        it?.close()
    }
}
