package dev.hyperears.runtime

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import dev.hyperears.integration.RfcommEndpointSpec
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * A connected vendor-control transport.
 *
 * Device sessions depend on this interface rather than Android RFCOMM directly so that a
 * future profile can select BLE GATT without changing session or service lifecycle code.
 */
internal interface EarbudChannel : Closeable {
    val endpointId: String

    suspend fun connect()

    suspend fun read(buffer: ByteArray): Int

    suspend fun write(bytes: ByteArray)
}

internal fun interface EarbudChannelFactory {
    fun create(device: BluetoothDevice, endpoint: RfcommEndpointSpec): EarbudChannel
}

internal object AndroidRfcommChannelFactory : EarbudChannelFactory {
    override fun create(
        device: BluetoothDevice,
        endpoint: RfcommEndpointSpec,
    ): EarbudChannel = AndroidRfcommChannel(
        socket = createSocket(device, endpoint),
        endpointId = endpoint.id,
    )

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
