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
    fun unmanagedFoldersNeedDesktopStyleModEvidence() {
        assertFalse(hasEvidence("assets", setOf("cards.png")))
        assertFalse(hasEvidence("native-library", setOf("nativefs.lua")))
        assertTrue(hasEvidence("PatchMod", setOf("lovely.toml")))
        assertTrue(hasEvidence("smods-1.0.0", setOf("loader.lua")))
        assertTrue(hasEvidence("Anything", emptySet(), managed = true))
        assertTrue(
            LocalModDetectionPolicy.hasModEvidence(
                folderName = "ManualMod",
                topLevelNames = setOf("main.lua"),
                metadata = LocalModMetadata(declaredId = "manual_mod"),
                managed = false,
            ),
        )
    }

    private fun hasEvidence(
        folderName: String,
        files: Set<String>,
        managed: Boolean = false,
    ) = LocalModDetectionPolicy.hasModEvidence(
        folderName = folderName,
        topLevelNames = files,
        metadata = LocalModMetadata(),
        managed = managed,
    )
}
