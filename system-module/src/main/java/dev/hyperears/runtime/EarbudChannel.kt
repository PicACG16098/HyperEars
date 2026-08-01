package dev.hyperears.runtime

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import dev.hyperears.integration.EarbudTransportSpec
import dev.hyperears.integration.GattTransportSpec
import dev.hyperears.integration.RfcommEndpointSpec
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * A connected vendor-control byte channel.
 *
 * Sessions and protocols do not know whether bytes travel over RFCOMM or BLE GATT. GATT
 * notifications are exposed as the same ordered byte stream consumed by protocol decoders.
 */
internal interface EarbudChannel : Closeable {
    val endpointId: String

    suspend fun connect()

    suspend fun read(buffer: ByteArray): Int

    suspend fun write(bytes: ByteArray)
}

internal fun interface EarbudChannelFactory {
    fun create(
        context: Context,
        device: BluetoothDevice,
        transport: EarbudTransportSpec,
    ): EarbudChannel
}

internal object AndroidEarbudChannelFactory : EarbudChannelFactory {
    override fun create(
        context: Context,
        device: BluetoothDevice,
        transport: EarbudTransportSpec,
    ): EarbudChannel = when (transport) {
        is RfcommEndpointSpec -> AndroidRfcommChannel(
            socket = createSocket(device, transport),
            endpointId = transport.id,
        )

        is GattTransportSpec -> AndroidGattChannel(
            context = context,
            device = device,
            spec = transport,
        )
    }

    private fun createSocket(
        device: BluetoothDevice,
        endpoint: RfcommEndpointSpec,
    ): BluetoothSocket = when (endpoint) {
        is RfcommEndpointSpec.ServiceUuid ->
            device.createRfcommSocketToServiceRecord(UUID.fromString(endpoint.uuid))

        is RfcommEndpointSpec.Channel -> {
            val methodName = if (endpoint.secure) {
                "createRfcommSocket"
            } else {
                "createInsecureRfcommSocket"
            }
            device.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
                .invoke(device, endpoint.number) as BluetoothSocket
        }
    }
}

private class AndroidRfcommChannel(
    private val socket: BluetoothSocket,
    override val endpointId: String,
) : EarbudChannel {
    override suspend fun connect() {
        runInterruptible(Dispatchers.IO) { socket.connect() }
    }

    override suspend fun read(buffer: ByteArray): Int =
        runInterruptible(Dispatchers.IO) { socket.inputStream.read(buffer) }

    override suspend fun write(bytes: ByteArray) {
        runInterruptible(Dispatchers.IO) {
            socket.outputStream.write(bytes)
            socket.outputStream.flush()
        }
    }

    override fun close() {
        runCatching(socket::close)
    }
}

/** BLE GATT implementation of the common byte-channel contract. */
@SuppressLint("MissingPermission")
private class AndroidGattChannel(
    context: Context,
    private val device: BluetoothDevice,
    private val spec: GattTransportSpec,
) : EarbudChannel {
    override val endpointId: String = spec.id

    private val appContext = context.applicationContext ?: context
    private val closed = AtomicBoolean()
    private val connectCompletion = CompletableDeferred<Unit>()
    private val incoming = Channel<ByteArray>(Channel.BUFFERED)
    private val writeMutex = Mutex()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var pendingWrite: CompletableDeferred<Unit>? = null

    private var pendingRead = ByteArray(0)

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!owns(gatt)) return
            when {
                status != BluetoothGatt.GATT_SUCCESS ->
                    terminate(IOException("GATT connection status=$status"))

                newState == BluetoothProfile.STATE_CONNECTED -> {
                    if (!gatt.discoverServices()) {
                        terminate(IOException("GATT service discovery did not start"))
                    }
                }

                newState == BluetoothProfile.STATE_DISCONNECTED ->
                    terminate(IOException("GATT disconnected"))
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!owns(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                terminate(IOException("GATT service discovery status=$status"))
                return
            }

            val characteristics = gatt.services.flatMap(BluetoothGattService::getCharacteristics)
            val write = characteristics.resolve(
                uuid = UUID.fromString(spec.writeCharacteristicUuid),
                instanceId = spec.writeInstanceId,
            ) { it.canWrite() }
            val notify = characteristics.resolve(
                uuid = UUID.fromString(spec.notifyCharacteristicUuid),
                instanceId = spec.notifyInstanceId,
            ) { it.canNotify() }
            if (write == null || notify == null) {
                terminate(IOException("captured GATT characteristics are unavailable"))
                return
            }

            writeCharacteristic = write
            if (!gatt.setCharacteristicNotification(notify, true)) {
                terminate(IOException("GATT notification registration failed"))
                return
            }
            val cccd = notify.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID)
            if (cccd == null) {
                connectCompletion.complete(Unit)
                return
            }
            val started =
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
            if (!started) terminate(IOException("GATT CCCD write did not start"))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (!owns(gatt) || descriptor.uuid != CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) {
                return
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connectCompletion.complete(Unit)
            } else {
                terminate(IOException("GATT CCCD write status=$status"))
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            offerIncoming(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            offerIncoming(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (!owns(gatt)) return
            val completion = pendingWrite ?: return
            pendingWrite = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                completion.complete(Unit)
            } else {
                completion.completeExceptionally(IOException("GATT write status=$status"))
            }
        }
    }

    override suspend fun connect() {
        check(!closed.get()) { "GATT channel is closed" }
        val active = device.connectGatt(
            appContext,
            false,
            callback,
            BluetoothDevice.TRANSPORT_LE,
        ) ?: error("could not create GATT client")
        gatt = active
        connectCompletion.await()
    }

    override suspend fun read(buffer: ByteArray): Int {
        require(buffer.isNotEmpty())
        if (pendingRead.isEmpty()) {
            val result = incoming.receiveCatching()
            result.exceptionOrNull()?.let { throw it }
            pendingRead = result.getOrNull() ?: return -1
        }
        val count = minOf(buffer.size, pendingRead.size)
        pendingRead.copyInto(buffer, endIndex = count)
        pendingRead = pendingRead.copyOfRange(count, pendingRead.size)
        return count
    }

    override suspend fun write(bytes: ByteArray) {
        require(bytes.isNotEmpty())
        writeMutex.withLock {
            val active = gatt ?: error("GATT is not connected")
            val characteristic = writeCharacteristic ?: error("GATT write characteristic is not ready")
            val completion = CompletableDeferred<Unit>()
            pendingWrite = completion
            val started = active.writeCharacteristic(
                characteristic,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
            if (!started) {
                pendingWrite = null
                throw IOException("GATT write did not start")
            }
            withTimeout(WRITE_TIMEOUT_MS) { completion.await() }
        }
    }

    override fun close() {
        terminate(IOException("GATT channel closed"))
    }

    private fun owns(candidate: BluetoothGatt): Boolean =
        !closed.get() && (gatt == null || gatt === candidate)

    private fun offerIncoming(value: ByteArray?) {
        if (value != null && value.isNotEmpty()) incoming.trySend(value.copyOf())
    }

    private fun terminate(error: IOException) {
        if (!closed.compareAndSet(false, true)) return
        connectCompletion.completeExceptionally(error)
        pendingWrite?.completeExceptionally(error)
        pendingWrite = null
        incoming.close(error)
        val active = gatt
        gatt = null
        writeCharacteristic = null
        runCatching { active?.disconnect() }
        runCatching { active?.close() }
    }

    private fun List<BluetoothGattCharacteristic>.resolve(
        uuid: UUID,
        instanceId: Int?,
        predicate: (BluetoothGattCharacteristic) -> Boolean,
    ): BluetoothGattCharacteristic? =
        firstOrNull {
            instanceId != null && it.uuid == uuid && it.instanceId == instanceId && predicate(it)
        } ?: firstOrNull {
            it.uuid == uuid && predicate(it)
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

    private companion object {
        const val WRITE_TIMEOUT_MS = 4_000L
        val CLIENT_CHARACTERISTIC_CONFIGURATION_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
