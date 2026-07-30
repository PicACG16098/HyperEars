package dev.hyperears.hook

import android.app.Application
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class HookEntry : XposedModule() {
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        val hooks = when (param.packageName) {
            "com.android.bluetooth" -> listOf(BluetoothProcessHook())

            "com.milink.service" -> {
                val processName = Application.getProcessName()
                if (processName in MILINK_PROCESSES) {
                    listOf(MiLinkServiceHook())
                } else {
                    emptyList()
                }
            }

            else -> emptyList()
        }

        hooks.forEach { hook ->
            ModuleLog.module = this
            hook.module = this
            hook.appClassLoader = param.defaultClassLoader
            hook.packageName = param.packageName
            runCatching(hook::install)
                .onSuccess {
                    ModuleLog.debug(
                        "Entry",
                        "installed ${hook.javaClass.simpleName} in ${param.packageName}",
                    )
                }
                .onFailure {
                    ModuleLog.warn(
                        "Entry",
                        "failed to install ${hook.javaClass.simpleName} in ${param.packageName}",
                        it,
                    )
            }
        }
    }

    private companion object {
        val MILINK_PROCESSES = setOf(
            "com.milink.service:audio",
            "com.milink.service:core",
            "com.milink.service:ui",
        )
    }
}
