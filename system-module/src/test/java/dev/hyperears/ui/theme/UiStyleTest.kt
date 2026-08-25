package dev.hyperears.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStyleTest {
    @Test
    fun material3IsTheCompatibilityDefault() {
        assertEquals(UiStyle.MATERIAL3, UiStyle.fromStoredValue(null))
        assertEquals(UiStyle.MATERIAL3, UiStyle.fromStoredValue("unknown"))
    }

    @Test
    fun restoresEveryKnownStyle() {
        UiStyle.entries.forEach { style ->
            assertEquals(style, UiStyle.fromStoredValue(style.name))
        }
    }
}
