package dev.hyperears.runtime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent

/**
 * Android Bluetooth's cached, device-wide headset battery.
 *
 * These platform members are hidden from the public SDK but are the same cache and broadcast
 * consumed by System UI. Access is confined to the injected Bluetooth process; no polling,
 * socket, or additional service is created.
 */
internal object BluetoothSystemBattery {
    const val ACTION_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

    private const val EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE"
    private const val EXTRA_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private const val UNKNOWN_LEVEL = -1

    // BluetoothDevice.getBatteryLevel() is a stable System API on the injected HyperOS target,
    // but it is intentionally absent from the public SDK used to compile the module.
    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    fun cachedLevel(device: BluetoothDevice): Int? =
        runCatching {
            device.javaClass
                .getDeclaredMethod("getBatteryLevel")
                .apply { isAccessible = true }
                .invoke(device) as? Int
        }.getOrNull().validLevel()

    fun device(intent: Intent): BluetoothDevice? =
        intent.getParcelableExtra(EXTRA_DEVICE, BluetoothDevice::class.java)

    fun level(intent: Intent): Int? =
        intent.getIntExtra(EXTRA_LEVEL, UNKNOWN_LEVEL).validLevel()

    private fun Int?.validLevel(): Int? = this?.takeIf { it in 0..100 }
}
