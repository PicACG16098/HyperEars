package dev.hyperears.runtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes physical Bluetooth connection establishment without limiting live sessions.
 *
 * Xiaomi's SPP layer similarly owns one connection task at a time, while each connected
 * device keeps its own socket and receive thread.
 */
internal class ConnectionAttemptCoordinator {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T =
        mutex.withLock { block() }
}
