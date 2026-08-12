package dev.hyperears.settings

import java.util.HashSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleSettingsTest {
    @Test
    fun vendorApplicationIntegrationIsOptIn() {
        val defaults = ModuleSettings()

        assertEquals(MoreSettingsTarget.SYSTEM_SETTINGS, defaults.moreSettingsTarget)
        assertFalse(defaults.yieldToVendorControlApp)
        assertTrue(defaults.disabledAdapterIds.isEmpty())
    }

    @Test
    fun moreSettingsTargetRejectsUnknownStoredValues() {
        assertEquals(
            MoreSettingsTarget.VENDOR_APP,
            MoreSettingsTarget.fromStoredValue("VENDOR_APP"),
        )
        assertEquals(null, MoreSettingsTarget.fromStoredValue("vendor"))
        assertEquals(null, MoreSettingsTarget.fromStoredValue(null))
    }

    @Test
    fun legacyVendorBooleanMigratesToTheClosestNavigationTarget() {
        assertEquals(
            MoreSettingsTarget.VENDOR_APP,
            resolveMoreSettingsTarget(storedValue = null, legacyPreferVendorApp = true),
        )
        assertEquals(
            MoreSettingsTarget.SYSTEM_SETTINGS,
            resolveMoreSettingsTarget(storedValue = null, legacyPreferVendorApp = false),
        )
        assertEquals(
            MoreSettingsTarget.HYPEREARS,
            resolveMoreSettingsTarget(
                storedValue = "HYPEREARS",
                legacyPreferVendorApp = true,
            ),
        )
    }

    @Test
    fun remotePreferenceSetsAlwaysUseAPlatformCollection() {
        val empty = emptySet<String>().toRemotePreferencesSet()
        val populated = setOf("vivo-family", "vivo-tws-air3-pro").toRemotePreferencesSet()

        assertEquals(HashSet::class.java, empty.javaClass)
        assertEquals(HashSet::class.java, populated.javaClass)
        assertTrue(empty.isEmpty())
        assertEquals(setOf("vivo-family", "vivo-tws-air3-pro"), populated)
    }
}
