package dev.hyperears.settings

import android.content.SharedPreferences
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-local view of the module's RemotePreferences.
 *
 * Each injected process reads exactly the same persisted source and receives settings changes
 * directly from libxposed. No target process needs to wake the companion application.
 */
internal object ModuleSettingsRuntime {
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<(ModuleSettings) -> Unit>()

    @Volatile
    private var settings = ModuleSettings()

    @Volatile
    private var preferences: SharedPreferences? = null

    @Volatile
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    val current: ModuleSettings
        get() = settings

    fun bind(remotePreferences: SharedPreferences) {
        synchronized(lock) {
            if (preferences === remotePreferences) return
            preferenceListener?.let { previous ->
                preferences?.unregisterOnSharedPreferenceChangeListener(previous)
            }
            preferences = remotePreferences
            preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                publish(ModuleSettingsStore.read(remotePreferences))
            }.also(remotePreferences::registerOnSharedPreferenceChangeListener)
        }
        publish(ModuleSettingsStore.read(remotePreferences))
    }

    fun observe(listener: (ModuleSettings) -> Unit): Closeable {
        listeners += listener
        listener(current)
        return Closeable { listeners -= listener }
    }

    private fun publish(updated: ModuleSettings) {
        settings = updated
        listeners.forEach { listener -> runCatching { listener(updated) } }
    }
}
