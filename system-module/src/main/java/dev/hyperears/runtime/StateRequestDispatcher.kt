package dev.hyperears.runtime

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Executes Adapter-requested, one-shot state reads without owning model policy.
 *
 * A feature has at most one pending request per physical device session. Scheduling another
 * request for the same feature replaces the previous timer; accepting a report or ending the
 * transport cancels it explicitly. Device targets, retry limits and report acceptance remain in
 * the Adapter that produced the request.
 */
internal class StateRequestDispatcher(
    private val scope: CoroutineScope,
) : Closeable {
    private val lock = Any()
    private val jobs = mutableMapOf<String, Job>()

    fun request(
        featureId: String,
        delayMs: Long,
        task: suspend () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        require(featureId.isNotBlank())
        require(delayMs >= 0L)

        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMs)
            task()
        }
        job.invokeOnCompletion { error ->
            synchronized(lock) {
                if (jobs[featureId] === job) jobs.remove(featureId)
            }
            if (error != null && error !is CancellationException) onFailure(error)
        }
        val previous = synchronized(lock) {
            jobs.put(featureId, job)
        }
        previous?.cancel(CancellationException("state request replaced"))
        job.start()
    }

    fun cancel(featureId: String, reason: String) {
        val job = synchronized(lock) { jobs.remove(featureId) }
        job?.cancel(CancellationException(reason))
    }

    fun cancelAll(reason: String) {
        val pending = synchronized(lock) {
            jobs.values.toList().also { jobs.clear() }
        }
        pending.forEach { job -> job.cancel(CancellationException(reason)) }
    }

    override fun close() {
        cancelAll("state request dispatcher closed")
    }
}
