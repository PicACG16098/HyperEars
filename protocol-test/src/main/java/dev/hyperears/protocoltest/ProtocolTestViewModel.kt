package dev.hyperears.protocoltest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hyperears.protocol.vivo.VivoTwsProtocol
import dev.hyperears.protocol.vivo.VivoTwsProtocol.NoiseMode
import dev.hyperears.protocol.vivo.VivoTwsProtocol.Variant
import dev.hyperears.protocol.vivo.VivoFastPairIdentity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal data class PairedDevice(
    val name: String,
    val address: String,
    val likelyVivo: Boolean,
)

internal enum class ConnectionPhase {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

internal enum class IdentityScanPhase {
    IDLE,
    SCANNING,
    FAILED,
}

internal data class VivoIdentityDetection(
    val id: String,
    val address: String,
    val name: String?,
    val rssi: Int,
    val identity: VivoFastPairIdentity,
    val rawAdvertisement: String,
    val seenCount: Int,
    val lastSeen: String,
)

internal data class ProtocolLog(
    val id: Long,
    val time: String,
    val direction: String,
    val message: String,
    val hex: String? = null,
)

internal data class ProtocolUiState(
    val permissionGranted: Boolean = false,
    val pairedDevices: List<PairedDevice> = emptyList(),
    val selectedAddress: String = "",
    val selectedName: String = "",
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val connectionMessage: String = "尚未连接",
    val endpoint: String? = null,
    val selectedVariant: Variant = Variant.AIR3_PRO_CAPTURED,
    val detectedVariant: Variant? = null,
    val battery: VivoTwsProtocol.BatteryState? = null,
    val noise: VivoTwsProtocol.NoiseState? = null,
    val handshakeStatus: String = "未测试",
    val noiseApiStatus: String = "未测试",
    val batteryApiStatus: String = "未测试",
    val identityScanPhase: IdentityScanPhase = IdentityScanPhase.IDLE,
    val identityScanMessage: String = "尚未扫描",
    val observedAdvertisements: Int = 0,
    val observedVivoAdvertisements: Int = 0,
    val identityDetections: List<VivoIdentityDetection> = emptyList(),
    val rawCommand: String = "",
    val logs: List<ProtocolLog> = emptyList(),
)

@SuppressLint("MissingPermission")
internal class ProtocolTestViewModel(application: Application) : AndroidViewModel(application) {
    private val adapter =
        (application.getSystemService(Application.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val client = VivoRfcommClient(application)
    private val identityScanner = VivoIdentityScanner(application)
    private val decoder = VivoTwsProtocol.Decoder()
    private val mutableState = MutableStateFlow(ProtocolUiState())
    val state: StateFlow<ProtocolUiState> = mutableState.asStateFlow()
    private val logId = AtomicLong()
    private var connectionJob: Job? = null
    private var identityScanTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            client.events.collect(::handleClientEvent)
        }
        viewModelScope.launch {
            identityScanner.events.collect(::handleIdentityScanEvent)
        }
        updatePermissionState()
    }

    fun updatePermissionState() {
        val granted = hasBluetoothPermission()
        mutableState.value = mutableState.value.copy(permissionGranted = granted)
        if (granted) refreshPairedDevices()
    }

    fun refreshPairedDevices() {
        if (!hasBluetoothPermission()) {
            mutableState.value = mutableState.value.copy(
                permissionGranted = false,
                connectionMessage = "需要附近设备权限",
            )
            return
        }
        val devices = runCatching {
            adapter?.bondedDevices.orEmpty()
                .map { device ->
                    val name = device.name?.takeIf { it.isNotBlank() } ?: "未命名蓝牙设备"
                    PairedDevice(
                        name = name,
                        address = device.address,
                        likelyVivo = name.contains("vivo", ignoreCase = true) &&
                            (name.contains("TWS", ignoreCase = true) ||
                                name.contains("Air", ignoreCase = true)),
                    )
                }
                .sortedWith(compareByDescending<PairedDevice> { it.likelyVivo }.thenBy { it.name })
        }.getOrElse {
            addLog("ERR", "读取已配对设备失败：${it.conciseMessage()}")
            emptyList()
        }

        val current = mutableState.value
        val selected = devices.firstOrNull { it.address == current.selectedAddress }
            ?: devices.firstOrNull { it.likelyVivo }
        mutableState.value = current.copy(
            permissionGranted = true,
            pairedDevices = devices,
            selectedAddress = selected?.address ?: current.selectedAddress,
            selectedName = selected?.name ?: current.selectedName,
        )
        addLog("SYS", "发现 ${devices.size} 个已配对设备，其中 ${devices.count { it.likelyVivo }} 个疑似 vivo TWS")
    }

    fun selectDevice(device: PairedDevice) {
        if (mutableState.value.phase == ConnectionPhase.CONNECTED) disconnect()
        mutableState.value = mutableState.value.copy(
            selectedAddress = device.address,
            selectedName = device.name,
        )
    }

    fun updateAddress(address: String) {
        mutableState.value = mutableState.value.copy(
            selectedAddress = address.trim().uppercase(Locale.US),
            selectedName = mutableState.value.pairedDevices
                .firstOrNull { it.address.equals(address.trim(), ignoreCase = true) }
                ?.name
                .orEmpty(),
        )
    }

    fun selectVariant(variant: Variant) {
        mutableState.value = mutableState.value.copy(selectedVariant = variant)
        addLog("SYS", "选择协议变体：${variant.label}")
    }

    fun updateRawCommand(value: String) {
        mutableState.value = mutableState.value.copy(rawCommand = value)
    }

    fun connect() {
        if (!hasBluetoothPermission()) {
            updatePermissionState()
            return
        }
        val address = mutableState.value.selectedAddress.trim()
        if (!MAC_ADDRESS.matches(address)) {
            mutableState.value = mutableState.value.copy(
                phase = ConnectionPhase.FAILED,
                connectionMessage = "请输入有效的蓝牙 MAC 地址",
            )
            return
        }

        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            decoder.reset()
            mutableState.value = mutableState.value.copy(
                phase = ConnectionPhase.CONNECTING,
                connectionMessage = "正在探测 RFCOMM 入口…",
                endpoint = null,
                detectedVariant = null,
                battery = null,
                noise = null,
                handshakeStatus = "未测试",
                noiseApiStatus = "未测试",
                batteryApiStatus = "未测试",
            )
            runCatching { client.connect(address) }
                .onSuccess { endpoint ->
                    mutableState.value = mutableState.value.copy(
                        phase = ConnectionPhase.CONNECTED,
                        connectionMessage = "RFCOMM 已连接",
                        endpoint = endpoint.label,
                    )
                    runReadOnlyProbe()
                }
                .onFailure { failure ->
                    if (mutableState.value.phase == ConnectionPhase.CONNECTING) {
                        mutableState.value = mutableState.value.copy(
                            phase = ConnectionPhase.FAILED,
                            connectionMessage = failure.conciseMessage(),
                        )
                        addLog("ERR", "连接失败：${failure.conciseMessage()}")
                    }
                }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        client.close()
        mutableState.value = mutableState.value.copy(
            phase = ConnectionPhase.DISCONNECTED,
            connectionMessage = "已断开",
            endpoint = null,
        )
    }

    fun runReadOnlyProbe() {
        viewModelScope.launch {
            if (!ensureConnected()) return@launch
            mutableState.value = mutableState.value.copy(
                handshakeStatus = "等待响应",
                noiseApiStatus = "等待响应",
                batteryApiStatus = "等待响应",
            )
            send(VivoTwsProtocol.handshake(), "v4 握手")
            delay(PROBE_GAP_MS)
            send(
                VivoTwsProtocol.queryNoiseMode(Variant.AIR3_PRO_CAPTURED),
                "查询降噪（Air3 Pro v3）",
            )
            delay(PROBE_GAP_MS)
            send(
                VivoTwsProtocol.queryNoiseMode(Variant.HANDMADE_V4),
                "查询降噪（公开资料 v4）",
            )
            delay(PROBE_GAP_MS)
            send(VivoTwsProtocol.queryBattery(), "查询左右耳/充电盒电量")
            markTimeoutsLater()
        }
    }

    fun sendHandshake() {
        viewModelScope.launch {
            if (!ensureConnected()) return@launch
            mutableState.value = mutableState.value.copy(handshakeStatus = "等待响应")
            send(VivoTwsProtocol.handshake(), "v4 握手")
            markTimeoutsLater()
        }
    }

    fun queryNoise() {
        viewModelScope.launch {
            if (!ensureConnected()) return@launch
            val variant = mutableState.value.selectedVariant
            mutableState.value = mutableState.value.copy(noiseApiStatus = "等待响应")
            send(VivoTwsProtocol.queryNoiseMode(variant), "查询降噪（${variant.label}）")
            markTimeoutsLater()
        }
    }

    fun queryBattery() {
        viewModelScope.launch {
            if (!ensureConnected()) return@launch
            mutableState.value = mutableState.value.copy(batteryApiStatus = "等待响应")
            send(VivoTwsProtocol.queryBattery(), "查询左右耳/充电盒电量")
            markTimeoutsLater()
        }
    }

    fun setNoiseMode(mode: NoiseMode) {
        viewModelScope.launch {
            if (!ensureConnected()) return@launch
            val variant = mutableState.value.selectedVariant
            mutableState.value = mutableState.value.copy(noiseApiStatus = "等待设置确认")
            send(
                VivoTwsProtocol.setNoiseMode(mode, variant),
                "设置${mode.label}（${variant.label}）",
            )
            markTimeoutsLater()
        }
    }

    fun sendRaw() {
        viewModelScope.launch {
            if (!ensureConnected()) return@launch
            val raw = parseHex(mutableState.value.rawCommand)
            if (raw == null || raw.isEmpty()) {
                addLog("ERR", "原始命令不是有效的十六进制字节")
                return@launch
            }
            send(raw, "发送原始命令")
        }
    }

    fun clearLogs() {
        mutableState.value = mutableState.value.copy(logs = emptyList())
    }

    fun startIdentityScan() {
        if (!hasBluetoothPermission()) {
            updatePermissionState()
            return
        }
        identityScanTimeoutJob?.cancel()
        mutableState.value = mutableState.value.copy(
            identityScanPhase = IdentityScanPhase.SCANNING,
            identityScanMessage = "正在启动 BLE 扫描…",
            observedAdvertisements = 0,
            observedVivoAdvertisements = 0,
            identityDetections = emptyList(),
        )
        runCatching { identityScanner.start() }
            .onFailure { failure ->
                mutableState.value = mutableState.value.copy(
                    identityScanPhase = IdentityScanPhase.FAILED,
                    identityScanMessage = failure.conciseMessage(),
                )
                addLog("ERR", "vivo 判型扫描启动失败：${failure.conciseMessage()}")
                return
            }
        identityScanTimeoutJob = viewModelScope.launch {
            delay(IDENTITY_SCAN_DURATION_MS)
            identityScanner.stop()
        }
    }

    fun stopIdentityScan() {
        identityScanTimeoutJob?.cancel()
        identityScanTimeoutJob = null
        identityScanner.stop()
    }

    private suspend fun send(packet: ByteArray, message: String) {
        addLog("TX", message, VivoTwsProtocol.run { packet.hex() })
        runCatching { client.send(packet) }
            .onFailure {
                addLog("ERR", "发送失败：${it.conciseMessage()}")
                mutableState.value = mutableState.value.copy(
                    phase = ConnectionPhase.FAILED,
                    connectionMessage = it.conciseMessage(),
                )
            }
    }

    private fun handleClientEvent(event: ClientEvent) {
        when (event) {
            is ClientEvent.Attempt ->
                addLog("CONN", "尝试 ${event.endpoint.label}")

            is ClientEvent.AttemptFailed ->
                addLog("CONN", "${event.endpoint.label} 失败：${event.reason}")

            is ClientEvent.Connected ->
                addLog("CONN", "已连接 ${event.endpoint.label}")

            is ClientEvent.Incoming ->
                handleIncoming(event.bytes)

            is ClientEvent.Disconnected -> {
                addLog("CONN", event.reason)
                if (mutableState.value.phase == ConnectionPhase.CONNECTED) {
                    mutableState.value = mutableState.value.copy(
                        phase = ConnectionPhase.DISCONNECTED,
                        connectionMessage = event.reason,
                        endpoint = null,
                    )
                }
            }
        }
    }

    private fun handleIncoming(bytes: ByteArray) {
        addLog("RX", "收到 ${bytes.size} 字节", VivoTwsProtocol.run { bytes.hex() })
        decoder.offer(bytes).forEach { frame ->
            addLog(
                "FRAME",
                "v${frame.version} vendor=0x${frame.vendor.hex4()} cmd=0x${frame.command.hex4()} payload=${frame.payload.size}",
                VivoTwsProtocol.run { frame.payload.hex() },
            )
            VivoTwsProtocol.parseHandshakeState(frame)?.let { handshake ->
                mutableState.value = mutableState.value.copy(
                    handshakeStatus = if (handshake.accepted) "可用 · 响应 v${handshake.version}" else "耳机拒绝",
                )
            }
            VivoTwsProtocol.parseNoiseState(frame)?.let { noise ->
                val inferred = inferVariant(noise)
                mutableState.value = mutableState.value.copy(
                    noise = noise,
                    detectedVariant = inferred ?: mutableState.value.detectedVariant,
                    noiseApiStatus = "可用 · ${noise.mode.label} · 响应 v${noise.version}",
                )
            }
            VivoTwsProtocol.parseBatteryState(frame)?.let { battery ->
                mutableState.value = mutableState.value.copy(
                    battery = battery,
                    batteryApiStatus = "可用 · 响应 v${battery.version}",
                )
            }
        }
    }

    private fun handleIdentityScanEvent(event: IdentityScanEvent) {
        when (event) {
            IdentityScanEvent.Started -> {
                mutableState.value = mutableState.value.copy(
                    identityScanPhase = IdentityScanPhase.SCANNING,
                    identityScanMessage = "扫描中 · 请开合充电盒或让耳机进入可发现状态",
                )
                addLog("BLE", "开始 20 秒 vivo 广播判型扫描")
            }

            is IdentityScanEvent.Detection -> {
                val key = listOf(
                    event.address,
                    event.identity.uuid,
                    event.identity.modelId,
                    event.identity.layout,
                ).joinToString("|")
                val current = mutableState.value
                val previous = current.identityDetections.firstOrNull { it.id == key }
                val detection = VivoIdentityDetection(
                    id = key,
                    address = event.address,
                    name = event.name,
                    rssi = event.rssi,
                    identity = event.identity,
                    rawAdvertisement = event.rawAdvertisement.hexBytes(),
                    seenCount = (previous?.seenCount ?: 0) + 1,
                    lastSeen = TIME_FORMAT.format(Date()),
                )
                mutableState.value = current.copy(
                    identityScanMessage =
                        "已命中 vivo 官方广播结构 · model=${event.identity.modelId}",
                    observedAdvertisements = event.observedAdvertisements,
                    observedVivoAdvertisements = event.observedVivoAdvertisements,
                    identityDetections = (
                        listOf(detection) +
                            current.identityDetections.filterNot { it.id == key }
                        ).take(MAX_IDENTITY_DETECTIONS),
                )
                if (previous == null) {
                    addLog(
                        "BLE",
                        "命中 vivo 型号：${event.address} · ${event.identity.uuidLabel} · " +
                            "${event.identity.layout.label} · model=${event.identity.modelId}",
                        detection.rawAdvertisement,
                    )
                }
            }

            is IdentityScanEvent.Stopped -> {
                identityScanTimeoutJob?.cancel()
                identityScanTimeoutJob = null
                val current = mutableState.value
                mutableState.value = current.copy(
                    identityScanPhase = IdentityScanPhase.IDLE,
                    identityScanMessage = if (event.observedVivoAdvertisements > 0) {
                        "扫描完成 · 捕获 ${event.observedVivoAdvertisements} 条 vivo 广播"
                    } else {
                        "扫描完成 · 未捕获 vivo 官方广播结构"
                    },
                    observedAdvertisements = event.observedAdvertisements,
                    observedVivoAdvertisements = event.observedVivoAdvertisements,
                )
                addLog(
                    "BLE",
                    "判型扫描结束：共 ${event.observedAdvertisements} 条广播，" +
                        "vivo ${event.observedVivoAdvertisements} 条",
                )
            }

            is IdentityScanEvent.Failed -> {
                identityScanTimeoutJob?.cancel()
                identityScanTimeoutJob = null
                mutableState.value = mutableState.value.copy(
                    identityScanPhase = IdentityScanPhase.FAILED,
                    identityScanMessage = event.reason,
                    observedAdvertisements = event.observedAdvertisements,
                    observedVivoAdvertisements = event.observedVivoAdvertisements,
                )
                addLog("ERR", event.reason)
            }
        }
    }

    private fun inferVariant(noise: VivoTwsProtocol.NoiseState): Variant? = when {
        noise.noiseEffect == 4 && noise.transparencyEffect == 0 -> Variant.AIR3_PRO_CAPTURED
        noise.noiseEffect == 3 && noise.transparencyEffect == 1 -> Variant.HANDMADE_V4
        else -> null
    }

    private fun markTimeoutsLater() {
        viewModelScope.launch {
            delay(RESPONSE_TIMEOUT_MS)
            val current = mutableState.value
            mutableState.value = current.copy(
                handshakeStatus = current.handshakeStatus.timeoutIfWaiting(),
                noiseApiStatus = current.noiseApiStatus.timeoutIfWaiting(),
                batteryApiStatus = current.batteryApiStatus.timeoutIfWaiting(),
            )
        }
    }

    private fun String.timeoutIfWaiting(): String =
        if (startsWith("等待")) "超时/未观察到响应" else this

    private fun ensureConnected(): Boolean {
        if (mutableState.value.phase == ConnectionPhase.CONNECTED) return true
        addLog("ERR", "请先连接耳机 RFCOMM")
        return false
    }

    private fun addLog(direction: String, message: String, hex: String? = null) {
        val entry = ProtocolLog(
            id = logId.incrementAndGet(),
            time = TIME_FORMAT.format(Date()),
            direction = direction,
            message = message,
            hex = hex,
        )
        Log.d(TAG, "[$direction] $message${hex?.let { " | $it" }.orEmpty()}")
        mutableState.value = mutableState.value.copy(
            logs = (listOf(entry) + mutableState.value.logs).take(MAX_LOGS),
        )
    }

    private fun hasBluetoothPermission(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(getApplication(), it) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun parseHex(value: String): ByteArray? {
        val compact = value.filterNot { it.isWhitespace() || it == ':' || it == '-' }
        if (compact.isEmpty() || compact.length % 2 != 0 || compact.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
            return null
        }
        return runCatching {
            ByteArray(compact.length / 2) { index ->
                compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun Throwable.conciseMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private fun Int.hex4(): String = toString(16).uppercase(Locale.US).padStart(4, '0')

    private fun ByteArray.hexBytes(): String =
        joinToString(" ") { byte -> "%02X".format(Locale.US, byte.toInt() and 0xFF) }

    override fun onCleared() {
        identityScanTimeoutJob?.cancel()
        identityScanner.close()
        client.destroy()
        super.onCleared()
    }

    private companion object {
        const val TAG = "HyperEarsProtocol"
        const val PROBE_GAP_MS = 300L
        const val RESPONSE_TIMEOUT_MS = 2_800L
        const val IDENTITY_SCAN_DURATION_MS = 20_000L
        const val MAX_LOGS = 250
        const val MAX_IDENTITY_DETECTIONS = 20
        val MAC_ADDRESS = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
        val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}
