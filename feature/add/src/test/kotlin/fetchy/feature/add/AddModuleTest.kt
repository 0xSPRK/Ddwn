package fetchy.feature.add

import fetchy.core.platform.AppPaths
import fetchy.data.DownloadRepository
import io.mockk.mockk
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AddModuleTest {

    @Test
    fun `add module resolves view model and preview service`() {
        val application = koinApplication {
            modules(
                module {
                    single { AppPaths(Paths.get("build/tmp/koin-add-test")) }
                    single<DownloadRepository> { mockk<DownloadRepository>(relaxed = true) }
                },
                addModule,
            )
        }

        assertNotNull(application.koin.get<AddViewModel>())
        assertNotNull(application.koin.get<UrlPreviewService>())
        application.close()
    }
}
