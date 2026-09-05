package com.balatromodmanager.settings

import org.junit.Assert.assertFalse
import org.junit.Test

class VisualSettingsTest {
    @Test
    fun `new installations use the normal static theme`() {
        val settings = VisualSettings()

        assertFalse(settings.darkMode)
        assertFalse(settings.animatedBackground)
    }
}
