package fetchy.feature.history

import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

val historyModule: Module = module {
    single { HistoryViewModel(get()) } onClose {
        it?.close()
    }
}
