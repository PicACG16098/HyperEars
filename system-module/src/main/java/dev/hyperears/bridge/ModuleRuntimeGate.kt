package dev.hyperears.bridge

/** Process-local fast path for disabling all HyperEars behavior without unloading Xposed hooks. */
object ModuleRuntimeGate {
    @Volatile
    var paused: Boolean = false
        private set

    fun update(modulePaused: Boolean) {
        paused = modulePaused
    }
}
