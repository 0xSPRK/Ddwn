package fetchy.ytdlp.components

import fetchy.ytdlp.binary.DefaultProcessLauncher
import fetchy.ytdlp.binary.ProcessLauncher
import fetchy.ytdlp.components.downloader.ComponentDownloader
import fetchy.ytdlp.components.downloader.HttpFetcher
import fetchy.ytdlp.integrity.IntegrityResult
import fetchy.ytdlp.integrity.Sha256
import fetchy.ytdlp.integrity.SumsParser
import fetchy.ytdlp.installer.ArchiveExtractor
import fetchy.ytdlp.installer.AtomicInstallResult
import fetchy.ytdlp.installer.AtomicInstaller
import fetchy.ytdlp.util.AppPaths
import fetchy.ytdlp.util.FilesExt
import fetchy.ytdlp.util.Platform
import fetchy.ytdlp.util.SingleFlight
import kotlinx.coroutines.flow.collect
import java.nio.file.Files
import java.nio.file.Path

sealed interface InstallOutcome {
    val component: ComponentId

    data class Installed(
        override val component: ComponentId,
        val version: String?,
        val integrity: IntegrityResult,
        val target: Path,
    ) : InstallOutcome

    data class BlockedByActiveTasks(override val component: ComponentId) : InstallOutcome

    data class Failed(
        override val component: ComponentId,
        val error: ComponentError,
    ) : InstallOutcome
}

class ComponentsManager(
    private val appPaths: AppPaths,
    private val downloader: ComponentDownloader,
    private val httpFetcher: HttpFetcher,
    private val platform: Platform = Platform.current(),
    private val extractor: ArchiveExtractor = ArchiveExtractor(),
    private val installer: AtomicInstaller = AtomicInstaller(platform),
    private val sources: ComponentSources = DefaultComponentSources(platform),
    private val processLauncher: ProcessLauncher = DefaultProcessLauncher(),
    private val activeTasks: () -> Boolean = { false },
    private val postCheckTimeoutMillis: Long = POST_CHECK_TIMEOUT_MILLIS,
) {

    private val singleFlight: SingleFlight<ComponentId, InstallOutcome> = SingleFlight()

    val componentsDir: Path
        get() = appPaths.componentsDir

    suspend fun install(
        component: ComponentId,
        channel: YtDlpChannel = YtDlpChannel.STABLE,
    ): InstallOutcome = singleFlight.run(component) { performInstall(component, channel) }

    private suspend fun performInstall(component: ComponentId, channel: YtDlpChannel): InstallOutcome {
        if (activeTasks()) return InstallOutcome.BlockedByActiveTasks(component)
        val descriptor: ReleaseDescriptor = sources.descriptor(component, channel)
        FilesExt.ensureDirectory(appPaths.componentsDir)
        FilesExt.ensureDirectory(appPaths.cacheDir)
        val work: Path = Files.createTempDirectory(appPaths.cacheDir, "install-")
        return try {
            installInto(component, descriptor, work)
        } finally {
            FilesExt.deleteRecursively(work)
        }
    }

    private suspend fun installInto(
        component: ComponentId,
        descriptor: ReleaseDescriptor,
        work: Path,
    ): InstallOutcome {
        val releaseResponse =
            try {
                httpFetcher.get(descriptor.releaseApiUrl)
            } catch (e: ComponentError.Offline) {
                return InstallOutcome.Failed(component, e)
            }
        if (releaseResponse.status == 404) {
            return InstallOutcome.Failed(
                component,
                ComponentError.NoAsset("Release not found: ${descriptor.releaseApiUrl}"),
            )
        }
        if (releaseResponse.status != 200) {
            return InstallOutcome.Failed(
                component,
                ComponentError.HttpError(releaseResponse.status, descriptor.releaseApiUrl),
            )
        }

        val release: ParsedRelease = GitHubReleaseParser.parse(releaseResponse.body)
        val asset: ReleaseAsset =
            release.assets.firstOrNull { it.name in descriptor.assetCandidates }
                ?: return InstallOutcome.Failed(
                    component,
                    ComponentError.NoAsset("No matching asset for $component among ${descriptor.assetCandidates}"),
                )
        val checksumAsset: ReleaseAsset? =
            release.assets.firstOrNull { it.name in descriptor.checksumCandidates }

        val downloaded: Path = work.resolve(asset.name)
        try {
            downloader.download(asset.downloadUrl, downloaded).collect { }
        } catch (e: ComponentError) {
            return InstallOutcome.Failed(component, e)
        }

        val actual: String = Sha256.of(downloaded)
        val integrity: IntegrityResult =
            if (checksumAsset != null) {
                val expected: String? = fetchExpectedHash(checksumAsset, asset.name)
                when {
                    expected == null -> IntegrityResult.NotVerified("No checksum entry for ${asset.name}")
                    expected != actual ->
                        return InstallOutcome.Failed(
                            component,
                            ComponentError.ChecksumMismatch(expected, actual),
                        )

                    else -> IntegrityResult.Verified(actual)
                }
            } else {
                IntegrityResult.NotVerified("Source provides no checksum file for $component")
            }

        val staged: Path =
            if (isArchive(asset.name)) {
                val extractDir: Path = work.resolve("extract")
                try {
                    extractor.extract(downloaded, extractDir)
                } catch (e: ComponentError) {
                    return InstallOutcome.Failed(component, e)
                }
                findBinary(extractDir, component)
                    ?: return InstallOutcome.Failed(
                        component,
                        ComponentError.NoAsset("Binary not found in archive for $component"),
                    )
            } else {
                downloaded
            }

        val target: Path =
            appPaths.componentsDir.resolve(ComponentNaming.executableName(component, platform))
        return when (val result = installer.install(staged, target) { verify(target, component) }) {
            is AtomicInstallResult.Installed ->
                InstallOutcome.Installed(component, release.tagName, integrity, target)

            is AtomicInstallResult.RolledBack ->
                InstallOutcome.Failed(component, ComponentError.PostCheckFailed(result.reason))

            is AtomicInstallResult.Failed -> InstallOutcome.Failed(component, result.error)
        }
    }

    private suspend fun fetchExpectedHash(checksumAsset: ReleaseAsset, assetName: String): String? {
        val response =
            try {
                httpFetcher.get(checksumAsset.downloadUrl)
            } catch (_: ComponentError.Offline) {
                return null
            }
        if (response.status != 200) return null
        return SumsParser.find(response.body, assetName)
    }

    private suspend fun verify(target: Path, component: ComponentId): Boolean =
        try {
            val output =
                processLauncher.run(
                    listOf(target.toString()) + ComponentNaming.versionArgs(component),
                    postCheckTimeoutMillis,
                )
            output.exitCode == 0
        } catch (_: Exception) {
            false
        }

    private fun isArchive(name: String): Boolean {
        val lower: String = name.lowercase()
        return lower.endsWith(".zip") || lower.endsWith(".tar.xz") || lower.endsWith(".txz")
    }

    private fun findBinary(dir: Path, component: ComponentId): Path? {
        val wanted: Set<String> =
            setOf(
                ComponentNaming.executableName(component, platform),
                ComponentNaming.executableName(component, Platform.OTHER),
            )
        return Files.walk(dir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString() in wanted }
                .findFirst()
                .orElse(null)
        }
    }

    companion object {
        const val POST_CHECK_TIMEOUT_MILLIS: Long = 5_000
    }
}
