package dev.hyperears

import android.app.Application
import android.content.SharedPreferences
import dev.hyperears.integration.EarbudAdapterRegistry
import dev.hyperears.settings.ModuleSettingsStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Owns the companion application's connection to the official libxposed configuration service. */
class HyperEarsApplication : Application(), XposedServiceHelper.OnServiceListener {
    interface PreferencesListener {
        fun onPreferencesChanged(preferences: SharedPreferences?)
    }

    private val listeners = CopyOnWriteArraySet<PreferencesListener>()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var service: XposedService? = null

    @Volatile
    private var remotePreferences: SharedPreferences? = null

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        applicationScope.launch {
            EarbudAdapterRegistry.preloadCatalog()
        }
    }

    fun addPreferencesListener(listener: PreferencesListener) {
        listeners += listener
        listener.onPreferencesChanged(remotePreferences)
    }

    fun removePreferencesListener(listener: PreferencesListener) {
        listeners -= listener
    }

    fun currentRemotePreferences(): SharedPreferences? = remotePreferences

    override fun onServiceBind(boundService: XposedService) {
        service = boundService
        val preferences = runCatching {
            boundService.getRemotePreferences(ModuleSettingsStore.PREFERENCES_GROUP)
        }.getOrNull()
        if (preferences != null) {
            ModuleSettingsStore.synchronizeRemote(this, preferences)
        }
        remotePreferences = preferences
        notifyListeners(preferences)
    }

    override fun onServiceDied(deadService: XposedService) {
        if (service == deadService) {
            service = null
            remotePreferences = null
            notifyListeners(null)
        }
    }

    private fun notifyListeners(preferences: SharedPreferences?) {
        listeners.forEach { it.onPreferencesChanged(preferences) }
    }
}
