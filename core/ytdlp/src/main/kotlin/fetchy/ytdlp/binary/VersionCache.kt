package fetchy.ytdlp.binary

import fetchy.ytdlp.components.ComponentId
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class VersionCacheKey(
    val path: Path,
    val mtimeMillis: Long?,
    val sizeBytes: Long?,
)

class VersionCache {

    private data class Entry(val key: VersionCacheKey, val info: BinaryInfo)

    private val entries: ConcurrentHashMap<ComponentId, Entry> = ConcurrentHashMap()

    fun lookup(component: ComponentId, key: VersionCacheKey): BinaryInfo? {
        val entry: Entry = entries[component] ?: return null
        return if (entry.key == key) entry.info else null
    }

    fun store(component: ComponentId, key: VersionCacheKey, info: BinaryInfo) {
        entries[component] = Entry(key, info)
    }

    fun invalidate(component: ComponentId) {
        entries.remove(component)
    }

    fun invalidateAll() {
        entries.clear()
    }
}
