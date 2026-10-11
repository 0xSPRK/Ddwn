package fetchy.ytdlp.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SingleFlight<K : Any, V> {

    private val mutex: Mutex = Mutex()
    private val inFlight: MutableMap<K, CompletableDeferred<V>> = mutableMapOf()

    suspend fun run(key: K, block: suspend () -> V): V {
        var owner: Boolean = false
        val deferred: CompletableDeferred<V> =
            mutex.withLock {
                val existing: CompletableDeferred<V>? = inFlight[key]
                if (existing != null) {
                    existing
                } else {
                    owner = true
                    CompletableDeferred<V>().also { inFlight[key] = it }
                }
            }
        if (!owner) return deferred.await()
        try {
            val result: V = block()
            deferred.complete(result)
            return result
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }

    suspend fun inFlightCount(): Int = mutex.withLock { inFlight.size }
}
