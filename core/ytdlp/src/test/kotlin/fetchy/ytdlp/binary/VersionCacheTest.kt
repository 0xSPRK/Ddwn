package fetchy.ytdlp.binary

import fetchy.ytdlp.components.ComponentId
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.file.Path

class VersionCacheTest {

    private val key: VersionCacheKey = VersionCacheKey(Path.of("/usr/bin/yt-dlp"), 123L, 456L)
    private val info: BinaryInfo =
        BinaryInfo(
            path = key.path,
            version = "2026.08.19",
            source = BinarySource.PATH,
            status = BinaryStatus.OK,
            mtimeMillis = key.mtimeMillis,
            sizeBytes = key.sizeBytes,
            checkedAtMillis = 1L,
        )

    @Test
    fun storeAndLookupReturnInfoForKey() {
        val cache = VersionCache()
        cache.store(ComponentId.YT_DLP, key, info)
        assertSame(info, cache.lookup(ComponentId.YT_DLP, key))
    }

    @Test
    fun lookupReturnsNullWhenNoEntryForComponent() {
        val cache = VersionCache()
        cache.store(ComponentId.YT_DLP, key, info)
        assertNull(cache.lookup(ComponentId.FFMPEG, key))
    }

    @Test
    fun lookupReturnsNullWhenFileChanged() {
        val cache = VersionCache()
        cache.store(ComponentId.YT_DLP, key, info)
        val changed = key.copy(sizeBytes = 999L)
        assertNull(cache.lookup(ComponentId.YT_DLP, changed))
    }

    @Test
    fun invalidateRemovesEntry() {
        val cache = VersionCache()
        cache.store(ComponentId.YT_DLP, key, info)
        cache.invalidate(ComponentId.YT_DLP)
        assertNull(cache.lookup(ComponentId.YT_DLP, key))
    }

    @Test
    fun invalidateAllClearsEverything() {
        val cache = VersionCache()
        cache.store(ComponentId.YT_DLP, key, info)
        cache.store(ComponentId.FFMPEG, key, info)
        cache.invalidateAll()
        assertNull(cache.lookup(ComponentId.YT_DLP, key))
        assertNull(cache.lookup(ComponentId.FFMPEG, key))
    }
}