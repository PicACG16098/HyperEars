package dev.hyperears.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import java.util.HashSet

/** User-controlled policy for vendor settings navigation and private-channel arbitration. */
data class ModuleSettings(
    val preferVendorControlApp: Boolean = false,
    val yieldToVendorControlApp: Boolean = false,
    val modulePaused: Boolean = false,
    val diagnosticLogging: Boolean = false,
    val disabledAdapterIds: Set<String> = emptySet(),
)

/**
 * Settings shared with injected processes through libxposed RemotePreferences.
 *
 * Writes deliberately use the platform editor and synchronous [SharedPreferences.Editor.commit]:
 * callers need the result before updating the local mirror or reporting a successful change.
 */
@SuppressLint("ApplySharedPref", "UseKtx")
object ModuleSettingsStore {
    const val PREFERENCES_GROUP = "hyper_ears_settings"

    private const val PREFER_VENDOR_CONTROL_APP = "prefer_vendor_control_app"
    private const val YIELD_TO_VENDOR_CONTROL_APP = "yield_to_vendor_control_app"
    private const val MODULE_PAUSED = "module_paused"
    private const val DIAGNOSTIC_LOGGING = "diagnostic_logging"
    private const val DISABLED_ADAPTER_IDS = "disabled_adapter_ids"
    private const val REMOTE_MIGRATION_COMPLETE = "remote_migration_complete"
    private const val REMOTE_WRITE_PENDING = "remote_write_pending"

    fun read(preferences: SharedPreferences): ModuleSettings = ModuleSettings(
        preferVendorControlApp = preferences.getBoolean(PREFER_VENDOR_CONTROL_APP, false),
        yieldToVendorControlApp = preferences.getBoolean(YIELD_TO_VENDOR_CONTROL_APP, false),
        modulePaused = preferences.getBoolean(MODULE_PAUSED, false),
        diagnosticLogging = preferences.getBoolean(DIAGNOSTIC_LOGGING, false),
        disabledAdapterIds = preferences
            .getStringSet(DISABLED_ADAPTER_IDS, emptySet())
            .orEmpty()
            .toSet(),
    )

    fun write(preferences: SharedPreferences, settings: ModuleSettings): Boolean =
        preferences.edit()
            .putBoolean(PREFER_VENDOR_CONTROL_APP, settings.preferVendorControlApp)
            .putBoolean(YIELD_TO_VENDOR_CONTROL_APP, settings.yieldToVendorControlApp)
            .putBoolean(MODULE_PAUSED, settings.modulePaused)
            .putBoolean(DIAGNOSTIC_LOGGING, settings.diagnosticLogging)
            .putStringSet(DISABLED_ADAPTER_IDS, settings.disabledAdapterIds.toRemotePreferencesSet())
            .commit()

    fun readLocal(context: Context): ModuleSettings = read(localPreferences(context))

    fun writeLocal(
        context: Context,
        settings: ModuleSettings,
        pendingRemoteWrite: Boolean,
    ): Boolean {
        val preferences = localPreferences(context)
        val settingsCommitted = write(preferences, settings)
        val metadataCommitted = preferences.edit()
            .putBoolean(REMOTE_WRITE_PENDING, pendingRemoteWrite)
            .commit()
        return settingsCommitted && metadataCommitted
    }

    /**
     * Migrates the former app-local settings once, then treats libxposed RemotePreferences as
     * the single runtime source of truth. Local preferences remain only as an offline UI mirror.
     */
    fun synchronizeRemote(context: Context, remote: SharedPreferences): ModuleSettings {
        val local = localPreferences(context)
        val migrationRequired = !local.getBoolean(REMOTE_MIGRATION_COMPLETE, false)
        val pendingWrite = local.getBoolean(REMOTE_WRITE_PENDING, false)
        val remoteConfigured = PREFERENCE_KEYS.any(remote::contains)
        val effective = if (migrationRequired || pendingWrite || !remoteConfigured) {
            read(local).also { write(remote, it) }
        } else {
            read(remote)
        }
        write(local, effective)
        local.edit()
            .putBoolean(REMOTE_MIGRATION_COMPLETE, true)
            .putBoolean(REMOTE_WRITE_PENDING, false)
            .commit()
        return effective
    }

    private fun localPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_GROUP, Context.MODE_PRIVATE)

    private val PREFERENCE_KEYS = setOf(
        PREFER_VENDOR_CONTROL_APP,
        YIELD_TO_VENDOR_CONTROL_APP,
        MODULE_PAUSED,
        DIAGNOSTIC_LOGGING,
        DISABLED_ADAPTER_IDS,
    )
}

/**
 * RemotePreferences serializes values in the module process and deserializes them in the LSPosed
 * daemon. Copy sets to a Java platform collection so an empty Kotlin singleton never crosses that
 * process boundary and requires the daemon to load Kotlin runtime classes.
 */
internal fun Set<String>.toRemotePreferencesSet(): HashSet<String> = HashSet(this)
