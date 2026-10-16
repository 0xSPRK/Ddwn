package fetchy.feature.history

import fetchy.core.platform.DefaultFolderOpener
import fetchy.core.platform.FolderOpener
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

val historyModule: Module = module {
    single<FolderOpener> { DefaultFolderOpener() }
    single<LogFileReader> { DefaultLogFileReader() }
    single { HistoryViewModel(get()) } onClose {
        it?.close()
    }
    factory { parameters ->
        HistoryDetailViewModel(
            repository = get(),
            opener = get(),
            logReader = get(),
            id = parameters.get(),
            shell = detectShell(),
        )
    } onClose {
        it?.close()
    }
}
