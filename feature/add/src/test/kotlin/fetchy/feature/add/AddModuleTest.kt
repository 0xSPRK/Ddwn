package fetchy.feature.add

import fetchy.core.platform.AppPaths
import fetchy.data.DownloadRepository
import fetchy.model.DownloadTask
import fetchy.model.TaskStatus
import fetchy.ytdlp.DownloadScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.file.Paths
import kotlinx.coroutines.test.runTest
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
                    single<DownloadScheduler> { mockk<DownloadScheduler>(relaxed = true) }
                },
                addModule,
            )
        }

        assertNotNull(application.koin.get<AddViewModel>())
        assertNotNull(application.koin.get<UrlPreviewService>())
        application.close()
    }

    @Test
    fun `download creator persists the task and submits it to the scheduler`() = runTest {
        val repository = mockk<DownloadRepository>(relaxed = true)
        val scheduler = mockk<DownloadScheduler>(relaxed = true)
        every { scheduler.submit(any()) } returns true
        val application = koinApplication {
            modules(
                module {
                    single { AppPaths(Paths.get("build/tmp/koin-add-test")) }
                    single<DownloadRepository> { repository }
                    single<DownloadScheduler> { scheduler }
                },
                addModule,
            )
        }
        val task = DownloadTask(
            id = "a",
            url = "https://example.test/a",
            title = "Title a",
            createdAt = 1L,
            status = TaskStatus.QUEUED,
        )

        application.koin.get<DownloadCreator>().create(task)

        coVerify(exactly = 1) { repository.create(task) }
        verify(exactly = 1) { scheduler.submit(task) }
        application.close()
    }
}
