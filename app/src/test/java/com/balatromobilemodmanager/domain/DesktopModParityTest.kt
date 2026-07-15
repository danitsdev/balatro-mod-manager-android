package com.balatromobilemodmanager.domain

import com.balatromobilemodmanager.catalog.CatalogMod
import com.balatromobilemodmanager.catalog.ManagedInstallManifest
import com.balatromobilemodmanager.installer.LocalModStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopModParityTest {
    private val catalog = listOf(
        CatalogMod(
            id = "author@official-id",
            title = "Official Mod",
            author = "Author",
            folderName = "OfficialFolder",
            version = "2.0.0",
        ),
        CatalogMod(
            id = "other@same-title",
            title = "Official Mod",
            author = "Someone Else",
            folderName = "OtherFolder",
            version = "1.0.0",
        ),
    )

    @Test
    fun declaredIdHasPriorityWhenMatchingLocalMod() {
        val local = LocalModStatus(
            folderName = "renamed-by-user",
            enabled = true,
            title = "Official Mod",
            author = "Unknown",
            declaredId = "official-id",
        )

        assertEquals("author@official-id", catalog.findCatalogForLocal(local)?.id)
    }

    @Test
    fun exactFolderMatchesExternalModToCatalog() {
        val local = LocalModStatus(folderName = "OfficialFolder", enabled = true)

        assertEquals("author@official-id", catalog.findCatalogForLocal(local)?.id)
    }

    @Test
    fun updateRuleMatchesDesktopVersionDifference() {
        val manifest = manifest(version = "1.0.0")

        assertTrue(catalog.first().hasUpdateFor(manifest))
        assertFalse(catalog.first().copy(version = "1.0.0").hasUpdateFor(manifest))
    }

    @Test
    fun unknownVersionDoesNotClaimUpdate() {
        assertFalse(catalog.first().hasUpdateFor(manifest(version = "")))
        assertFalse(catalog.first().copy(version = "").hasUpdateFor(manifest(version = "1.0.0")))
    }

    @Test
    fun primaryActionsMatchDesktopManagedAndLocalStates() {
        assertEquals(ModPrimaryAction.Download, resolveModPrimaryAction(false, false, false))
        assertEquals(ModPrimaryAction.Installed, resolveModPrimaryAction(true, false, false))
        assertEquals(ModPrimaryAction.GetOfficial, resolveModPrimaryAction(true, false, true))
        assertEquals(ModPrimaryAction.Update, resolveModPrimaryAction(true, true, false))
        assertEquals(ModPrimaryAction.Update, resolveModPrimaryAction(true, true, true))
    }

    private fun manifest(version: String) = ManagedInstallManifest(
        modId = "author@official-id",
        title = "Official Mod",
        folderName = "OfficialFolder",
        version = version,
        sourceUrl = "https://example.com/mod.zip",
        installedAtEpochMs = 1L,
        fileCount = 1,
        totalBytes = 1L,
    )
}
