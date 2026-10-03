package com.balatromodmanager.installer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModDetectionPolicyTest {
    @Test
    fun desktopInfrastructureFoldersAreHidden() {
        listOf(
            "lovely",
            "Lovely7",
            ".lovely",
            "BMM-Compat",
            "nativefs",
            "BMMM-STAGING-Talisman",
            "BMMM-BACKUP-Talisman",
            "node_modules",
            "__MACOSX",
        ).forEach { name ->
            assertTrue(name, LocalModDetectionPolicy.shouldSkipFolder(name))
        }

        assertFalse(LocalModDetectionPolicy.shouldSkipFolder("Talisman"))
    }

    @Test
    fun nonInfrastructureFoldersStayVisibleWithoutCatalogMetadata() {
        listOf("assets", "native-library", "PatchMod", "ManualMod", "unknown-package")
            .forEach { name -> assertFalse(name, LocalModDetectionPolicy.shouldSkipFolder(name)) }
    }
}
