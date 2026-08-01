package dev.hyperears.protocoltest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.IOException
import java.util.UUID

internal sealed interface StarRingGattEvent {
    data class Services(val summary: String) : StarRingGattEvent
    data class Incoming(val bytes: ByteArray) : StarRingGattEvent
    data class Disconnected(val reason: String) : StarRingGattEvent
}

internal data class StarRingGattEndpoint(
    val writeUuid: UUID,
    val notifyUuid: UUID,
) {
    val label: String = "BLE GATT · write=$writeUuid · notify=$notifyUuid"
}

/**
 * Direct StarRing transport matching the current Lightyear app capture.
 *
 * The official app writes business frames to ATT value handle 0xA102 and receives notifications
 * from value handle 0xA105. Instance IDs are used only to identify the captured characteristics;
 * UUID/property fallback keeps the probe useful when Android rebuilds the cached handle table.
 */
@SuppressLint("MissingPermission")
internal class StarRingGattProbeClient(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val adapter =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private val mutableEvents = MutableSharedFlow<StarRingGattEvent>(extraBufferCapacity = 64)
    val events = mutableEvents.asSharedFlow()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var ready: CompletableDeferred<StarRingGattEndpoint>? = null

    @Volatile
    private var pendingWrite: CompletableDeferred<Unit>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (this@StarRingGattProbeClient.gatt !== gatt) return
            when {
                status != BluetoothGatt.GATT_SUCCESS ->
                    failConnection("GATT 连接状态错误：$status")

                newState == BluetoothProfile.STATE_CONNECTED -> {
                    if (!gatt.discoverServices()) failConnection("无法启动 GATT 服务发现")
                }

                newState == BluetoothProfile.STATE_DISCONNECTED ->
                    failConnection("StarRing BLE GATT 已断开")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (this@StarRingGattProbeClient.gatt !== gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("GATT 服务发现失败：$status")
                return
            }

            val allCharacteristics = gatt.services.flatMap(BluetoothGattService::getCharacteristics)
            mutableEvents.tryEmit(
                StarRingGattEvent.Services(
                    allCharacteristics.joinToString(" · ") {
                        "${it.uuid}@0x${it.instanceId.toString(16).uppercase()}[${it.properties}]"
                    },
                ),
            )
            val write = allCharacteristics.firstOrNull { it.instanceId == WRITE_VALUE_HANDLE }
                ?: allCharacteristics.firstOrNull {
                    it.uuid == CAPTURED_CHARACTERISTIC_UUID && it.canWrite()
                }
                ?: allCharacteristics.firstOrNull { it.canWrite() }
            val notify = allCharacteristics.firstOrNull { it.instanceId == NOTIFY_VALUE_HANDLE }
                ?: allCharacteristics.firstOrNull {
                    it.uuid == CAPTURED_CHARACTERISTIC_UUID && it.canNotify()
                }
                ?: allCharacteristics.firstOrNull { it.canNotify() }

            if (write == null || notify == null) {
                failConnection("未找到官方 GATT 写入/通知特征")
                return
            }
            writeCharacteristic = write
            if (!gatt.setCharacteristicNotification(notify, true)) {
                failConnection("无法启用 StarRing GATT 通知")
                return
            }
            val cccd = notify.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID)
            if (cccd == null) {
                completeReady(write, notify)
                return
            }
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
            if (!started) failConnection("无法写入 StarRing 通知描述符")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (this@StarRingGattProbeClient.gatt !== gatt) return
            if (descriptor.uuid != CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("StarRing 通知描述符写入失败：$status")
                return
            }
            val write = writeCharacteristic ?: return failConnection("GATT 写入特征已丢失")
            completeReady(write, descriptor.characteristic)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            emitIncoming(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            emitIncoming(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (this@StarRingGattProbeClient.gatt !== gatt) return
            val completion = pendingWrite ?: return
            pendingWrite = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                completion.complete(Unit)
            } else {
                completion.completeExceptionally(IOException("GATT 写入失败：$status"))
            }
        }
    }

    suspend fun connect(address: String): StarRingGattEndpoint {
        closeGatt("重新连接", emitEvent = false)
        val bluetoothAdapter = adapter ?: error("设备没有可用的蓝牙适配器")
        check(bluetoothAdapter.isEnabled) { "请先开启蓝牙" }
        val device = bluetoothAdapter.getRemoteDevice(address)
        val completion = CompletableDeferred<StarRingGattEndpoint>()
        ready = completion
        val connection = device.connectGatt(
            appContext,
            false,
            callback,
            BluetoothDevice.TRANSPORT_LE,
        ) ?: error("无法创建 StarRing BLE GATT 会话")
        gatt = connection
        return try {
            withTimeout(CONNECT_TIMEOUT_MS) { completion.await() }
        } catch (failure: Throwable) {
            closeGatt(failure.conciseMessage(), emitEvent = false)
            throw failure
        } finally {
            if (ready === completion) ready = null
        }
    }

    suspend fun send(packet: ByteArray) {
        sendMutex.withLock {
            val active = gatt ?: error("StarRing BLE GATT 尚未连接")
            val characteristic = writeCharacteristic ?: error("StarRing GATT 写入特征尚未就绪")
            val completion = CompletableDeferred<Unit>()
            pendingWrite = completion
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                active.writeCharacteristic(
                    characteristic,
                    packet,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = packet
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                active.writeCharacteristic(characteristic)
            }
            if (!started) {
                pendingWrite = null
                error("无法启动 StarRing GATT 写入")
            }
            withTimeout(WRITE_TIMEOUT_MS) { completion.await() }
        }
    }

    private fun completeReady(
        write: BluetoothGattCharacteristic,
        notify: BluetoothGattCharacteristic,
    ) {
        ready?.complete(StarRingGattEndpoint(write.uuid, notify.uuid))
    }

    private fun emitIncoming(value: ByteArray?) {
        if (value != null && value.isNotEmpty()) {
            mutableEvents.tryEmit(StarRingGattEvent.Incoming(value.copyOf()))
        }
    }

    private fun failConnection(reason: String) {
        ready?.completeExceptionally(IOException(reason))
        pendingWrite?.completeExceptionally(IOException(reason))
        pendingWrite = null
        closeGatt(reason, emitEvent = true)
    }

    private fun closeGatt(reason: String, emitEvent: Boolean) {
        val active = gatt
        gatt = null
        writeCharacteristic = null
        runCatching { active?.disconnect() }
        runCatching { active?.close() }
        if (active != null && emitEvent) {
            mutableEvents.tryEmit(StarRingGattEvent.Disconnected(reason))
        }
    }

    override fun close() {
        closeGatt("已主动断开", emitEvent = false)
    }

    fun destroy() {
        close()
        scope.cancel()
    }

    private fun BluetoothGattCharacteristic.canWrite(): Boolean =
        properties and (
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            ) != 0

    private fun BluetoothGattCharacteristic.canNotify(): Boolean =
        properties and (
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE
            ) != 0

    private fun Throwable.conciseMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private companion object {
        const val WRITE_VALUE_HANDLE = 0xA102
        const val NOTIFY_VALUE_HANDLE = 0xA105
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val WRITE_TIMEOUT_MS = 4_000L
        val CLIENT_CHARACTERISTIC_CONFIGURATION_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val CAPTURED_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("66666666-6666-6666-6666-666666666666")
    }
}
