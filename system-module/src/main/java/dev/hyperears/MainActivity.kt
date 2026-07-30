package dev.hyperears

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hyperears.bridge.ModuleContract
import dev.hyperears.integration.ControlRequest
import dev.hyperears.ui.dashboard.DashboardScreen
import dev.hyperears.ui.dashboard.DashboardUiState
import dev.hyperears.ui.dashboard.DeviceSessionCollection
import dev.hyperears.ui.dashboard.DeviceSessionReducer
import dev.hyperears.ui.dashboard.DeviceSessionSnapshot
import dev.hyperears.ui.theme.HyperEarsTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val sessionCollection = MutableStateFlow(DeviceSessionCollection())
    private val runtimeResponsive = MutableStateFlow(false)
    private val miLinkProcesses = MutableStateFlow<Set<String>>(emptySet())
    private val lastUpdatedAtMillis = MutableStateFlow<Long?>(null)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ModuleContract.ACTION_STATE_CHANGED -> {
                    with(ModuleContract) {
                        val state = intent.readState()
                        val token = intent.readSessionToken()
                        if (state != null && token != null) {
                            sessionCollection.value = DeviceSessionReducer.reduce(
                                previous = sessionCollection.value,
                                state = state,
                                sessionToken = token,
                            )
                            lastUpdatedAtMillis.value = System.currentTimeMillis()
                        }
                    }
                    runtimeResponsive.value = true
                }

                ModuleContract.ACTION_BRIDGE_STATE_OBSERVED -> {
                    val receipt = with(ModuleContract) {
                        intent.readBridgeReceipt()
                    } ?: return
                    sessionCollection.value = DeviceSessionReducer.acceptBridgeReceipt(
                        previous = sessionCollection.value,
                        receipt = receipt,
                    )
                }

                ModuleContract.ACTION_BRIDGE_RUNTIME_OBSERVED -> {
                    val receipt = with(ModuleContract) {
                        intent.readBridgeRuntimeReceipt()
                    } ?: return
                    miLinkProcesses.value += receipt.consumerProcess
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(ModuleContract.ACTION_STATE_CHANGED)
                addAction(ModuleContract.ACTION_BRIDGE_STATE_OBSERVED)
                addAction(ModuleContract.ACTION_BRIDGE_RUNTIME_OBSERVED)
            },
            Context.RECEIVER_EXPORTED,
        )
        setContent {
            HyperEarsTheme {
                val activeSessions = sessionCollection
                    .collectAsStateWithLifecycle()
                    .value
                    .sessions
                val online = runtimeResponsive.collectAsStateWithLifecycle().value
                val bridgeProcesses = miLinkProcesses.collectAsStateWithLifecycle().value
                val updatedAt = lastUpdatedAtMillis.collectAsStateWithLifecycle().value

                DashboardScreen(
                    uiState = DashboardUiState(
                        sessions = activeSessions.values
                            .sortedBy { it.state.deviceName.orEmpty() },
                        runtimeResponsive = online,
                        miLinkProcesses = bridgeProcesses,
                        lastUpdatedAtMillis = updatedAt,
                    ),
                    onRefresh = {
                        requestRuntimeState()
                        activeSessions.values.forEach(::sendRefreshControl)
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runtimeResponsive.value = false
        miLinkProcesses.value = emptySet()
        requestRuntimeState()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    private fun requestRuntimeState() {
        sendBroadcast(
            ModuleContract.requestState(packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        )
        sendBroadcast(
            ModuleContract.requestBridgeStatus(packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        )
    }

    private fun sendRefreshControl(session: DeviceSessionSnapshot) {
        val address = session.state.address ?: return
        if (!session.state.connected) return
        sendBroadcast(
            ModuleContract.control(ControlRequest.Refresh, address, session.sessionToken)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        )
    }
}
