package dev.hyperears.hook

import android.content.Context
import dev.hyperears.bridge.ModuleRuntimeGate
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture

/**
 * Installs the semantic MiLink "more settings" bridge for the host version in use.
 *
 * MiLink 17.2.4 exposes a stable controller name. The known 17.2.0 build keeps the same
 * operation behind an obfuscated controller, so that one entry is isolated here and enabled
 * only for its verified package version and method signature. Unknown builds are left untouched.
 */
internal class MiLinkHeadsetSettingsNavigationBridge(
    private val contextProvider: () -> Context?,
    private val isHyperEarsCard: (serviceInfo: Any, address: String) -> Boolean,
    private val serviceInfoAddress: (serviceInfo: Any) -> String?,
    private val openPreferredSettings: (address: String) -> Boolean,
) : HookContext() {

    override fun install() {
        val serviceInfoClass = findClassOrNull(SERVICE_INFO_CLASS)
        if (serviceInfoClass == null) {
            ModuleLog.debug(COMPONENT, "CirculateServiceInfo unavailable")
            return
        }

        val packageVersion = packageVersionCode()
        val stableMethod = findControllerMethod(
            className = STABLE_CONTROLLER_CLASS,
            methodName = STABLE_METHOD,
            serviceInfoClass = serviceInfoClass,
        )
        val bridge = when {
            stableMethod != null -> NavigationMethod(STABLE_BRIDGE, stableMethod)
            packageVersion == LEGACY_VERSION_CODE -> findControllerMethod(
                className = LEGACY_CONTROLLER_CLASS,
                methodName = LEGACY_METHOD,
                serviceInfoClass = serviceInfoClass,
            )?.let { NavigationMethod(LEGACY_BRIDGE, it) }

            else -> null
        }

        if (bridge == null) {
            ModuleLog.debug(
                COMPONENT,
                "settings navigation bridge unavailable versionCode=$packageVersion",
            )
            return
        }

        hookBefore(bridge.method) {
            if (ModuleRuntimeGate.paused) return@hookBefore
            val serviceInfo = args.singleOrNull() ?: return@hookBefore
            val address = serviceInfoAddress(serviceInfo) ?: return@hookBefore
            if (!isHyperEarsCard(serviceInfo, address) || !openPreferredSettings(address)) {
                return@hookBefore
            }
            result = CompletableFuture.completedFuture(HEADSET_OPERATION_SUCCESS)
            ModuleLog.debug(
                COMPONENT,
                "opened headset settings bridge=${bridge.name} " +
                    "versionCode=$packageVersion address=${maskBluetoothAddress(address)}",
            )
        }
        ModuleLog.debug(
            COMPONENT,
            "settings navigation installed bridge=${bridge.name} versionCode=$packageVersion",
        )
    }

    private fun packageVersionCode(): Long? = runCatching {
        contextProvider()
            ?.packageManager
            ?.getPackageInfo(packageName, 0)
            ?.longVersionCode
    }.getOrNull()

    private fun findControllerMethod(
        className: String,
        methodName: String,
        serviceInfoClass: Class<*>,
    ): Method? = findClassOrNull(className)
        ?.declaredMethods
        ?.singleOrNull { candidate ->
            candidate.name == methodName &&
                candidate.parameterTypes.contentEquals(arrayOf(serviceInfoClass)) &&
                CompletableFuture::class.java.isAssignableFrom(candidate.returnType)
        }
        ?.apply { isAccessible = true }

    private data class NavigationMethod(
        val name: String,
        val method: Method,
    )

    private companion object {
        const val COMPONENT = "MiLink"
        const val SERVICE_INFO_CLASS = "com.miui.circulate.api.service.CirculateServiceInfo"
        const val STABLE_CONTROLLER_CLASS =
            "com.miui.circulate.api.protocol.headset.HeadsetServiceController"
        const val STABLE_METHOD = "switchToHeadsetActivity"
        const val LEGACY_CONTROLLER_CLASS = "com.miui.circulate.api.protocol.headset.b0"
        const val LEGACY_METHOD = "e0"
        const val LEGACY_BRIDGE = "legacy-b0.e0"
        const val STABLE_BRIDGE = "stable-controller"
        const val LEGACY_VERSION_CODE = 170020001L
        const val HEADSET_OPERATION_SUCCESS = 100
    }
}
