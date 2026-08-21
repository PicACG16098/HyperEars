package dev.hyperears.hook

import android.util.Log
import dev.hyperears.settings.ModuleSettingsRuntime
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

internal abstract class HookContext {
    lateinit var module: XposedModule
    lateinit var appClassLoader: ClassLoader
    lateinit var packageName: String

    abstract fun install()

    fun findClass(name: String): Class<*> =
        Class.forName(name, false, appClassLoader)

    fun findClassOrNull(name: String): Class<*>? =
        runCatching { findClass(name) }.getOrNull()

    fun findMethod(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method = findClass(className)
        .getDeclaredMethod(methodName, *parameterTypes)
        .apply { isAccessible = true }

    fun findMethodByParamCount(
        className: String,
        methodName: String,
        parameterCount: Int,
    ): Method = findClass(className).declaredMethods
        .first { it.name == methodName && it.parameterCount == parameterCount }
        .apply { isAccessible = true }

    fun hookBefore(method: Method, block: HookParam.() -> Unit) {
        module.hook(method).intercept { chain ->
            val parameter = HookParam(chain, null).apply(block)
            if (parameter.hasResult) parameter.result else chain.proceed()
        }
    }

    fun hookAfter(method: Method, block: HookParam.() -> Unit) {
        module.hook(method).intercept { chain ->
            val original = chain.proceed()
            HookParam(chain, original).apply(block).result
        }
    }

}

internal class HookParam(
    chain: XposedInterface.Chain,
    initialResult: Any?,
) {
    val args: List<Any?> = chain.args
    val instance: Any? = chain.thisObject

    var hasResult: Boolean = false
        private set

    var result: Any? = initialResult
        set(value) {
            hasResult = true
            field = value
        }
}

internal object ModuleLog {
    private const val ROOT_TAG = "HyperEars"

    @Volatile
    var module: XposedModule? = null

    fun debug(component: String, message: String) =
        emit(Log.DEBUG, component, message)

    fun debug(component: String, message: () -> String) {
        if (!ModuleSettingsRuntime.current.diagnosticLogging) return
        emit(Log.DEBUG, component, message())
    }

    fun warn(component: String, message: String, error: Throwable? = null) {
        if (error == null) emit(Log.WARN, component, message)
        else emit(Log.ERROR, component, message, error)
    }

    private fun emit(
        level: Int,
        component: String,
        message: String,
        error: Throwable? = null,
    ) {
        if (!ModuleSettingsRuntime.current.diagnosticLogging) return
        val activeModule = module ?: return
        val tag = "$ROOT_TAG/$component"
        runCatching {
            if (error == null) activeModule.log(level, tag, message)
            else activeModule.log(level, tag, message, error)
        }
    }
}

internal fun getObjectField(instance: Any?, fieldName: String): Any? {
    var type: Class<*>? = instance?.javaClass
    while (type != null) {
        runCatching {
            return type.getDeclaredField(fieldName)
                .apply { isAccessible = true }
                .get(instance)
        }
        type = type.superclass
    }
    throw NoSuchFieldException(fieldName)
}

internal fun callMethod(instance: Any?, methodName: String, vararg arguments: Any?): Any? {
    var type: Class<*>? = instance?.javaClass
    while (type != null) {
        type.declaredMethods
            .firstOrNull { it.name == methodName && it.parameterCount == arguments.size }
            ?.let { method ->
                method.isAccessible = true
                return method.invoke(instance, *arguments)
            }
        type = type.superclass
    }
    throw NoSuchMethodException(methodName)
}

internal fun maskBluetoothAddress(address: String?): String =
    address?.takeLast(5)?.let { "**:**:**:**:$it" } ?: "<unknown>"
