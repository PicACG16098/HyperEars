package dev.hyperears.integration

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned state envelope shared by the Bluetooth and MiLink processes. */
@Serializable
private data class DeviceFeatureStateEnvelope(
    val schemaVersion: Int,
    val snapshot: DeviceFeatureSnapshot,
)

/**
 * Framework-owned serialization for device feature state.
 *
 * Adapter and card implementations exchange typed [DeviceFeatureState] values only. JSON, Intent
 * extras and process boundaries remain an integration concern owned here.
 */
object FeatureStateTransport {
    private const val SCHEMA_VERSION = 1
    private const val MAX_ENCODED_BYTES = 16 * 1024

    private val json = Json {
        classDiscriminator = "feature"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(snapshot: DeviceFeatureSnapshot): String {
        val encoded = json.encodeToString(
            DeviceFeatureStateEnvelope(
                schemaVersion = SCHEMA_VERSION,
                snapshot = snapshot,
            ),
        )
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_ENCODED_BYTES) {
            "Device feature state exceeds the transport payload limit"
        }
        return encoded
    }

    fun decode(encoded: String): DeviceFeatureSnapshot? {
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_ENCODED_BYTES) return null
        return runCatching {
            json.decodeFromString<DeviceFeatureStateEnvelope>(encoded)
                .takeIf { it.schemaVersion == SCHEMA_VERSION }
                ?.snapshot
        }.getOrNull()
    }
}
