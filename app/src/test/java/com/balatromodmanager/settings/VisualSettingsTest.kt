package com.balatromodmanager.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSettingsTest {
    @Test
    fun `new installations use the dark static theme`() {
        val settings = VisualSettings()

        assertTrue(settings.darkMode)
        assertFalse(settings.animatedBackground)
    }
}
