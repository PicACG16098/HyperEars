package dev.hyperears.runtime

import android.content.Context
import android.os.IBinder
import dev.hyperears.bridge.ModuleContract
import dev.hyperears.hook.ModuleLog
import dev.hyperears.integration.ControlAppCatalog
import java.io.Closeable

/**
 * Tracks hooked vendor-controller processes without polling.
 *
 * Each hooked process contributes a Binder token. A process death invalidates its token through
 * Binder's death notification, so private-channel ownership is released even when the app is
 * force-stopped or crashes. Multiple processes of one controller package are reference-counted.
 */
internal class ControlAppPresenceRegistry(
    private val context: Context,
    private val onChanged: (Set<String>) -> Unit,
) : Closeable {
    private data class Entry(
        val packageName: String,
        val processName: String,
        val token: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
    )

    private val lock = Any()
    private val entries = linkedMapOf<IBinder, Entry>()

    fun register(
        registration: ModuleContract.ControlAppRegistration,
        senderPackage: String?,
        senderUid: Int,
    ): Boolean {
        val packageName = registration.packageName
        if (ControlAppCatalog.find(packageName) == null) {
            ModuleLog.debug(COMPONENT, "ignored registration for unknown package=$packageName")
            return false
        }
        if (!isAuthenticatedSender(senderPackage, senderUid, packageName)) {
            ModuleLog.debug(
                COMPONENT,
                "rejected registration sender=$senderPackage uid=$senderUid " +
                    "package=$packageName",
            )
            return false
        }

        val token = registration.token
        val deathRecipient = IBinder.DeathRecipient {
            remove(token, "process death")
        }
        val old = synchronized(lock) {
            entries.remove(token).also {
                entries[token] = Entry(
                    packageName = packageName,
                    processName = registration.processName,
                    token = token,
                    deathRecipient = deathRecipient,
                )
            }
        }
        old?.let { unlink(it) }
        if (!runCatching { token.linkToDeath(deathRecipient, 0) }.isSuccess) {
            ModuleLog.debug(COMPONENT, "registration token already dead package=$packageName")
            remove(token, "already dead")
            return false
        }
        notifyChanged()
        ModuleLog.debug(
            COMPONENT,
            "registered ${packageName}:${registration.processName}",
        )
        return true
    }

    fun requestAnnouncements() {
        ModuleLog.debug(COMPONENT, "requesting controller presence packages=${ControlAppCatalog.packageNames}")
        ControlAppCatalog.packageNames.forEach { packageName ->
            runCatching {
                context.sendBroadcast(ModuleContract.controlAppQuery(packageName))
            }.onFailure {
                ModuleLog.debug(COMPONENT, "presence query failed for $packageName")
            }
        }
    }

    fun activePackages(): Set<String> = synchronized(lock) {
        entries.values.mapTo(linkedSetOf(), Entry::packageName)
    }

    override fun close() {
        val oldEntries = synchronized(lock) {
            entries.values.toList().also { entries.clear() }
        }
        oldEntries.forEach(::unlink)
        notifyChanged()
    }

    private fun remove(token: IBinder, reason: String) {
        val removed = synchronized(lock) { entries.remove(token) } ?: return
        unlink(removed)
        notifyChanged()
        ModuleLog.debug(
            COMPONENT,
            "unregistered ${removed.packageName}:${removed.processName} ($reason)",
        )
    }

    private fun unlink(entry: Entry) {
        runCatching { entry.token.unlinkToDeath(entry.deathRecipient, 0) }
    }

    private fun notifyChanged() {
        val active = activePackages()
        ModuleLog.debug(COMPONENT, "active controller packages=$active")
        onChanged(active)
    }

    private fun isAuthenticatedSender(
        senderPackage: String?,
        senderUid: Int,
        declaredPackage: String,
    ): Boolean {
        val packageIdentityAvailable = !senderPackage.isNullOrBlank()
        val uidIdentityAvailable = senderUid >= 0
        if (!packageIdentityAvailable && !uidIdentityAvailable) return false
        if (packageIdentityAvailable && senderPackage != declaredPackage) return false
        if (uidIdentityAvailable && !isUidOwner(senderUid, declaredPackage)) return false
        return true
    }

    private fun isUidOwner(uid: Int, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackagesForUid(uid).orEmpty().contains(packageName)
        }.getOrDefault(false)
    }

    private companion object {
        const val COMPONENT = "ControlAppPresence"
    }
}
