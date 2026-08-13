package dev.hyperears.hook

import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves MiLink's semantic headset-settings operation when an unknown build has obfuscated
 * its controller and method names.
 *
 * This is deliberately a last-resort resolver. Callers must try stable symbols and the verified
 * version table first. The fingerprint uses exact DEX semantics shared by the verified 17.2.0
 * and 17.2.4 implementations; a missing or non-unique result is never hooked.
 */
internal object MiLinkHeadsetSettingsDexResolver {
    data class Resolution(
        val method: Method?,
        val detail: String,
    )

    fun resolve(
        apkPath: String,
        appClassLoader: ClassLoader,
        serviceInfoClass: Class<*>,
    ): Resolution = resolutions.computeIfAbsent(apkPath) {
        resolveUncached(apkPath, appClassLoader, serviceInfoClass)
    }

    private fun resolveUncached(
        apkPath: String,
        appClassLoader: ClassLoader,
        serviceInfoClass: Class<*>,
    ): Resolution = runCatching {
        loadNativeLibrary()
        val candidates = DexKitBridge.create(apkPath).use { bridge ->
            // This fallback runs once during MiLink process initialization. A single worker is
            // sufficient and avoids briefly consuming all available cores on an unknown build.
            bridge.setThreadNum(1)
            bridge.findMethod {
                searchPackages(HEADSET_PACKAGE)
                matcher {
                    returnType = CompletableFuture::class.java.name
                    paramTypes(SERVICE_INFO_CLASS)
                    usingEqStrings(CONTROLLER_LOG_TAG, OPERATION_LOG_MESSAGE)
                    addInvoke(COMPLETABLE_FUTURE_SUPPLY_ASYNC)
                }
            }.mapNotNull { candidate ->
                runCatching { candidate.getMethodInstance(appClassLoader) }.getOrNull()
            }.filter { candidate ->
                candidate.parameterTypes.contentEquals(arrayOf(serviceInfoClass)) &&
                    CompletableFuture::class.java.isAssignableFrom(candidate.returnType) &&
                    candidate.declaringClass.name.startsWith("$HEADSET_PACKAGE.")
            }.distinctBy { candidate ->
                "${candidate.declaringClass.name}#${candidate.name}"
            }
        }

        when (candidates.size) {
            1 -> Resolution(candidates.single(), "unique")
            0 -> Resolution(null, "no-match")
            else -> Resolution(null, "ambiguous:${candidates.size}")
        }
    }.getOrElse { error ->
        Resolution(null, "error:${error.javaClass.simpleName}")
    }

    private fun loadNativeLibrary() {
        nativeLibraryLoaded
    }

    private val nativeLibraryLoaded: Unit by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        System.loadLibrary(DEXKIT_LIBRARY)
    }

    private val resolutions = ConcurrentHashMap<String, Resolution>()

    private const val HEADSET_PACKAGE = "com.miui.circulate.api.protocol.headset"
    private const val SERVICE_INFO_CLASS =
        "com.miui.circulate.api.service.CirculateServiceInfo"
    private const val CONTROLLER_LOG_TAG = "HeadsetServiceController"
    private const val OPERATION_LOG_MESSAGE = "switch to headset activity"
    private const val DEXKIT_LIBRARY = "dexkit"
    private const val COMPLETABLE_FUTURE_SUPPLY_ASYNC =
        "Ljava/util/concurrent/CompletableFuture;->supplyAsync(" +
            "Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)" +
            "Ljava/util/concurrent/CompletableFuture;"
}
