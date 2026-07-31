package dev.hyperears.runtime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dev.hyperears.bridge.ModuleContract
import dev.hyperears.bridge.StateBroadcaster
import dev.hyperears.hook.ModuleLog
import dev.hyperears.integration.EarbudState
import java.io.Closeable

/**
 * Service-lifecycle facade installed in the Bluetooth process.
 *
 * Hook code calls only this API. The facade deliberately mirrors the native service shape:
 * create, register/unregister a device, disconnect all devices, and destroy.
 */
internal object EarbudSessionService {
    private val lock = Any()

    // This is the Bluetooth process application context, and onDestroy clears the
    // reference after synchronously unregistering the receiver and closing all sessions.
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var runtime: Runtime? = null

    fun onCreate(context: Context?) {
        if (context == null || runtime != null) return
        synchronized(lock) {
            if (runtime != null) return
            runtime = Runtime(context.applicationContext ?: context)
        }
        ModuleLog.debug(COMPONENT, "created")
    }

    fun registerDevice(device: BluetoothDevice): Boolean =
        runtime?.connectionManager?.registerDevice(device) == true

    fun unregisterDevice(device: BluetoothDevice?): Boolean =
        runtime?.connectionManager?.unregisterDevice(device) == true

    fun disconnectAllDevices() {
        runtime?.connectionManager?.unregisterDevice(null)
    }

    fun onDestroy() {
        val oldRuntime = synchronized(lock) {
            runtime.also { runtime = null }
        } ?: return
        oldRuntime.close()
        ModuleLog.debug(COMPONENT, "destroyed")
    }

    private class Runtime(
        private val context: Context,
    ) : Closeable {
        val connectionManager = EarbudConnectionManager(context)

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context == null || intent == null) return
                when (intent.action) {
                    BluetoothSystemBattery.ACTION_LEVEL_CHANGED -> {
                        val device = BluetoothSystemBattery.device(intent) ?: return
                        connectionManager.updateSystemBattery(
                            device,
                            BluetoothSystemBattery.level(intent),
                        )
                    }

                    ModuleContract.ACTION_REQUEST_STATE -> {
                        val target = with(ModuleContract) { intent.readReplyPackage() }
                            ?: return
                        val snapshots = connectionManager.snapshots()
                        if (snapshots.isEmpty()) {
                            StateBroadcaster.reply(
                                context = context,
                                targetPackage = target,
                                state = EarbudState(),
                                sessionToken = "",
                            )
                        } else {
                            snapshots.forEach { snapshot ->
                                StateBroadcaster.reply(
                                    context = context,
                                    targetPackage = target,
                                    state = snapshot.state,
                                    sessionToken = snapshot.sessionToken,
                                )
                            }
                        }
                    }

                    ModuleContract.ACTION_CONTROL -> {
                        val request = with(ModuleContract) { intent.readControl() } ?: return
                        val token = with(ModuleContract) {
                            intent.readSessionToken()
                        } ?: return
                        val address = with(ModuleContract) { intent.readAddress() } ?: return
                        if (!connectionManager.execute(request, address, token)) {
                            ModuleLog.warn(
                                COMPONENT,
                                "rejected stale, disconnected, or unauthenticated control",
                            )
                        }
                    }
                }
            }
        }

        init {
            val filter = IntentFilter().apply {
                addAction(ModuleContract.ACTION_REQUEST_STATE)
                addAction(ModuleContract.ACTION_CONTROL)
                addAction(BluetoothSystemBattery.ACTION_LEVEL_CHANGED)
            }
            context.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED,
            )
            ModuleLog.debug(COMPONENT, "control receiver registered")
        }

        override fun close() {
            connectionManager.close()
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure {
                    ModuleLog.debug(COMPONENT, "control receiver already unregistered")
                }
        }
    }

    private const val COMPONENT = "SessionService"
}
