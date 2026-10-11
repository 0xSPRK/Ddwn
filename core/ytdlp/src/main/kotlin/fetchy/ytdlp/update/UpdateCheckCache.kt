package fetchy.ytdlp.update

import fetchy.ytdlp.components.ComponentError
import fetchy.ytdlp.components.ComponentId
import fetchy.ytdlp.components.GitHubReleaseParser
import fetchy.ytdlp.components.ParsedRelease
import fetchy.ytdlp.components.downloader.HttpFetcher
import fetchy.ytdlp.util.FilesExt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class UpdateCheckCache(
    private val cacheFile: Path,
    private val fetcher: HttpFetcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val manualRateLimitMillis: Long = DEFAULT_MANUAL_RATE_LIMIT_MILLIS,
) {

    private val entries: MutableMap<ComponentId, UpdateInfo> = load()
    private val lastManualCheck: MutableMap<ComponentId, Long> = mutableMapOf()

    fun cached(component: ComponentId): UpdateInfo? = entries[component]

    suspend fun check(
        component: ComponentId,
        releaseApiUrl: String,
        assetCandidates: List<String>,
        currentVersion: String? = null,
        manual: Boolean = false,
    ): UpdateInfo {
        val now: Long = clock()
        val cached: UpdateInfo? = entries[component]
        if (cached != null) {
            if (!manual && now - cached.checkedAtMillis < ttlMillis) return cached
            if (manual) {
                val lastManual: Long? = lastManualCheck[component]
                if (lastManual != null && now - lastManual < manualRateLimitMillis) return cached
            }
        }
        if (manual) lastManualCheck[component] = now

        val headers: Map<String, String> = cached?.etag?.let { mapOf("If-None-Match" to it) } ?: emptyMap()
        val response =
            try {
                fetcher.get(releaseApiUrl, headers)
            } catch (_: ComponentError.Offline) {
                return offline(component, currentVersion, cached, now)
            } catch (_: IOException) {
                return offline(component, currentVersion, cached, now)
            }

        return when (response.status) {
            304 -> {
                val info: UpdateInfo =
                    cached?.copy(checkedAtMillis = now)
                        ?: UpdateInfo(
                            component,
                            currentVersion,
                            null,
                            UpdateStatus.UNKNOWN,
                            null,
                            null,
                            null,
                            null,
                            now,
                        )
                store(component, info)
                info
            }

            200 -> {
                val release: ParsedRelease = GitHubReleaseParser.parse(response.body)
                val asset =
                    release.assets.firstOrNull { it.name in assetCandidates }
                val checksum =
                    release.assets.firstOrNull {
                        it.name.endsWith(".sha256") || it.name.endsWith(".sha256sum") ||
                            it.name.contains("SUMS", ignoreCase = true)
                    }
                val status: UpdateStatus =
                    when {
                        release.tagName == null -> UpdateStatus.UNKNOWN
                        currentVersion == null -> UpdateStatus.UPDATE_AVAILABLE
                        release.tagName == currentVersion -> UpdateStatus.UP_TO_DATE
                        else -> UpdateStatus.UPDATE_AVAILABLE
                    }
                val info: UpdateInfo =
                    UpdateInfo(
                        component = component,
                        currentVersion = currentVersion,
                        latestVersion = release.tagName,
                        status = status,
                        downloadUrl = asset?.downloadUrl,
                        assetName = asset?.name,
                        checksumsUrl = checksum?.downloadUrl,
                        etag = response.etag,
                        checkedAtMillis = now,
                    )
                store(component, info)
                info
            }

            else -> cached ?: UpdateInfo(
                component,
                currentVersion,
                null,
                UpdateStatus.UNKNOWN,
                null,
                null,
                null,
                null,
                now,
            )
        }
    }

    private fun offline(
        component: ComponentId,
        currentVersion: String?,
        cached: UpdateInfo?,
        now: Long,
    ): UpdateInfo =
        UpdateInfo(
            component = component,
            currentVersion = currentVersion,
            latestVersion = cached?.latestVersion,
            status = UpdateStatus.OFFLINE,
            downloadUrl = cached?.downloadUrl,
            assetName = cached?.assetName,
            checksumsUrl = cached?.checksumsUrl,
            etag = cached?.etag,
            checkedAtMillis = now,
        )

    private fun store(component: ComponentId, info: UpdateInfo) {
        entries[component] = info
        persist()
    }

    private fun persist() {
        try {
            val root =
                buildJsonObject {
                    entries.forEach { (id: ComponentId, info: UpdateInfo) ->
                        put(
                            id.name,
                            buildJsonObject {
                                put("currentVersion", info.currentVersion?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("latestVersion", info.latestVersion?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("status", JsonPrimitive(info.status.name))
                                put("downloadUrl", info.downloadUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("assetName", info.assetName?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("checksumsUrl", info.checksumsUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("etag", info.etag?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("checkedAtMillis", JsonPrimitive(info.checkedAtMillis))
                            },
                        )
                    }
                }
            FilesExt.ensureParent(cacheFile)
            Files.writeString(cacheFile, root.toString())
        } catch (_: Exception) {
            // cache persistence is best-effort
        }
    }

    private fun load(): MutableMap<ComponentId, UpdateInfo> {
        if (!Files.exists(cacheFile)) return mutableMapOf()
        return try {
            val root = Json.parseToJsonElement(Files.readString(cacheFile)).jsonObject
            val map: MutableMap<ComponentId, UpdateInfo> = mutableMapOf()
            root.forEach { (key: String, value) ->
                val id: ComponentId = ComponentId.entries.firstOrNull { it.name == key } ?: return@forEach
                val obj = value.jsonObject
                map[id] =
                    UpdateInfo(
                        component = id,
                        currentVersion = obj["currentVersion"]?.jsonPrimitive?.contentOrNull,
                        latestVersion = obj["latestVersion"]?.jsonPrimitive?.contentOrNull,
                        status = obj["status"]?.jsonPrimitive?.contentOrNull?.let { UpdateStatus.valueOf(it) }
                            ?: UpdateStatus.UNKNOWN,
                        downloadUrl = obj["downloadUrl"]?.jsonPrimitive?.contentOrNull,
                        assetName = obj["assetName"]?.jsonPrimitive?.contentOrNull,
                        checksumsUrl = obj["checksumsUrl"]?.jsonPrimitive?.contentOrNull,
                        etag = obj["etag"]?.jsonPrimitive?.contentOrNull,
                        checkedAtMillis = obj["checkedAtMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
                    )
            }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 6 * 60 * 60 * 1000
        const val DEFAULT_MANUAL_RATE_LIMIT_MILLIS: Long = 60_000
    }
}
