package dev.hyperears.protocoltest

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dev.hyperears.protocol.vivo.VivoFastPairAdvertisementParser
import dev.hyperears.protocol.vivo.VivoFastPairIdentity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

internal sealed interface IdentityScanEvent {
    data object Started : IdentityScanEvent

    data class Detection(
        val address: String,
        val name: String?,
        val rssi: Int,
        val identity: VivoFastPairIdentity,
        val rawAdvertisement: ByteArray,
        val observedAdvertisements: Int,
        val observedVivoAdvertisements: Int,
    ) : IdentityScanEvent

    data class Stopped(
        val observedAdvertisements: Int,
        val observedVivoAdvertisements: Int,
    ) : IdentityScanEvent

    data class Failed(
        val reason: String,
        val observedAdvertisements: Int,
        val observedVivoAdvertisements: Int,
    ) : IdentityScanEvent
}

@SuppressLint("MissingPermission")
internal class VivoIdentityScanner(context: Context) : Closeable {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val observedAdvertisements = AtomicInteger()
    private val observedVivoAdvertisements = AtomicInteger()
    private val mutableEvents = MutableSharedFlow<IdentityScanEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = mutableEvents.asSharedFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            observe(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::observe)
        }

        override fun onScanFailed(errorCode: Int) {
            synchronized(lock) {
                scanner = null
                scanning = false
            }
            mutableEvents.tryEmit(
                IdentityScanEvent.Failed(
                    reason = "BLE 扫描失败（errorCode=$errorCode）",
                    observedAdvertisements = observedAdvertisements.get(),
                    observedVivoAdvertisements = observedVivoAdvertisements.get(),
                ),
            )
        }
    }

    private val lock = Any()
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false

    fun start() {
        val bluetoothAdapter = adapter ?: error("设备没有可用的蓝牙适配器")
        check(bluetoothAdapter.isEnabled) { "请先开启蓝牙" }
        stop()
        observedAdvertisements.set(0)
        observedVivoAdvertisements.set(0)

        val activeScanner = bluetoothAdapter.bluetoothLeScanner
            ?: error("当前蓝牙适配器不支持 BLE 扫描")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        synchronized(lock) {
            scanner = activeScanner
            scanning = true
        }
        try {
            activeScanner.startScan(null, settings, callback)
            mutableEvents.tryEmit(IdentityScanEvent.Started)
        } catch (failure: Throwable) {
            synchronized(lock) {
                scanner = null
                scanning = false
            }
            throw failure
        }
    }

    fun stop() {
        val activeScanner = synchronized(lock) {
            if (!scanning) return
            scanning = false
            scanner.also { scanner = null }
        }
        runCatching { activeScanner?.stopScan(callback) }
        mutableEvents.tryEmit(
            IdentityScanEvent.Stopped(
                observedAdvertisements = observedAdvertisements.get(),
                observedVivoAdvertisements = observedVivoAdvertisements.get(),
            ),
        )
    }

    private fun observe(result: ScanResult) {
        val total = observedAdvertisements.incrementAndGet()
        val bytes = result.scanRecord?.bytes ?: return
        val identity = VivoFastPairAdvertisementParser.parse(bytes) ?: return
        val vivoTotal = observedVivoAdvertisements.incrementAndGet()
        val name = result.scanRecord?.deviceName
            ?: runCatching { result.device.name }.getOrNull()
        mutableEvents.tryEmit(
            IdentityScanEvent.Detection(
                address = result.device.address,
                name = name,
                rssi = result.rssi,
                identity = identity,
                rawAdvertisement = bytes.copyOf(),
                observedAdvertisements = total,
                observedVivoAdvertisements = vivoTotal,
            ),
        )
    }

    override fun close() {
        stop()
    }
}
