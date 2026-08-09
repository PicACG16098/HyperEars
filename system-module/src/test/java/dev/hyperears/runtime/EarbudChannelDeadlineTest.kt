package dev.hyperears.runtime

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EarbudChannelDeadlineTest {
    @Test
    fun deadlineClosesChannelAndReleasesBlockingRead() = runBlocking {
        val channel = BlockingChannel()

        try {
            channel.withIoDeadline(timeoutMs = 50L, operation = "test read") {
                val buffer = ByteArray(1)
                check(read(buffer) >= 0)
            }
            fail("Expected EarbudChannelDeadlineException")
        } catch (_: EarbudChannelDeadlineException) {
            assertTrue(channel.closed.get())
        }
    }

    @Test
    fun completedOperationDoesNotCloseChannel() = runBlocking {
        val channel = BlockingChannel()

        channel.withIoDeadline(timeoutMs = 1_000L, operation = "test read") { Unit }

        assertFalse(channel.closed.get())
    }

    private class BlockingChannel : EarbudChannel {
        override val endpointId: String = "blocking-test"
        val closed = AtomicBoolean()
        private val closeSignal = CompletableDeferred<Unit>()

        override suspend fun connect() = Unit

        override suspend fun read(buffer: ByteArray): Int {
            closeSignal.await()
            return -1
        }

        override suspend fun write(bytes: ByteArray) = Unit

        override fun close() {
            closed.set(true)
            closeSignal.complete(Unit)
        }
    }
}
