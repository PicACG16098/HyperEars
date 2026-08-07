package dev.hyperears.settings

import org.junit.Assert.assertFalse
import org.junit.Test

class ModuleSettingsTest {
    @Test
    fun vendorApplicationIntegrationIsOptIn() {
        val defaults = ModuleSettings()

        assertFalse(defaults.preferVendorControlApp)
        assertFalse(defaults.yieldToVendorControlApp)
    }
}
