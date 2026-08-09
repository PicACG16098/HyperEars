package dev.hyperears.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateRequestDispatcherTest {
    @Test
    fun newerRequestReplacesThePendingRequestForTheSameFeature() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dispatcher = StateRequestDispatcher(scope)
        val executions = AtomicInteger()
        val completed = CountDownLatch(1)

        try {
            dispatcher.request(
                featureId = "standard.noise_mode",
                delayMs = 60_000L,
                task = { executions.addAndGet(100) },
                onFailure = { throw AssertionError(it) },
            )
            dispatcher.request(
                featureId = "standard.noise_mode",
                delayMs = 0L,
                task = {
                    executions.incrementAndGet()
                    completed.countDown()
                },
                onFailure = { throw AssertionError(it) },
            )

            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(1, executions.get())
        } finally {
            dispatcher.close()
            scope.cancel()
        }
    }

    @Test
    fun cancellingOneFeatureLeavesOtherFeatureRequestsActive() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dispatcher = StateRequestDispatcher(scope)
        val batteryExecutions = AtomicInteger()
        val modeCompleted = CountDownLatch(1)

        try {
            dispatcher.request(
                featureId = "standard.battery",
                delayMs = 60_000L,
                task = { batteryExecutions.incrementAndGet() },
                onFailure = { throw AssertionError(it) },
            )
            dispatcher.request(
                featureId = "standard.noise_mode",
                delayMs = 0L,
                task = { modeCompleted.countDown() },
                onFailure = { throw AssertionError(it) },
            )
            dispatcher.cancel("standard.battery", "report accepted")

            assertTrue(modeCompleted.await(2, TimeUnit.SECONDS))
            assertEquals(0, batteryExecutions.get())
        } finally {
            dispatcher.close()
            scope.cancel()
        }
    }
}
