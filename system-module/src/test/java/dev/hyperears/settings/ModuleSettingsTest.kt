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

        assertFalse(defaults.preferVendorControlApp)
        assertFalse(defaults.yieldToVendorControlApp)
        assertTrue(defaults.disabledAdapterIds.isEmpty())
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
