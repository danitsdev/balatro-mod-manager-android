package com.balatromobilemodmanager.installer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamoddedBlacklistTest {
    @Test
    fun parsesSteamoddedBlacklistCaseInsensitively() {
        val result = SteamoddedBlacklist.parse("# disabled mods\nCryptid\n\nTalisman\n")

        assertEquals(setOf("cryptid", "talisman"), result)
    }

    @Test
    fun enablingRemovesOnlyTheTargetEntry() {
        val result = SteamoddedBlacklist.remove("# keep this\nCryptid\nTalisman\n", "cryptid")

        assertFalse(result.lineSequence().any { it.equals("Cryptid", ignoreCase = true) })
        assertTrue(result.contains("# keep this"))
        assertTrue(result.contains("Talisman"))
    }
}
